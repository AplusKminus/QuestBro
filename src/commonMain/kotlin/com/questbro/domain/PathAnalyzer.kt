package com.questbro.domain

data class ActionAnalysis(
    val action: GameAction,
    val isAvailable: Boolean,
    val wouldBreakGoals: List<Goal>,
    val requiredForGoals: List<Goal>,
    val directlyFulfillsGoals: List<Goal>, // Goals that are completed by this action
    val enablesNewActions: List<GameAction> // Actions that become available after this action
)

class PathAnalyzer(private val preconditionEngine: PreconditionEngine) {
    
    fun analyzeActions(
        gameData: GameData,
        gameRun: GameRun
    ): List<ActionAnalysis> {
        val inventory = preconditionEngine.getInventory(gameData, gameRun.completedActions)
        
        return gameData.actions.values.map { action ->
            val isCompleted = gameRun.completedActions.contains(action.id)
            val isAvailable = !isCompleted && preconditionEngine.evaluate(
                action.preconditions, 
                gameRun.completedActions, 
                inventory
            )
            
            val wouldBreakGoals = if (isAvailable) {
                findGoalConflicts(gameData, gameRun, action)
            } else emptyList()
            
            val requiredForGoals = findAllGoalsContributedTo(gameData, gameRun, action)
            val directlyFulfillsGoals = findDirectlyFulfilledGoals(gameData, gameRun, action)
            val enablesNewActions = if (isAvailable) {
                findEnabledActions(gameData, gameRun, action)
            } else emptyList()
            
            ActionAnalysis(
                action = action,
                isAvailable = isAvailable,
                wouldBreakGoals = wouldBreakGoals,
                requiredForGoals = requiredForGoals,
                directlyFulfillsGoals = directlyFulfillsGoals,
                enablesNewActions = enablesNewActions
            )
        }.sortedWith(createActionComparator())
    }
    
    private fun findGoalConflicts(
        gameData: GameData,
        gameRun: GameRun,
        candidateAction: GameAction
    ): List<Goal> {
        val currentInventory = preconditionEngine.getInventory(gameData, gameRun.completedActions)
        val simulatedCompleted = gameRun.completedActions + candidateAction.id
        val simulatedInventory = preconditionEngine.getInventory(gameData, simulatedCompleted)
        
        return gameRun.goals.filter { goal ->
            // Only consider goals that are currently achievable but would become unachievable after the action
            val currentlyAchievable = isGoalAchievable(gameData, goal, gameRun.completedActions, currentInventory)
            val wouldBeAchievable = isGoalAchievable(gameData, goal, simulatedCompleted, simulatedInventory)
            
            // Goal is conflicting only if it goes from achievable to unachievable
            currentlyAchievable && !wouldBeAchievable
        }
    }
    
    private fun findRequiredForGoals(
        gameData: GameData,
        gameRun: GameRun,
        action: GameAction
    ): List<Goal> {
        return gameRun.goals.filter { goal ->
            isActionRequiredForGoal(gameData, goal, action, gameRun.completedActions)
        }
    }
    
    /**
     * Find ALL goals that an action contributes progress toward, including indirect contributions
     * through multi-step dependency chains
     */
    private fun findAllGoalsContributedTo(
        gameData: GameData,
        gameRun: GameRun,
        action: GameAction
    ): List<Goal> {
        val currentInventory = preconditionEngine.getInventory(gameData, gameRun.completedActions)
        val simulatedCompleted = gameRun.completedActions + action.id
        val simulatedInventory = preconditionEngine.getInventory(gameData, simulatedCompleted)
        
        return gameRun.goals.filter { goal ->
            // Check if this action contributes to the goal by making it more achievable
            val currentlyRequiredActions = getRequiredActionsForGoal(gameData, goal, gameRun.completedActions, currentInventory)
            val afterActionRequiredActions = getRequiredActionsForGoal(gameData, goal, simulatedCompleted, simulatedInventory)
            
            // If the required actions count decreases, this action contributes to the goal
            currentlyRequiredActions.size > afterActionRequiredActions.size ||
            // Or if the action is directly in the requirement chain
            isActionRequiredForGoal(gameData, goal, action, gameRun.completedActions)
        }
    }
    
    /**
     * Get the list of actions still required to achieve a goal
     */
    private fun getRequiredActionsForGoal(
        gameData: GameData,
        goal: Goal,
        completedActions: Set<String>,
        inventory: Set<String>
    ): List<String> {
        val targetAction = gameData.actions[goal.targetId] ?: return emptyList()
        
        // Already completed
        if (completedActions.contains(goal.targetId)) {
            return emptyList()
        }
        
        // If directly achievable, no other actions required
        if (preconditionEngine.evaluate(targetAction.preconditions, completedActions, inventory)) {
            return emptyList()
        }
        
        // Find all actions required to make this goal achievable
        return findRequiredActionsRecursive(targetAction.preconditions, gameData, completedActions, inventory, emptySet())
    }
    
