package com.questbro.sat

import com.questbro.domain.*
import org.kosat.Kosat

class KoSATAdapter : SATAdapter {
    
    private lateinit var gameData: GameData
    private lateinit var gameRun: GameRun
    
    override fun encode(gameData: GameData, gameRun: GameRun): SATEncoding {
        this.gameData = gameData
        this.gameRun = gameRun
        
        val startTime = System.currentTimeMillis()
        val solver = Kosat(mutableListOf(), 0)
        
        // Variable allocation
        val actionVariables = mutableMapOf<String, Int>()
        val itemVariables = mutableMapOf<String, Int>()
        val goalVariables = mutableMapOf<String, Int>()
        val timeStepVariables = mutableMapOf<String, Map<Int, Int>>()
        
        // Allocate variables for all actions
        for (actionId in gameData.actions.keys) {
            actionVariables[actionId] = solver.addVariable()
        }
        
        // Allocate variables for all items
        for (itemId in gameData.items.keys) {
            itemVariables[itemId] = solver.addVariable()
        }
        
        // Allocate variables for all goals
        for (goal in gameRun.goals) {
            goalVariables[goal.id] = solver.addVariable()
        }
        
        // Encode preconditions for each action
        for (action in gameData.actions.values) {
            encodePreconditions(solver, action, actionVariables, itemVariables)
        }
        
        // Encode item rewards
        for (action in gameData.actions.values) {
            encodeRewards(solver, action, actionVariables, itemVariables)
        }
        
        // Encode item sources: if an item is available, at least one action that produces it must be completed
        encodeItemSources(solver, gameData, actionVariables, itemVariables)
        
        // Encode completed actions as unit clauses
        for (completedActionId in gameRun.completedActions) {
            actionVariables[completedActionId]?.let { variable ->
                solver.addClause(variable)
            }
        }
        
        // Encode goals
        for (goal in gameRun.goals) {
            encodeGoal(solver, goal, goalVariables, actionVariables)
        }
        
        val endTime = System.currentTimeMillis()
        val metadata = EncodingMetadata(
            variableCount = solver.numberOfVariables,
            clauseCount = solver.numberOfClauses,
            actionCount = gameData.actions.size,
            itemCount = gameData.items.size,
            encodingTime = endTime - startTime
        )
        
        return SATEncoding(
            solver = solver,
            actionVariables = actionVariables,
            itemVariables = itemVariables,
            timeStepVariables = timeStepVariables,
            goalVariables = goalVariables,
            metadata = metadata
        )
    }
    
    override fun solve(encoding: SATEncoding, query: SATQuery): SATResult {
        // Create a copy of the solver to avoid modifying the original
        val solverCopy = Kosat(mutableListOf(), 0)
        
        // Copy all clauses from original solver
        for (variable in 1..encoding.metadata.variableCount) {
            solverCopy.addVariable()
        }
        
        // Re-encode the base constraints
        for (action in gameData.actions.values) {
            encodePreconditions(solverCopy, action, encoding.actionVariables, encoding.itemVariables)
            encodeRewards(solverCopy, action, encoding.actionVariables, encoding.itemVariables)
        }
        
        // Re-encode item sources
        encodeItemSources(solverCopy, gameData, encoding.actionVariables, encoding.itemVariables)
        
        // For undoability queries, we want to check if goals can still be achieved 
        // WITHOUT the action being undone. So we only encode completed actions
        // that are NOT the one being undone.
        val actionsToEncode = when (query) {
            is SATQuery.UndoabilityQuery -> gameRun.completedActions - query.actionId
            else -> gameRun.completedActions
        }
        
        // Encode completed actions
        for (completedActionId in actionsToEncode) {
            encoding.actionVariables[completedActionId]?.let { variable ->
                solverCopy.addClause(variable)
            }
        }
        
        // Encode existing goals
        for (goal in gameRun.goals) {
            encodeGoal(solverCopy, goal, encoding.goalVariables, encoding.actionVariables)
        }
        
        // Add query-specific constraints
        when (query) {
            is SATQuery.GoalCompatibilityQuery -> {
                addGoalCompatibilityConstraints(solverCopy, query, encoding)
            }
            is SATQuery.UndoabilityQuery -> {
                addUndoabilityConstraints(solverCopy, query, encoding)
            }
            is SATQuery.OptimalPathQuery -> {
                addOptimalPathConstraints(solverCopy, query, encoding)
            }
        }
        
        // Solve the SAT problem
        val result = solverCopy.solve()
        
        return when (result) {
            true -> {
                val model = mutableMapOf<Int, Boolean>()
                for (variable in 1..encoding.metadata.variableCount) {
                    model[variable] = solverCopy.getValue(variable)
                }
                SATResult.Satisfiable(model)
            }
            false -> {
                SATResult.Unsatisfiable(emptyList())
            }
        }
    }
    
