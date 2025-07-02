package com.questbro.domain

enum class GoalAchievability {
    DIRECTLY_ACHIEVABLE,  // Can be completed with currently available actions
    ACHIEVABLE,          // Can still be achieved, but requires completing other actions first
    UNACHIEVABLE,        // Cannot be achieved in this run (preconditions permanently blocked)
    COMPLETED            // Goal has been completed (target action was executed)
}

data class AnalyzedGoal(
    val goal: Goal,
    val achievability: GoalAchievability,
    val requiredActions: List<String> = emptyList(),  // Actions needed to make this goal achievable
    val blockingActions: List<String> = emptyList()   // Actions that would make this goal unachievable
)

class GoalAnalyzer(private val preconditionEngine: PreconditionEngine) {
    
    fun analyzeGoals(
        gameData: GameData,
        gameRun: GameRun
    ): List<AnalyzedGoal> {
        val inventory = preconditionEngine.getInventory(gameData, gameRun.completedActions)
        
        return gameRun.goals.map { goal ->
            analyzeGoal(goal, gameData, gameRun.completedActions, inventory)
        }
    }
    
    fun analyzeGoal(
        goal: Goal,
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>
    ): AnalyzedGoal {
        return analyzeActionGoal(goal, gameData, completedActions, inventory)
    }
    
    private fun analyzeActionGoal(
        goal: Goal,
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>
    ): AnalyzedGoal {
        val targetAction = gameData.actions[goal.targetId]
            ?: return AnalyzedGoal(goal, GoalAchievability.UNACHIEVABLE)
        
        // Already completed
        if (completedActions.contains(goal.targetId)) {
            return AnalyzedGoal(goal, GoalAchievability.COMPLETED)
        }
        
        // Check if directly achievable (preconditions met)
        if (preconditionEngine.evaluate(targetAction.preconditions, completedActions, inventory)) {
            return AnalyzedGoal(goal, GoalAchievability.DIRECTLY_ACHIEVABLE)
        }
        
        // Check if achievable at all (no forbidden actions completed that would block it)
        val achievabilityResult = checkAchievability(targetAction.preconditions, gameData, completedActions, inventory)
        
        return AnalyzedGoal(
            goal = goal,
            achievability = if (achievabilityResult.isAchievable) GoalAchievability.ACHIEVABLE else GoalAchievability.UNACHIEVABLE,
            requiredActions = achievabilityResult.requiredActions,
            blockingActions = achievabilityResult.blockingActions
        )
    }
    
    
    private data class AchievabilityResult(
        val isAchievable: Boolean,
        val requiredActions: List<String> = emptyList(),
        val blockingActions: List<String> = emptyList()
    )
    
    private fun checkAchievability(
        preconditions: PreconditionExpression,
        gameData: GameData,
        completedActions: Set<String>,
        inventory: Set<String>,
        visited: Set<String> = emptySet()
    ): AchievabilityResult {
        return when (preconditions) {
            is PreconditionExpression.Always -> AchievabilityResult(true)
            
            is PreconditionExpression.ActionRequired -> {
                val actionId = preconditions.actionId
                when {
                    completedActions.contains(actionId) -> AchievabilityResult(true)
                    visited.contains(actionId) -> AchievabilityResult(false) // Circular dependency
                    else -> {
                        val action = gameData.actions[actionId] ?: return AchievabilityResult(false)
                        val subResult = checkAchievability(
                            action.preconditions, 
                            gameData, 
                            completedActions, 
                            inventory, 
                            visited + actionId
                        )
                        AchievabilityResult(
                            isAchievable = subResult.isAchievable,
                            requiredActions = subResult.requiredActions + actionId,
                            blockingActions = subResult.blockingActions
                        )
                    }
                }
            }
            
            is PreconditionExpression.ActionForbidden -> {
                if (completedActions.contains(preconditions.actionId)) {
                    AchievabilityResult(false, blockingActions = listOf(preconditions.actionId))
                } else {
                    AchievabilityResult(true)
                }
            }
            
            is PreconditionExpression.ItemRequired -> {
                if (inventory.contains(preconditions.itemId)) {
                    AchievabilityResult(true)
                } else {
                    // Find actions that provide this item
                    val providingActions = gameData.actions.values.filter { action ->
                        action.rewards.any { it.itemId == preconditions.itemId }
                    }
                    
                    for (action in providingActions) {
                        if (!completedActions.contains(action.id)) {
                            val subResult = checkAchievability(
                                action.preconditions,
                                gameData,
                                completedActions,
                                inventory,
                                visited + action.id
                            )
                            if (subResult.isAchievable) {
                                return AchievabilityResult(
                                    isAchievable = true,
                                    requiredActions = subResult.requiredActions + action.id,
                                    blockingActions = subResult.blockingActions
                                )
                            }
                        }
                    }
                    
                    AchievabilityResult(false)
                }
            }
            
            is PreconditionExpression.And -> {
                val results = preconditions.expressions.map { 
                    checkAchievability(it, gameData, completedActions, inventory, visited) 
                }
                
                if (results.all { it.isAchievable }) {
                    AchievabilityResult(
                        isAchievable = true,
                        requiredActions = results.flatMap { it.requiredActions }.distinct(),
                        blockingActions = results.flatMap { it.blockingActions }.distinct()
                    )
                } else {
                    AchievabilityResult(
                        isAchievable = false,
                        blockingActions = results.flatMap { it.blockingActions }.distinct()
                    )
                }
            }
            
            is PreconditionExpression.Or -> {
                val results = preconditions.expressions.map { 
                    checkAchievability(it, gameData, completedActions, inventory, visited) 
                }
                
                // Find the shortest achievable path (fewest required actions)
                val achievableResults = results.filter { it.isAchievable }
                if (achievableResults.isNotEmpty()) {
                    achievableResults.minByOrNull { it.requiredActions.size } ?: achievableResults.first()
                } else {
                    AchievabilityResult(
                        isAchievable = false,
                        blockingActions = results.flatMap { it.blockingActions }.distinct()
                    )
                }
            }
        }
    }
}