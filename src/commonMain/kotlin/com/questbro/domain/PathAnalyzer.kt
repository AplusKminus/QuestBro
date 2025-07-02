package com.questbro.domain

data class ActionAnalysis(
    val action: GameAction,
    val isAvailable: Boolean,
    val wouldBreakGoals: List<Goal>,
    val requiredForGoals: List<Goal>
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
            
            val requiredForGoals = findRequiredForGoals(gameData, gameRun, action)
            
            ActionAnalysis(
                action = action,
                isAvailable = isAvailable,
                wouldBreakGoals = wouldBreakGoals,
                requiredForGoals = requiredForGoals
            )
        }.sortedBy { it.action.name }
    }
    
    private fun findGoalConflicts(
        gameData: GameData,
        gameRun: GameRun,
        candidateAction: GameAction
    ): List<Goal> {
        val simulatedCompleted = gameRun.completedActions + candidateAction.id
        val simulatedInventory = preconditionEngine.getInventory(gameData, simulatedCompleted)
        
        return gameRun.goals.filter { goal ->
            !isGoalAchievable(gameData, goal, simulatedCompleted, simulatedInventory)
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
}