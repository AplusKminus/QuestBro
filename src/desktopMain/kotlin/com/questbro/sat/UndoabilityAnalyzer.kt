package com.questbro.sat

import com.questbro.domain.*

/**
 * Analyzes action undoability using SAT solving to determine if actions can be safely undone
 * without breaking existing goals or creating impossible states.
 */
class UndoabilityAnalyzer(private val satAdapter: SATAdapter) {
    
    /**
     * Analyzes whether an action can be undone while preserving all goals
     */
    fun analyzeUndoability(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): UndoabilityAnalysisResult {
        if (!gameRun.completedActions.contains(actionId)) {
            return UndoabilityAnalysisResult(
                actionId = actionId,
                undoable = false,
                reason = UndoabilityReason.NOT_COMPLETED,
                blockedBy = emptyList(),
                cascadeEffects = emptyList(),
                alternativeActions = emptyList()
            )
        }
        
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = SATQuery.UndoabilityQuery(actionId, gameRun.goals)
        
        // Use solveAndDecode which includes dependency analysis
        val currentAdapter = satAdapter
        val domainResult = if (currentAdapter is KoSATAdapter) {
            currentAdapter.solveAndDecode(encoding, query)
        } else {
            val result = currentAdapter.solve(encoding, query)
            currentAdapter.decode(result, encoding)
        }
        
        return when (domainResult) {
            is DomainResult.UndoabilityResult -> {
                if (domainResult.undoable) {
                    UndoabilityAnalysisResult(
                        actionId = actionId,
                        undoable = true,
                        reason = UndoabilityReason.SAFE_TO_UNDO,
                        blockedBy = emptyList(),
                        cascadeEffects = emptyList(),
                        alternativeActions = findAlternativeActions(actionId, gameData, gameRun)
                    )
                } else {
                    val cascadeEffects = analyzeCascadeEffects(actionId, gameData, gameRun)
                    val blockedBy = domainResult.blockedBy ?: findBlockingActions(actionId, gameData, gameRun)
                    
                    UndoabilityAnalysisResult(
                        actionId = actionId,
                        undoable = false,
                        reason = UndoabilityReason.WOULD_BREAK_GOALS,
                        blockedBy = blockedBy,
                        cascadeEffects = cascadeEffects,
                        alternativeActions = findAlternativeActions(actionId, gameData, gameRun)
                    )
                }
            }
            else -> {
                UndoabilityAnalysisResult(
                    actionId = actionId,
                    undoable = false,
                    reason = UndoabilityReason.ANALYSIS_FAILED,
                    blockedBy = emptyList(),
                    cascadeEffects = emptyList(),
                    alternativeActions = emptyList()
                )
            }
        }
    }
    
    /**
     * Analyzes conditional undoability - whether an action can be undone if certain conditions are met
     */
    fun analyzeConditionalUndoability(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): ConditionalUndoabilityResult {
        val baseResult = analyzeUndoability(actionId, gameData, gameRun)
        
        if (baseResult.undoable) {
            return ConditionalUndoabilityResult(
                actionId = actionId,
                unconditionallyUndoable = true,
                conditions = emptyList(),
                worstCaseEffects = emptyList()
            )
        }
        
        // Try to find conditions under which the action could be undone
        val conditions = findUndoabilityConditions(actionId, gameData, gameRun)
        
        return ConditionalUndoabilityResult(
            actionId = actionId,
            unconditionallyUndoable = false,
            conditions = conditions,
            worstCaseEffects = baseResult.cascadeEffects
        )
    }
    