    /**
     * Recursively find all actions required to satisfy preconditions
     */
    private fun findRequiredActionsRecursive(
        expression: PreconditionExpression,
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>,
        visited: Set<String>
    ): List<String> {
        return when (expression) {
            is PreconditionExpression.Always -> emptyList()
            
            is PreconditionExpression.ActionRequired -> {
                val actionId = expression.actionId
                when {
                    completedActions.contains(actionId) -> emptyList()
                    visited.contains(actionId) -> emptyList() // Prevent cycles
                    else -> {
                        val action = gameData.actions[actionId] ?: return emptyList()
                        val subRequirements = findRequiredActionsRecursive(
                            action.preconditions, 
                            gameData, 
                            completedActions, 
                            inventory, 
                            visited + actionId
                        )
                        subRequirements + actionId
                    }
                }
            }
            
            is PreconditionExpression.ActionForbidden -> {
                // If forbidden action is already completed, this path is blocked
                if (completedActions.contains(expression.actionId)) emptyList() else emptyList()
            }
            
            is PreconditionExpression.ItemRequired -> {
                if (inventory.contains(expression.itemId)) {
                    emptyList()
                } else {
                    // Find actions that provide this item
                    val providingActions = gameData.actions.values.filter { action ->
                        action.rewards.any { it.itemId == expression.itemId }
                    }
                    
                    // Return the shortest path to get the item
                    providingActions.filter { !completedActions.contains(it.id) }
                        .map { action ->
                            val subRequirements = findRequiredActionsRecursive(
                                action.preconditions, 
                                gameData, 
                                completedActions, 
                                inventory, 
                                visited + action.id
                            )
                            subRequirements + action.id
                        }
                        .minByOrNull { it.size } ?: emptyList()
                }
            }
            
            is PreconditionExpression.And -> {
                expression.expressions.flatMap { subExpression ->
                    findRequiredActionsRecursive(subExpression, gameData, completedActions, inventory, visited)
                }.distinct()
            }
            
            is PreconditionExpression.Or -> {
                // Find the shortest path among OR options
                expression.expressions.map { subExpression ->
                    findRequiredActionsRecursive(subExpression, gameData, completedActions, inventory, visited)
                }.minByOrNull { it.size } ?: emptyList()
            }
        }
    }
    
    private fun isGoalAchievable(
        gameData: GameData,
        goal: Goal,
        completedActions: Set<String>,
        inventory: Set<String>
    ): Boolean {
        val targetAction = gameData.actions[goal.targetId] ?: return false
        
        // Already completed - goal is achieved
        if (completedActions.contains(goal.targetId)) {
            return true
        }
        
        // Use deep achievability analysis to check if target action can be reached
        return isActionAchievable(gameData, targetAction, completedActions, inventory)
    }
    
    /**
     * Deep achievability analysis using DAG traversal with memoization
     * Checks if an action can be completed given current state and constraints
     */
    private fun isActionAchievable(
        gameData: GameData,
        targetAction: GameAction,
        completedActions: Set<String>,
        inventory: Set<String>,
        visited: Set<String> = emptySet()
    ): Boolean {
        // Prevent infinite recursion (shouldn't happen in DAG, but safety check)
        if (visited.contains(targetAction.id)) {
            return false
        }
        
        // Already completed
        if (completedActions.contains(targetAction.id)) {
            return true
        }
        
        // Evaluate preconditions recursively
        return evaluatePreconditionAchievability(
            targetAction.preconditions,
            gameData,
            completedActions,
            inventory,
            visited + targetAction.id
        )
    }
    
    private fun evaluatePreconditionAchievability(
        expression: PreconditionExpression,
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>,
        visited: Set<String>
    ): Boolean {
        return when (expression) {
            is PreconditionExpression.Always -> true
            
            is PreconditionExpression.ActionRequired -> {
                val requiredAction = gameData.actions[expression.actionId] ?: return false
                isActionAchievable(gameData, requiredAction, completedActions, inventory, visited)
            }
            
            is PreconditionExpression.ActionForbidden -> {
                // Cannot be achieved if the forbidden action is already completed
                !completedActions.contains(expression.actionId)
            }
            
            is PreconditionExpression.ItemRequired -> {
                // Check if item is in inventory or can be obtained
                inventory.contains(expression.itemId) || 
                canObtainItem(gameData, expression.itemId, completedActions, inventory, visited)
            }
            
            is PreconditionExpression.And -> {
                // All conditions must be achievable
                expression.expressions.all { subExpression ->
                    evaluatePreconditionAchievability(subExpression, gameData, completedActions, inventory, visited)
                }
            }
            
            is PreconditionExpression.Or -> {
                // At least one condition must be achievable
                expression.expressions.any { subExpression ->
                    evaluatePreconditionAchievability(subExpression, gameData, completedActions, inventory, visited)
                }
            }
        }
    }
    
