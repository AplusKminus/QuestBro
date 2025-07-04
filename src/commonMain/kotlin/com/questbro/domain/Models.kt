package com.questbro.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

@Serializable
data class GameData(
    val gameId: String,
    val name: String,
    val version: String,
    val actions: Map<String, GameAction>,
    val items: Map<String, Item>
)

@Serializable
data class GameAction(
    val id: String,
    val name: String,
    val description: String,
    val completionCriteria: String? = null,
    val preconditions: PreconditionExpression,
    val rewards: List<Reward>,
    val category: ActionCategory,
    val location: String? = null
)

@Serializable
sealed class PreconditionExpression {
    @Serializable
    @SerialName("ActionRequired")
    data class ActionRequired(val actionId: String) : PreconditionExpression()
    
    @Serializable
    @SerialName("ActionForbidden")
    data class ActionForbidden(val actionId: String) : PreconditionExpression()
    
    @Serializable
    @SerialName("ItemRequired")
    data class ItemRequired(val itemId: String) : PreconditionExpression()
    
    @Serializable
    @SerialName("And")
    data class And(val expressions: List<PreconditionExpression>) : PreconditionExpression()
    
    @Serializable
    @SerialName("Or")
    data class Or(val expressions: List<PreconditionExpression>) : PreconditionExpression()
    
    @Serializable
    @SerialName("Always")
    object Always : PreconditionExpression()
}

@Serializable
data class Reward(
    val itemId: String,
    val description: String
)

@Serializable
data class Item(
    val id: String,
    val name: String,
    val description: String
)

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

@Serializable
data class GameRun(
    val gameId: String,
    val gameVersion: String,
    val runName: String,
    val completedActions: Set<String>,
    val goals: List<Goal>,
    val createdAt: Long,
    val lastModified: Long
)

@Serializable
data class Goal(
    val id: String,
    val targetId: String,
    val description: String,
    val priority: Int = 0
)

@Serializable
enum class ActionCategory { BOSS, NPC_DIALOGUE, EXPLORATION, QUEST, ITEM_PICKUP }