    /**
     * Analyzes the optimal sequence for undoing multiple actions
     */
    fun analyzeOptimalUndoSequence(
        actionIds: List<String>,
        gameData: GameData,
        gameRun: GameRun
    ): UndoSequenceResult {
        val individualResults = actionIds.map { actionId ->
            analyzeUndoability(actionId, gameData, gameRun)
        }
        
        // Check if all actions can be undone independently
        val allUndoable = individualResults.all { it.undoable }
        
        if (allUndoable) {
            val optimalSequence = findOptimalUndoOrder(actionIds, gameData, gameRun)
            return UndoSequenceResult(
                actionIds = actionIds,
                feasible = true,
                optimalSequence = optimalSequence,
                totalCost = calculateUndoCost(optimalSequence, gameData, gameRun),
                warnings = emptyList()
            )
        }
        
        // Find the largest subset that can be undone safely
        val safestSubset = findLargestUndoableSubset(actionIds, gameData, gameRun)
        val warnings = generateUndoWarnings(actionIds, safestSubset, individualResults)
        
        return UndoSequenceResult(
            actionIds = actionIds,
            feasible = false,
            optimalSequence = safestSubset,
            totalCost = if (safestSubset.isNotEmpty()) {
                calculateUndoCost(safestSubset, gameData, gameRun)
            } else {
                Int.MAX_VALUE
            },
            warnings = warnings
        )
    }
    
    /**
     * Performs comprehensive undoability analysis for all completed actions
     */
    fun performComprehensiveUndoabilityAnalysis(
        gameData: GameData,
        gameRun: GameRun
    ): ComprehensiveUndoabilityResult {
        val actionResults = gameRun.completedActions.map { actionId ->
            analyzeUndoability(actionId, gameData, gameRun)
        }
        
        val undoableActions = actionResults.filter { it.undoable }
        val nonUndoableActions = actionResults.filter { !it.undoable }
        
        val criticalActions = findCriticalActions(gameData, gameRun)
        val dependencyChains = buildDependencyChains(gameData, gameRun)
        val undoabilityReport = generateUndoabilityReport(actionResults)
        
        return ComprehensiveUndoabilityResult(
            totalActions = gameRun.completedActions.size,
            undoableActions = undoableActions,
            nonUndoableActions = nonUndoableActions,
            criticalActions = criticalActions,
            dependencyChains = dependencyChains,
            undoabilityReport = undoabilityReport
        )
    }
    
    // Private helper methods
    
    private fun analyzeCascadeEffects(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): List<CascadeEffect> {
        val effects = mutableListOf<CascadeEffect>()
        val action = gameData.actions[actionId] ?: return effects
        
        // Check which goals would be affected by undoing this action
        for (goal in gameRun.goals) {
            val goalAction = gameData.actions[goal.targetId] ?: continue
            
            // Check if this goal depends on the action being undone
            if (actionWouldAffectGoal(action, goalAction, gameData, gameRun)) {
                val severity = calculateEffectSeverity(action, goal, gameData, gameRun)
                effects.add(
                    CascadeEffect(
                        affectedGoal = goal.id,
                        reason = "Goal ${goal.description} depends on action ${action.name}",
                        severity = severity
                    )
                )
            }
        }
        
        return effects
    }
    
    private fun findBlockingActions(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): List<String> {
        val blockingActions = mutableListOf<String>()
        val action = gameData.actions[actionId] ?: return blockingActions
        
        // Find actions that depend on this action
        for (completedActionId in gameRun.completedActions) {
            if (completedActionId == actionId) continue
            
            val completedAction = gameData.actions[completedActionId] ?: continue
            if (actionDependsOn(completedAction, actionId, gameData)) {
                blockingActions.add(completedActionId)
            }
        }
        
        return blockingActions
    }
    
    private fun findAlternativeActions(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): List<String> {
        val action = gameData.actions[actionId] ?: return emptyList()
        val alternatives = mutableListOf<String>()
        
        // Find actions that provide similar rewards
        for (otherAction in gameData.actions.values) {
            if (otherAction.id == actionId) continue
            
            val hasOverlappingRewards = action.rewards.any { reward ->
                otherAction.rewards.any { it.itemId == reward.itemId }
            }
            
            if (hasOverlappingRewards && !gameRun.completedActions.contains(otherAction.id)) {
                alternatives.add(otherAction.id)
            }
        }
        
        return alternatives
    }
    
