package com.questbro.data

import com.questbro.domain.*
import kotlin.test.*

/**
 * Tests for validating game data integrity and structure
 */
class GameDataValidationTest {
    
    @Test
    fun testValidGameDataStructure() {
        // Test that well-formed game data is accepted
        val validGameData = GameData(
            gameId = "valid-game",
            name = "Valid Game",
            version = "1.0.0",
            actions = mapOf(
                "start" to GameAction(
                    id = "start",
                    name = "Start Game",
                    description = "Begin the adventure",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("sword", "Basic Sword")),
                    category = ActionCategory.EXPLORATION
                ),
                "boss_fight" to GameAction(
                    id = "boss_fight",
                    name = "Fight Boss",
                    description = "Battle the final boss",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.ItemRequired("sword")
                    )),
                    rewards = listOf(Reward("victory", "Victory Token")),
                    category = ActionCategory.BOSS
                )
            ),
            items = mapOf(
                "sword" to Item("sword", "Basic Sword", "A simple sword"),
                "victory" to Item("victory", "Victory Token", "Proof of victory")
            )
        )
        
        // Basic validation - should not throw exceptions
        assertNotNull(validGameData.gameId, "Game ID should not be null")
        assertNotNull(validGameData.name, "Game name should not be null")
        assertNotNull(validGameData.version, "Game version should not be null")
        assertTrue(validGameData.actions.isNotEmpty(), "Should have actions")
        assertTrue(validGameData.items.isNotEmpty(), "Should have items")
    }
    
    @Test
    fun testActionIdConsistency() {
        // Test that action IDs are consistent between map keys and action.id
        val gameData = GameData(
            gameId = "consistency-test",
            name = "Consistency Test",
            version = "1.0.0",
            actions = mapOf(
                "action1" to GameAction(
                    id = "action1", // Consistent with key
                    name = "Action 1",
                    description = "First action",
                    preconditions = PreconditionExpression.Always,
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "action2" to GameAction(
                    id = "action2", // Consistent with key
                    name = "Action 2", 
                    description = "Second action",
                    preconditions = PreconditionExpression.ActionRequired("action1"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                )
            ),
            items = emptyMap()
        )
        
        // Verify consistency
        for ((key, action) in gameData.actions) {
            assertEquals(key, action.id, "Action map key should match action.id for $key")
        }
    }
    
    @Test
    fun testItemIdConsistency() {
        // Test that item IDs are consistent between map keys and item.id
        val gameData = GameData(
            gameId = "item-consistency-test",
            name = "Item Consistency Test",
            version = "1.0.0",
            actions = emptyMap(),
            items = mapOf(
                "item1" to Item("item1", "Item 1", "First item"),
                "item2" to Item("item2", "Item 2", "Second item")
            )
        )
        
        // Verify consistency
        for ((key, item) in gameData.items) {
            assertEquals(key, item.id, "Item map key should match item.id for $key")
        }
    }
    
    @Test
    fun testRewardItemReferences() {
        // Test that all reward items reference valid items
        val gameData = GameData(
            gameId = "reward-test",
            name = "Reward Test",
            version = "1.0.0",
            actions = mapOf(
                "action1" to GameAction(
                    id = "action1",
                    name = "Action 1",
                    description = "Gives valid items",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(
                        Reward("valid_item", "Valid Item"),
                        Reward("another_valid_item", "Another Valid Item")
                    ),
                    category = ActionCategory.EXPLORATION
                )
            ),
            items = mapOf(
                "valid_item" to Item("valid_item", "Valid Item", "A valid item"),
                "another_valid_item" to Item("another_valid_item", "Another Valid Item", "Another valid item"),
                "unused_item" to Item("unused_item", "Unused Item", "This item is not rewarded by any action")
            )
        )
        
        // Verify all reward references are valid
        for (action in gameData.actions.values) {
            for (reward in action.rewards) {
                assertTrue(
                    gameData.items.containsKey(reward.itemId),
                    "Reward item ${reward.itemId} in action ${action.id} should exist in items map"
                )
                
                val item = gameData.items[reward.itemId]!!
                assertEquals(
                    reward.itemId,
                    item.id,
                    "Reward item ID should match referenced item ID"
                )
            }
        }
    }
    
    @Test
    fun testPreconditionActionReferences() {
        // Test that all precondition action references are valid
        val gameData = GameData(
            gameId = "precondition-test",
            name = "Precondition Test", 
            version = "1.0.0",
            actions = mapOf(
                "start" to GameAction(
                    id = "start",
                    name = "Start",
                    description = "Starting action",
                    preconditions = PreconditionExpression.Always,
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "dependent" to GameAction(
                    id = "dependent",
                    name = "Dependent Action",
                    description = "Depends on start",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "complex" to GameAction(
                    id = "complex",
                    name = "Complex Action",
                    description = "Complex dependencies",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.ActionForbidden("dependent"),
                        PreconditionExpression.Or(listOf(
                            PreconditionExpression.ActionRequired("start"), // Redundant but valid
                            PreconditionExpression.ActionRequired("dependent")
                        ))
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.BOSS
                )
            ),
            items = emptyMap()
        )
        
        // Helper function to recursively extract action references
        fun extractActionReferences(expr: PreconditionExpression): Set<String> {
            return when (expr) {
                is PreconditionExpression.ActionRequired -> setOf(expr.actionId)
                is PreconditionExpression.ActionForbidden -> setOf(expr.actionId)
                is PreconditionExpression.And -> expr.expressions.flatMap { extractActionReferences(it) }.toSet()
                is PreconditionExpression.Or -> expr.expressions.flatMap { extractActionReferences(it) }.toSet()
                else -> emptySet()
            }
        }
        
        // Verify all action references are valid
        for (action in gameData.actions.values) {
            val referencedActions = extractActionReferences(action.preconditions)
            for (referencedActionId in referencedActions) {
                assertTrue(
                    gameData.actions.containsKey(referencedActionId),
                    "Referenced action $referencedActionId in ${action.id} should exist in actions map"
                )
            }
        }
    }
    
    @Test
    fun testPreconditionItemReferences() {
        // Test that all precondition item references are valid
        val gameData = GameData(
            gameId = "item-precondition-test",
            name = "Item Precondition Test",
            version = "1.0.0",
            actions = mapOf(
                "gated_action" to GameAction(
                    id = "gated_action",
                    name = "Gated Action",
                    description = "Requires specific items",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("key_item"),
                        PreconditionExpression.ItemRequired("another_key")
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                )
            ),
            items = mapOf(
                "key_item" to Item("key_item", "Key Item", "Important key"),
                "another_key" to Item("another_key", "Another Key", "Another important key"),
                "unrelated_item" to Item("unrelated_item", "Unrelated Item", "Not used in preconditions")
            )
        )
        
        // Helper function to recursively extract item references
        fun extractItemReferences(expr: PreconditionExpression): Set<String> {
            return when (expr) {
                is PreconditionExpression.ItemRequired -> setOf(expr.itemId)
                is PreconditionExpression.And -> expr.expressions.flatMap { extractItemReferences(it) }.toSet()
                is PreconditionExpression.Or -> expr.expressions.flatMap { extractItemReferences(it) }.toSet()
                else -> emptySet()
            }
        }
        
        // Verify all item references are valid
        for (action in gameData.actions.values) {
            val referencedItems = extractItemReferences(action.preconditions)
            for (referencedItemId in referencedItems) {
                assertTrue(
                    gameData.items.containsKey(referencedItemId),
                    "Referenced item $referencedItemId in ${action.id} should exist in items map"
                )
            }
        }
    }
    
    @Test
    fun testCircularDependencyDetection() {
        // Test detection of circular dependencies in action requirements
        val circularGameData = GameData(
            gameId = "circular-test",
            name = "Circular Test",
            version = "1.0.0",
            actions = mapOf(
                "action_a" to GameAction(
                    id = "action_a",
                    name = "Action A",
                    description = "Requires B",
                    preconditions = PreconditionExpression.ActionRequired("action_b"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "action_b" to GameAction(
                    id = "action_b",
                    name = "Action B",
                    description = "Requires C",
                    preconditions = PreconditionExpression.ActionRequired("action_c"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "action_c" to GameAction(
                    id = "action_c",
                    name = "Action C",
                    description = "Requires A - creates cycle",
                    preconditions = PreconditionExpression.ActionRequired("action_a"),
                    rewards = emptyList(),
                    category = ActionCategory.BOSS
                )
            ),
            items = emptyMap()
        )
        
        // Try to use the circular game data with PreconditionEngine
        val engine = PreconditionEngine()
        val availableActions = engine.getAvailableActions(circularGameData, emptySet(), emptySet())
        
        // In a circular dependency, no actions should be available
        assertEquals(0, availableActions.size, "No actions should be available with circular dependencies")
        
        // The system should handle this gracefully without infinite recursion
        // If we get here without timeout or stack overflow, the test passes
    }
    
    @Test
    fun testEmptyGameData() {
        // Test behavior with minimal/empty game data
        val emptyGameData = GameData(
            gameId = "empty-test",
            name = "Empty Test",
            version = "1.0.0",
            actions = emptyMap(),
            items = emptyMap()
        )
        
        // Should handle empty data gracefully
        val engine = PreconditionEngine()
        val availableActions = engine.getAvailableActions(emptyGameData, emptySet(), emptySet())
        assertTrue(availableActions.isEmpty(), "Empty game data should have no available actions")
        
        val inventory = engine.getInventory(emptyGameData, emptySet())
        assertTrue(inventory.isEmpty(), "Empty game data should have empty inventory")
        
        // GameActionGraph should also handle empty data
        val graph = GameActionGraph.create(emptyGameData)
        assertTrue(graph.currentActions.isEmpty(), "Empty game should have no current actions")
        assertTrue(graph.completedActions.isEmpty(), "Empty game should have no completed actions")
    }
    
    @Test
    fun testDuplicateNames() {
        // Test behavior with duplicate action/item names (IDs should still be unique)
        val gameDataWithDuplicateNames = GameData(
            gameId = "duplicate-names-test",
            name = "Duplicate Names Test",
            version = "1.0.0",
            actions = mapOf(
                "action1" to GameAction(
                    id = "action1",
                    name = "Duplicate Name", // Same name as action2
                    description = "First action with duplicate name",
                    preconditions = PreconditionExpression.Always,
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "action2" to GameAction(
                    id = "action2",
                    name = "Duplicate Name", // Same name as action1
                    description = "Second action with duplicate name",
                    preconditions = PreconditionExpression.Always,
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                )
            ),
            items = mapOf(
                "item1" to Item("item1", "Duplicate Item", "First item"),
                "item2" to Item("item2", "Duplicate Item", "Second item")
            )
        )
        
        // System should handle duplicate names gracefully (IDs are what matter)
        val engine = PreconditionEngine()
        val availableActions = engine.getAvailableActions(gameDataWithDuplicateNames, emptySet(), emptySet())
        
        assertEquals(2, availableActions.size, "Should have both actions despite duplicate names")
        
        val actionIds = availableActions.map { it.id }.toSet()
        assertTrue(actionIds.contains("action1"), "Should include action1")
        assertTrue(actionIds.contains("action2"), "Should include action2")
    }
    
    @Test
    fun testLargeGameDataPerformance() {
        // Test performance with larger game data sets
        val largeGameData = GameData(
            gameId = "performance-test",
            name = "Performance Test",
            version = "1.0.0",
            actions = (1..100).associate { i ->
                "action$i" to GameAction(
                    id = "action$i",
                    name = "Action $i",
                    description = "Generated action $i",
                    preconditions = if (i == 1) {
                        PreconditionExpression.Always
                    } else {
                        PreconditionExpression.ActionRequired("action${i-1}")
                    },
                    rewards = listOf(Reward("item$i", "Item $i")),
                    category = ActionCategory.EXPLORATION
                )
            },
            items = (1..100).associate { i ->
                "item$i" to Item("item$i", "Item $i", "Generated item $i")
            }
        )
        
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        // Test various operations
        val engine = PreconditionEngine()
        val availableActions = engine.getAvailableActions(largeGameData, emptySet(), emptySet())
        val inventory = engine.getInventory(largeGameData, setOf("action1", "action2", "action3"))
        val graph = GameActionGraph.create(largeGameData)
        
        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime
        
        // Verify operations completed
        assertEquals(1, availableActions.size, "Should have one available action (action1)")
        assertEquals(3, inventory.size, "Should have 3 items from completed actions")
        assertTrue(graph.currentActions.isNotEmpty(), "Graph should have current actions")
        
        // Performance check
        assertTrue(duration < 1000, "Operations on large game data should complete quickly (took ${duration}ms)")
    }
}