    override fun decode(result: SATResult, encoding: SATEncoding): DomainResult {
        return when (result) {
            is SATResult.Satisfiable -> {
                decodeModel(result.model, encoding)
            }
            is SATResult.Unsatisfiable -> {
                // Return appropriate failure result based on most recent query type
                // In practice, we'd pass the query type to decode, but for now use a heuristic
                DomainResult.GoalCompatibilityResult(compatible = false)
            }
            is SATResult.Unknown -> {
                DomainResult.GoalCompatibilityResult(compatible = false)
            }
        }
    }
    
    fun solveAndDecode(encoding: SATEncoding, query: SATQuery): DomainResult {
        val result = solve(encoding, query)
        return when (query) {
            is SATQuery.GoalCompatibilityQuery -> {
                when (result) {
                    is SATResult.Satisfiable -> {
                        DomainResult.GoalCompatibilityResult(
                            compatible = true,
                            requiredActions = extractRequiredActions(result.model, encoding)
                        )
                    }
                    is SATResult.Unsatisfiable -> {
                        DomainResult.GoalCompatibilityResult(compatible = false)
                    }
                    is SATResult.Unknown -> {
                        DomainResult.GoalCompatibilityResult(compatible = false)
                    }
                }
            }
            is SATQuery.UndoabilityQuery -> {
                // Special case: if there are no goals to preserve, the action is always undoable
                if (query.preserveGoals.isEmpty()) {
                    DomainResult.UndoabilityResult(undoable = true)
                } else {
                    // Check if the action is actually needed for any of the goals
                    val isNeeded = isActionNeededForGoals(query.actionId, query.preserveGoals, encoding)
                    if (isNeeded) {
                        DomainResult.UndoabilityResult(
                            undoable = false,
                            blockedBy = listOf(query.actionId)
                        )
                    } else {
                        when (result) {
                            is SATResult.Satisfiable -> {
                                DomainResult.UndoabilityResult(undoable = true)
                            }
                            is SATResult.Unsatisfiable -> {
                                DomainResult.UndoabilityResult(
                                    undoable = false,
                                    blockedBy = listOf(query.actionId)
                                )
                            }
                            is SATResult.Unknown -> {
                                DomainResult.UndoabilityResult(undoable = false)
                            }
                        }
                    }
                }
            }
            is SATQuery.OptimalPathQuery -> {
                when (result) {
                    is SATResult.Satisfiable -> {
                        DomainResult.PathPlanResult(
                            feasible = true,
                            actionSequence = extractActionSequence(result.model, encoding)
                        )
                    }
                    is SATResult.Unsatisfiable -> {
                        DomainResult.PathPlanResult(feasible = false)
                    }
                    is SATResult.Unknown -> {
                        DomainResult.PathPlanResult(feasible = false)
                    }
                }
            }
        }
    }
    
    private fun encodePreconditions(
        solver: Kosat,
        action: GameAction,
        actionVariables: Map<String, Int>,
        itemVariables: Map<String, Int>
    ) {
        val actionVar = actionVariables[action.id] ?: return
        val preconditionClauses = translatePreconditionToCNF(
            action.preconditions,
            actionVariables,
            itemVariables
        )
        
        // For each precondition clause, add implication: action → precondition
        for (clause in preconditionClauses) {
            val implicationClause = mutableListOf(-actionVar)
            implicationClause.addAll(clause)
            solver.addClause(implicationClause)
        }
    }
    
    private fun encodeRewards(
        solver: Kosat,
        action: GameAction,
        actionVariables: Map<String, Int>,
        itemVariables: Map<String, Int>
    ) {
        val actionVar = actionVariables[action.id] ?: return
        
        // For each reward, add implication: action → item
        for (reward in action.rewards) {
            val itemVar = itemVariables[reward.itemId] ?: continue
            solver.addClause(-actionVar, itemVar)
        }
    }
    
    private fun encodeGoal(
        solver: Kosat,
        goal: Goal,
        goalVariables: Map<String, Int>,
        actionVariables: Map<String, Int>
    ) {
        val goalVar = goalVariables[goal.id] ?: return
        val targetVar = actionVariables[goal.targetId] ?: return
        
        // Goal is achieved if and only if target action is completed
        solver.addClause(-goalVar, targetVar)  // goal → target
        solver.addClause(goalVar, -targetVar)  // target → goal
    }
    