    private fun findUndoabilityConditions(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): List<UndoabilityCondition> {
        val conditions = mutableListOf<UndoabilityCondition>()
        val blockingActions = findBlockingActions(actionId, gameData, gameRun)
        
        for (blockingActionId in blockingActions) {
            conditions.add(
                UndoabilityCondition(
                    type = UndoabilityConditionType.UNDO_DEPENDENT_ACTION,
                    description = "First undo action $blockingActionId",
                    requiredActions = listOf(blockingActionId),
                    impact = UndoabilityConditionImpact.MEDIUM
                )
            )
        }
        
        return conditions
    }
    
    private fun findOptimalUndoOrder(
        actionIds: List<String>,
        gameData: GameData,
        gameRun: GameRun
    ): List<String> {
        // Use topological sort to find dependency order
        val dependencyGraph = buildUndoDependencyGraph(actionIds, gameData, gameRun)
        return topologicalSort(dependencyGraph).reversed() // Reverse for undo order
    }
    
    private fun findLargestUndoableSubset(
        actionIds: List<String>,
        gameData: GameData,
        gameRun: GameRun
    ): List<String> {
        val undoableActions = mutableListOf<String>()
        
        for (actionId in actionIds) {
            val result = analyzeUndoability(actionId, gameData, gameRun)
            if (result.undoable) {
                undoableActions.add(actionId)
            }
        }
        
        return undoableActions
    }
    
    private fun calculateUndoCost(
        actionIds: List<String>,
        gameData: GameData,
        gameRun: GameRun
    ): Int {
        // Simple cost calculation - in practice, this could be more sophisticated
        return actionIds.size
    }
    
    private fun generateUndoWarnings(
        requestedActions: List<String>,
        safeActions: List<String>,
        results: List<UndoabilityAnalysisResult>
    ): List<UndoWarning> {
        val warnings = mutableListOf<UndoWarning>()
        
        val unsafeActions = requestedActions - safeActions.toSet()
        for (actionId in unsafeActions) {
            val result = results.find { it.actionId == actionId }
            if (result != null) {
                warnings.add(
                    UndoWarning(
                        actionId = actionId,
                        severity = UndoWarningSeverity.HIGH,
                        message = "Cannot undo action $actionId: ${result.reason}",
                        suggestedAction = UndoWarningSuggestedAction.SKIP_ACTION
                    )
                )
            }
        }
        
        return warnings
    }
    
    private fun findCriticalActions(
        gameData: GameData,
        gameRun: GameRun
    ): List<String> {
        val criticalActions = mutableListOf<String>()
        
        for (actionId in gameRun.completedActions) {
            val result = analyzeUndoability(actionId, gameData, gameRun)
            if (!result.undoable && result.cascadeEffects.any { it.severity == EffectSeverity.CRITICAL }) {
                criticalActions.add(actionId)
            }
        }
        
        return criticalActions
    }
    
    private fun buildDependencyChains(
        gameData: GameData,
        gameRun: GameRun
    ): List<DependencyChain> {
        val chains = mutableListOf<DependencyChain>()
        
        // Build dependency chains for each completed action
        for (actionId in gameRun.completedActions) {
            val chain = buildDependencyChain(actionId, gameData, gameRun)
            if (chain.actions.size > 1) {
                chains.add(chain)
            }
        }
        
        return chains
    }
    
