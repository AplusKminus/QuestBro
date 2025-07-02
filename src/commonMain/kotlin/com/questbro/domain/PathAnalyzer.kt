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
        return completedActions.contains(goal.targetId) || 
               preconditionEngine.evaluate(targetAction.preconditions, completedActions, inventory)
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