    private fun addGoalCompatibilityConstraints(
        solver: Kosat,
        query: SATQuery.GoalCompatibilityQuery,
        encoding: SATEncoding
    ) {
        // For goal compatibility, we want to check if adding the new goal
        // makes the formula unsatisfiable (indicating a conflict)
        
        // Add all existing goals as constraints (they must all be achievable)
        for (goal in query.existingGoals) {
            val goalVar = encoding.goalVariables[goal.id]
            if (goalVar != null) {
                solver.addClause(goalVar)
            }
        }
        
        // Add the new goal constraint
        val newGoalTargetVar = encoding.actionVariables[query.newGoal.targetId]
        if (newGoalTargetVar != null) {
            // The new goal requires its target action to be completed
            solver.addClause(newGoalTargetVar)
            
            // Also encode the goal's preconditions
            val targetAction = gameData.actions[query.newGoal.targetId]
            if (targetAction != null) {
                encodePreconditions(solver, targetAction, encoding.actionVariables, encoding.itemVariables)
            }
        } else {
            // If the target action doesn't exist, add an unsatisfiable constraint
            // This should make the SAT problem unsatisfiable, indicating incompatibility
            solver.addClause(1)  // Always true
            solver.addClause(-1) // Always false - creates contradiction
        }
    }
    
    private fun addUndoabilityConstraints(
        solver: Kosat,
        query: SATQuery.UndoabilityQuery,
        encoding: SATEncoding
    ) {
        // Require that all goals can still be achieved after removing the action
        // The action removal is handled in the solve() method by not encoding it as completed
        for (goal in query.preserveGoals) {
            val goalVar = encoding.goalVariables[goal.id]
            if (goalVar != null) {
                solver.addClause(goalVar)
            }
        }
    }
    
    private fun addOptimalPathConstraints(
        solver: Kosat,
        query: SATQuery.OptimalPathQuery,
        encoding: SATEncoding
    ) {
        // Add all goals as constraints
        for (goal in query.goals) {
            val goalVar = encoding.goalVariables[goal.id]
            if (goalVar != null) {
                solver.addClause(goalVar)
            }
        }
        
        // Add path constraints
        for (constraint in query.constraints) {
            val actionVar = encoding.actionVariables[constraint.actionId]
            if (actionVar != null) {
                when (constraint.type) {
                    PathConstraintType.MUST_INCLUDE -> solver.addClause(actionVar)
                    PathConstraintType.MUST_EXCLUDE -> solver.addClause(-actionVar)
                    // TODO: Implement temporal constraints
                    PathConstraintType.MUST_COMPLETE_BEFORE -> { /* TODO */ }
                    PathConstraintType.MUST_COMPLETE_AFTER -> { /* TODO */ }
                }
            }
        }
    }
    
    private fun decodeModel(model: Map<Int, Boolean>, encoding: SATEncoding): DomainResult {
        val completedActions = mutableListOf<String>()
        val achievedGoals = mutableListOf<String>()
        
        // Extract completed actions
        for ((actionId, variable) in encoding.actionVariables) {
            if (model[variable] == true) {
                completedActions.add(actionId)
            }
        }
        
        // Extract achieved goals
        for ((goalId, variable) in encoding.goalVariables) {
            if (model[variable] == true) {
                achievedGoals.add(goalId)
            }
        }
        
        // Return appropriate result based on what we found
        return DomainResult.GoalCompatibilityResult(
            compatible = true,
            requiredActions = completedActions
        )
    }
    
    private fun extractRequiredActions(model: Map<Int, Boolean>, encoding: SATEncoding): List<String> {
        val completedActions = mutableListOf<String>()
        for ((actionId, variable) in encoding.actionVariables) {
            if (model[variable] == true) {
                completedActions.add(actionId)
            }
        }
        return completedActions
    }
    
    private fun extractActionSequence(model: Map<Int, Boolean>, encoding: SATEncoding): List<String> {
        return extractRequiredActions(model, encoding)
    }
    
    private fun isActionNeededForGoals(
        actionId: String, 
        goals: List<Goal>, 
        encoding: SATEncoding
    ): Boolean {
        // Check if the action is needed for any goal using a simple dependency analysis
        return goals.any { goal ->
            isActionNeededForGoal(actionId, goal.targetId, mutableSetOf())
        }
    }
    