    private fun buildDependencyChain(
        actionId: String,
        gameData: GameData,
        gameRun: GameRun
    ): DependencyChain {
        val visited = mutableSetOf<String>()
        val chain = mutableListOf<String>()
        
        fun buildChainRecursive(currentActionId: String) {
            if (visited.contains(currentActionId)) return
            visited.add(currentActionId)
            chain.add(currentActionId)
            
            val dependentActions = findBlockingActions(currentActionId, gameData, gameRun)
            for (dependentId in dependentActions) {
                buildChainRecursive(dependentId)
            }
        }
        
        buildChainRecursive(actionId)
        
        return DependencyChain(
            rootAction = actionId,
            actions = chain,
            depth = chain.size
        )
    }
    
    private fun generateUndoabilityReport(
        results: List<UndoabilityAnalysisResult>
    ): UndoabilityReport {
        val undoableCount = results.count { it.undoable }
        val nonUndoableCount = results.size - undoableCount
        
        val reasonBreakdown = results.groupBy { it.reason }
            .mapValues { it.value.size }
        
        val severityBreakdown = results.flatMap { it.cascadeEffects }
            .groupBy { it.severity }
            .mapValues { it.value.size }
        
        return UndoabilityReport(
            totalActions = results.size,
            undoableActions = undoableCount,
            nonUndoableActions = nonUndoableCount,
            reasonBreakdown = reasonBreakdown,
            severityBreakdown = severityBreakdown
        )
    }
    
    // Helper methods for dependency analysis
    
    private fun actionWouldAffectGoal(
        action: GameAction,
        goalAction: GameAction,
        gameData: GameData,
        gameRun: GameRun
    ): Boolean {
        // Check if goal action depends on the action being undone
        return actionDependsOn(goalAction, action.id, gameData)
    }
    
    private fun actionDependsOn(
        action: GameAction,
        dependencyId: String,
        gameData: GameData
    ): Boolean {
        val preconditionEngine = PreconditionEngine()
        val requiredActions = preconditionEngine.extractRequiredActions(action.preconditions)
        
        return requiredActions.contains(dependencyId) ||
               checkIndirectDependency(action, dependencyId, gameData)
    }
    
    private fun checkIndirectDependency(
        action: GameAction,
        dependencyId: String,
        gameData: GameData
    ): Boolean {
        val preconditionEngine = PreconditionEngine()
        val requiredItems = preconditionEngine.extractRequiredItems(action.preconditions)
        
        val dependencyAction = gameData.actions[dependencyId] ?: return false
        val providedItems = dependencyAction.rewards.map { it.itemId }
        
        return requiredItems.any { providedItems.contains(it) }
    }
    
    private fun calculateEffectSeverity(
        action: GameAction,
        goal: Goal,
        gameData: GameData,
        gameRun: GameRun
    ): EffectSeverity {
        val goalAction = gameData.actions[goal.targetId] ?: return EffectSeverity.LOW
        
        // Check if this is a direct dependency
        if (actionDependsOn(goalAction, action.id, gameData)) {
            return EffectSeverity.CRITICAL
        }
        
        // Check if this is an indirect dependency through items
        if (checkIndirectDependency(goalAction, action.id, gameData)) {
            return EffectSeverity.HIGH
        }
        
        return EffectSeverity.MEDIUM
    }
    
    private fun buildUndoDependencyGraph(
        actionIds: List<String>,
        gameData: GameData,
        gameRun: GameRun
    ): Map<String, List<String>> {
        val graph = mutableMapOf<String, MutableList<String>>()
        
        for (actionId in actionIds) {
            graph[actionId] = mutableListOf()
        }
        
        for (actionId in actionIds) {
            val dependencies = findBlockingActions(actionId, gameData, gameRun)
            for (depId in dependencies) {
                if (actionIds.contains(depId)) {
                    graph[depId]?.add(actionId)
                }
            }
        }
        
        return graph
    }
    
    private fun topologicalSort(graph: Map<String, List<String>>): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        val result = mutableListOf<String>()
        val queue = mutableListOf<String>()
        
        // Initialize in-degree
        for (node in graph.keys) {
            inDegree[node] = 0
        }
        
