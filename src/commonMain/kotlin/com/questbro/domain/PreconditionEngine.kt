package com.questbro.domain

class PreconditionEngine {
    
    fun evaluate(
        expression: PreconditionExpression,
        completedActions: Set<String>,
        inventory: Set<String>
    ): Boolean {
        return when (expression) {
            is PreconditionExpression.Always -> true
            is PreconditionExpression.ActionRequired -> completedActions.contains(expression.actionId)
            is PreconditionExpression.ActionForbidden -> !completedActions.contains(expression.actionId)
            is PreconditionExpression.ItemRequired -> inventory.contains(expression.itemId)
            is PreconditionExpression.And -> expression.expressions.all { 
                evaluate(it, completedActions, inventory) 
            }
            is PreconditionExpression.Or -> expression.expressions.any { 
                evaluate(it, completedActions, inventory) 
            }
        }
    }
    
    fun getAvailableActions(
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>
    ): List<GameAction> {
        return gameData.actions.values.filter { action ->
            !completedActions.contains(action.id) && 
            evaluate(action.preconditions, completedActions, inventory)
        }
    }
    
    fun getInventory(gameData: GameData, completedActions: Set<String>): Set<String> {
        return completedActions.flatMap { actionId ->
            gameData.actions[actionId]?.rewards?.map { it.itemId } ?: emptyList()
        }.toSet()
    }
}