    private fun canObtainItem(
        gameData: GameData,
        itemId: String,
        completedActions: Set<String>,
        inventory: Set<String>,
        visited: Set<String>
    ): Boolean {
        // Find all actions that provide this item
        val providingActions = gameData.actions.values.filter { action ->
            action.rewards.any { it.itemId == itemId }
        }
        
        // Check if any providing action is achievable
        return providingActions.any { action ->
            !completedActions.contains(action.id) && 
            isActionAchievable(gameData, action, completedActions, inventory, visited)
        }
    }
    
    private fun isActionRequiredForGoal(
        gameData: GameData,
        goal: Goal,
        action: GameAction,
        @Suppress("UNUSED_PARAMETER") completedActions: Set<String>
    ): Boolean {
        return goal.targetId == action.id || 
               isActionInPreconditionChain(gameData, goal.targetId, action.id)
    }
    
    private fun isActionInPreconditionChain(
        gameData: GameData,
        targetActionId: String,
        candidateActionId: String
    ): Boolean {
        val targetAction = gameData.actions[targetActionId] ?: return false
        return containsActionRequirement(targetAction.preconditions, candidateActionId)
    }
    
    private fun containsActionRequirement(
        expression: PreconditionExpression,
        actionId: String
    ): Boolean {
        return when (expression) {
            is PreconditionExpression.ActionRequired -> expression.actionId == actionId
            is PreconditionExpression.And -> expression.expressions.any { 
                containsActionRequirement(it, actionId) 
            }
            is PreconditionExpression.Or -> expression.expressions.any { 
                containsActionRequirement(it, actionId) 
            }
            else -> false
        }
    }
    
    private fun findDirectlyFulfilledGoals(
        @Suppress("UNUSED_PARAMETER") gameData: GameData,
        gameRun: GameRun,
        action: GameAction
    ): List<Goal> {
        return gameRun.goals.filter { goal ->
            goal.targetId == action.id
        }
    }
    
    private fun findEnabledActions(
        gameData: GameData,
        gameRun: GameRun,
        action: GameAction
    ): List<GameAction> {
        val simulatedCompleted = gameRun.completedActions + action.id
        val simulatedInventory = preconditionEngine.getInventory(gameData, simulatedCompleted)
        val currentInventory = preconditionEngine.getInventory(gameData, gameRun.completedActions)
        
        return gameData.actions.values.filter { candidateAction ->
            // Action becomes available after completing this action
            !gameRun.completedActions.contains(candidateAction.id) &&
            !preconditionEngine.evaluate(candidateAction.preconditions, gameRun.completedActions, currentInventory) &&
            preconditionEngine.evaluate(candidateAction.preconditions, simulatedCompleted, simulatedInventory)
        }
    }
    
    private fun createActionComparator(): Comparator<ActionAnalysis> {
        return compareBy<ActionAnalysis> { analysis ->
            // First, categorize actions by their goal impact
            when {
                analysis.directlyFulfillsGoals.isNotEmpty() -> 0 // Category 1: Directly fulfills goals
                analysis.wouldBreakGoals.isNotEmpty() -> 3       // Category 4: Would break goals (prioritize showing conflicts)
                analysis.requiredForGoals.isNotEmpty() -> 1      // Category 2: Makes progress on goals
                else -> 2                                        // Category 3: Neutral actions
            }
        }.thenBy { analysis ->
            // Within each category, sort by the appropriate metric
            when {
                analysis.directlyFulfillsGoals.isNotEmpty() -> -analysis.directlyFulfillsGoals.size // Category 1: More goals fulfilled first (desc)
                analysis.wouldBreakGoals.isNotEmpty() -> analysis.wouldBreakGoals.size             // Category 4: Fewer goals broken first (asc)
                analysis.requiredForGoals.isNotEmpty() -> -analysis.requiredForGoals.size           // Category 2: More progress first (desc)
                else -> -analysis.enablesNewActions.size                                           // Category 3: More actions enabled first (desc)
            }
        }.thenBy { analysis ->
            analysis.action.name // Finally, alphabetically
        }
    }
}