        for (neighbors in graph.values) {
            for (neighbor in neighbors) {
                inDegree[neighbor] = inDegree[neighbor]!! + 1
            }
        }
        
        // Find nodes with no incoming edges
        for ((node, degree) in inDegree) {
            if (degree == 0) {
                queue.add(node)
            }
        }
        
        // Process nodes
        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)
            result.add(current)
            
            for (neighbor in graph[current] ?: emptyList()) {
                inDegree[neighbor] = inDegree[neighbor]!! - 1
                if (inDegree[neighbor] == 0) {
                    queue.add(neighbor)
                }
            }
        }
        
        return result
    }
    
    private fun PreconditionEngine.extractRequiredActions(expression: PreconditionExpression): Set<String> {
        return when (expression) {
            is PreconditionExpression.ActionRequired -> setOf(expression.actionId)
            is PreconditionExpression.And -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
            is PreconditionExpression.Or -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
            else -> emptySet()
        }
    }
    
    private fun PreconditionEngine.extractRequiredItems(expression: PreconditionExpression): Set<String> {
        return when (expression) {
            is PreconditionExpression.ItemRequired -> setOf(expression.itemId)
            is PreconditionExpression.And -> expression.expressions.flatMap { extractRequiredItems(it) }.toSet()
            is PreconditionExpression.Or -> expression.expressions.flatMap { extractRequiredItems(it) }.toSet()
            else -> emptySet()
        }
    }
}

// Data classes for undoability analysis results

data class UndoabilityAnalysisResult(
    val actionId: String,
    val undoable: Boolean,
    val reason: UndoabilityReason,
    val blockedBy: List<String>,
    val cascadeEffects: List<CascadeEffect>,
    val alternativeActions: List<String>
)

data class ConditionalUndoabilityResult(
    val actionId: String,
    val unconditionallyUndoable: Boolean,
    val conditions: List<UndoabilityCondition>,
    val worstCaseEffects: List<CascadeEffect>
)

data class UndoSequenceResult(
    val actionIds: List<String>,
    val feasible: Boolean,
    val optimalSequence: List<String>,
    val totalCost: Int,
    val warnings: List<UndoWarning>
)

data class ComprehensiveUndoabilityResult(
    val totalActions: Int,
    val undoableActions: List<UndoabilityAnalysisResult>,
    val nonUndoableActions: List<UndoabilityAnalysisResult>,
    val criticalActions: List<String>,
    val dependencyChains: List<DependencyChain>,
    val undoabilityReport: UndoabilityReport
)

data class UndoabilityCondition(
    val type: UndoabilityConditionType,
    val description: String,
    val requiredActions: List<String>,
    val impact: UndoabilityConditionImpact
)

data class UndoWarning(
    val actionId: String,
    val severity: UndoWarningSeverity,
    val message: String,
    val suggestedAction: UndoWarningSuggestedAction
)

data class DependencyChain(
    val rootAction: String,
    val actions: List<String>,
    val depth: Int
)

data class UndoabilityReport(
    val totalActions: Int,
    val undoableActions: Int,
    val nonUndoableActions: Int,
    val reasonBreakdown: Map<UndoabilityReason, Int>,
    val severityBreakdown: Map<EffectSeverity, Int>
)

enum class UndoabilityReason {
    SAFE_TO_UNDO,
    NOT_COMPLETED,
    WOULD_BREAK_GOALS,
    HAS_DEPENDENCIES,
    ANALYSIS_FAILED
}

enum class UndoabilityConditionType {
    UNDO_DEPENDENT_ACTION,
    COMPLETE_ALTERNATIVE_ACTION,
    REMOVE_GOAL,
    MODIFY_CONSTRAINTS
}

enum class UndoabilityConditionImpact {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class UndoWarningSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class UndoWarningSuggestedAction {
    SKIP_ACTION,
    UNDO_DEPENDENCIES_FIRST,
    FIND_ALTERNATIVE,
    MODIFY_GOALS
}