    private fun isActionNeededForGoal(
        actionId: String, 
        targetActionId: String, 
        visited: MutableSet<String>
    ): Boolean {
        if (targetActionId == actionId) return true
        if (visited.contains(targetActionId)) return false
        visited.add(targetActionId)
        
        val targetAction = gameData.actions[targetActionId] ?: return false
        
        return when (val precondition = targetAction.preconditions) {
            is PreconditionExpression.ActionRequired -> {
                isActionNeededForGoal(actionId, precondition.actionId, visited)
            }
            is PreconditionExpression.ItemRequired -> {
                // Check if actionId produces this item
                val action = gameData.actions[actionId]
                action?.rewards?.any { it.itemId == precondition.itemId } == true
            }
            is PreconditionExpression.And -> {
                precondition.expressions.any { 
                    isActionNeededForPrecondition(actionId, it, visited)
                }
            }
            is PreconditionExpression.Or -> {
                // For OR, the action is only needed if it's needed for ALL branches
                // (because if any branch doesn't need it, we can use that branch)
                precondition.expressions.all { 
                    isActionNeededForPrecondition(actionId, it, visited)
                }
            }
            else -> false
        }
    }
    
    private fun isActionNeededForPrecondition(
        actionId: String,
        precondition: PreconditionExpression,
        visited: MutableSet<String>
    ): Boolean {
        return when (precondition) {
            is PreconditionExpression.ActionRequired -> {
                isActionNeededForGoal(actionId, precondition.actionId, visited)
            }
            is PreconditionExpression.ItemRequired -> {
                val action = gameData.actions[actionId]
                action?.rewards?.any { it.itemId == precondition.itemId } == true
            }
            is PreconditionExpression.And -> {
                precondition.expressions.any { 
                    isActionNeededForPrecondition(actionId, it, visited)
                }
            }
            is PreconditionExpression.Or -> {
                precondition.expressions.all { 
                    isActionNeededForPrecondition(actionId, it, visited)
                }
            }
            else -> false
        }
    }

    private fun encodeItemSources(
        solver: Kosat,
        gameData: GameData,
        actionVariables: Map<String, Int>,
        itemVariables: Map<String, Int>
    ) {
        // For each item, find all actions that produce it
        val itemSources = mutableMapOf<String, MutableList<String>>()
        for (action in gameData.actions.values) {
            for (reward in action.rewards) {
                itemSources.getOrPut(reward.itemId) { mutableListOf() }.add(action.id)
            }
        }
        
        // For each item, add constraint: item → (producer1 ∨ producer2 ∨ ... producerN)
        for ((itemId, producers) in itemSources) {
            val itemVar = itemVariables[itemId] ?: continue
            if (producers.isNotEmpty()) {
                val clause = mutableListOf(-itemVar) // ¬item ∨ (producer1 ∨ producer2 ∨ ...)
                for (producerId in producers) {
                    val producerVar = actionVariables[producerId]
                    if (producerVar != null) {
                        clause.add(producerVar)
                    }
                }
                // Only add the clause if we have at least one producer variable
                if (clause.size > 1) {
                    solver.addClause(clause)
                }
            }
        }
    }

    private fun translatePreconditionToCNF(
        expression: PreconditionExpression,
        actionVariables: Map<String, Int>,
        itemVariables: Map<String, Int>
    ): List<List<Int>> {
        return when (expression) {
            is PreconditionExpression.ActionRequired -> {
                val variable = actionVariables[expression.actionId]
                if (variable != null) {
                    listOf(listOf(variable))
                } else {
                    emptyList()
                }
            }
            is PreconditionExpression.ActionForbidden -> {
                val variable = actionVariables[expression.actionId]
                if (variable != null) {
                    listOf(listOf(-variable))
                } else {
                    emptyList()
                }
            }
            is PreconditionExpression.ItemRequired -> {
                val variable = itemVariables[expression.itemId]
                if (variable != null) {
                    listOf(listOf(variable))
                } else {
                    emptyList()
                }
            }
            is PreconditionExpression.And -> {
                val allClauses = mutableListOf<List<Int>>()
                for (subExpression in expression.expressions) {
                    allClauses.addAll(translatePreconditionToCNF(subExpression, actionVariables, itemVariables))
                }
                allClauses
            }
            is PreconditionExpression.Or -> {
                val subClauses = expression.expressions.map { subExpression ->
                    translatePreconditionToCNF(subExpression, actionVariables, itemVariables)
                }
                
                // Convert OR to CNF using distribution
                if (subClauses.isEmpty()) {
                    emptyList()
                } else {
                    // Simple implementation: create disjunction of all literals
                    val disjunction = mutableListOf<Int>()
                    for (clauseSet in subClauses) {
                        for (clause in clauseSet) {
                            disjunction.addAll(clause)
                        }
                    }
                    listOf(disjunction)
                }
            }
            is PreconditionExpression.Always -> {
                // Always true - no constraints needed
                emptyList()
            }
        }
    }
}