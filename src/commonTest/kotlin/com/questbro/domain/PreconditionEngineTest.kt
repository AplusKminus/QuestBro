package com.questbro.domain

import kotlin.test.*

class PreconditionEngineTest {
    
    private lateinit var preconditionEngine: PreconditionEngine
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        preconditionEngine = PreconditionEngine()
        
        testGameData = GameData(
            gameId = "test-game",
            name = "Test Game",
            version = "1.0.0",
            actions = mapOf(
                "action_a" to GameAction(
                    id = "action_a",
                    name = "Action A",
                    description = "First action",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("item_1", "Item 1")),
                    category = ActionCategory.EXPLORATION
                ),
                "action_b" to GameAction(
                    id = "action_b", 
                    name = "Action B",
                    description = "Requires Action A",
                    preconditions = PreconditionExpression.ActionRequired("action_a"),
                    rewards = listOf(Reward("item_2", "Item 2")),
                    category = ActionCategory.QUEST
                ),
                "action_c" to GameAction(
                    id = "action_c",
                    name = "Action C", 
                    description = "Forbidden if Action B completed",
                    preconditions = PreconditionExpression.ActionForbidden("action_b"),
                    rewards = listOf(Reward("item_3", "Item 3")),
                    category = ActionCategory.BOSS
                ),
                "action_d" to GameAction(
                    id = "action_d",
                    name = "Action D",
                    description = "Requires Item 1",
                    preconditions = PreconditionExpression.ItemRequired("item_1"),
                    rewards = emptyList(),
                    category = ActionCategory.NPC_DIALOGUE
                ),
                "action_e" to GameAction(
                    id = "action_e",
                    name = "Action E", 
                    description = "Requires both Item 1 AND Item 2",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("item_1"),
                        PreconditionExpression.ItemRequired("item_2")
                    )),
                    rewards = listOf(Reward("item_4", "Item 4")),
                    category = ActionCategory.ITEM_PICKUP
                ),
                "action_f" to GameAction(
                    id = "action_f",
                    name = "Action F",
                    description = "Requires Item 1 OR Item 3",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ItemRequired("item_1"),
                        PreconditionExpression.ItemRequired("item_3")
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "complex_action" to GameAction(
                    id = "complex_action",
                    name = "Complex Action",
                    description = "Complex nested preconditions",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.Or(listOf(
                            PreconditionExpression.ActionRequired("action_a"),
                            PreconditionExpression.ActionRequired("action_b")
                        )),
                        PreconditionExpression.ActionForbidden("action_c"),
                        PreconditionExpression.ItemRequired("item_1")
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.BOSS
                )
            ),
            items = mapOf(
                "item_1" to Item("item_1", "Item 1", "First item"),
                "item_2" to Item("item_2", "Item 2", "Second item"),
                "item_3" to Item("item_3", "Item 3", "Third item"),
                "item_4" to Item("item_4", "Item 4", "Fourth item")
            )
        )
    }
    
    @Test
    fun testAlwaysExpression() {
        val result = preconditionEngine.evaluate(
            PreconditionExpression.Always,
            emptySet(),
            emptySet()
        )
        assertTrue(result, "Always expression should always evaluate to true")
    }
    
    @Test
    fun testActionRequiredEvaluation() {
        // Action not completed - should be false
        assertFalse(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionRequired("action_a"),
                emptySet(),
                emptySet()
            ),
            "ActionRequired should be false when action not completed"
        )
        
        // Action completed - should be true
        assertTrue(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionRequired("action_a"),
                setOf("action_a"),
                emptySet()
            ),
            "ActionRequired should be true when action is completed"
        )
        
        // Different action completed - should be false
        assertFalse(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionRequired("action_a"),
                setOf("action_b"),
                emptySet()
            ),
            "ActionRequired should be false when different action is completed"
        )
    }
    
    @Test
    fun testActionForbiddenEvaluation() {
        // Action not completed - should be true
        assertTrue(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionForbidden("action_b"),
                emptySet(),
                emptySet()
            ),
            "ActionForbidden should be true when forbidden action not completed"
        )
        
        // Action completed - should be false
        assertFalse(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionForbidden("action_b"),
                setOf("action_b"),
                emptySet()
            ),
            "ActionForbidden should be false when forbidden action is completed"
        )
        
        // Different action completed - should be true
        assertTrue(
            preconditionEngine.evaluate(
                PreconditionExpression.ActionForbidden("action_b"),
                setOf("action_a"),
                emptySet()
            ),
            "ActionForbidden should be true when different action is completed"
        )
    }
    
    @Test
    fun testItemRequiredEvaluation() {
        // Item not in inventory - should be false
        assertFalse(
            preconditionEngine.evaluate(
                PreconditionExpression.ItemRequired("item_1"),
                emptySet(),
                emptySet()
            ),
            "ItemRequired should be false when item not in inventory"
        )
        
        // Item in inventory - should be true
        assertTrue(
            preconditionEngine.evaluate(
                PreconditionExpression.ItemRequired("item_1"),
                emptySet(),
                setOf("item_1")
            ),
            "ItemRequired should be true when item is in inventory"
        )
        
        // Different item in inventory - should be false
        assertFalse(
            preconditionEngine.evaluate(
                PreconditionExpression.ItemRequired("item_1"),
                emptySet(),
                setOf("item_2")
            ),
            "ItemRequired should be false when different item is in inventory"
        )
    }
    
    @Test
    fun testAndExpressionAllTrue() {
        val andExpression = PreconditionExpression.And(listOf(
            PreconditionExpression.ActionRequired("action_a"),
            PreconditionExpression.ItemRequired("item_1"),
            PreconditionExpression.ActionForbidden("action_c")
        ))
        
        assertTrue(
            preconditionEngine.evaluate(
                andExpression,
                setOf("action_a"), // action_a completed, action_c not completed
                setOf("item_1")    // item_1 in inventory
            ),
            "And expression should be true when all conditions are true"
        )
    }
    
    @Test
    fun testAndExpressionSomeFalse() {
        val andExpression = PreconditionExpression.And(listOf(
            PreconditionExpression.ActionRequired("action_a"),
            PreconditionExpression.ItemRequired("item_1"),
            PreconditionExpression.ActionForbidden("action_c")
        ))
        
        // Missing required action
        assertFalse(
            preconditionEngine.evaluate(
                andExpression,
                emptySet(),          // action_a not completed
                setOf("item_1")      // item_1 in inventory
            ),
            "And expression should be false when required action missing"
        )
        
        // Missing required item
        assertFalse(
            preconditionEngine.evaluate(
                andExpression,
                setOf("action_a"),   // action_a completed
                emptySet()           // item_1 not in inventory
            ),
            "And expression should be false when required item missing"
        )
        
        // Forbidden action completed
        assertFalse(
            preconditionEngine.evaluate(
                andExpression,
                setOf("action_a", "action_c"), // both actions completed (action_c is forbidden)
                setOf("item_1")     // item_1 in inventory
            ),
            "And expression should be false when forbidden action is completed"
        )
    }
    
    @Test
    fun testOrExpressionAtLeastOneTrue() {
        val orExpression = PreconditionExpression.Or(listOf(
            PreconditionExpression.ActionRequired("action_a"),
            PreconditionExpression.ActionRequired("action_b"),
            PreconditionExpression.ItemRequired("item_2")
        ))
        
        // First condition true
        assertTrue(
            preconditionEngine.evaluate(
                orExpression,
                setOf("action_a"),
                emptySet()
            ),
            "Or expression should be true when first condition is true"
        )
        
        // Second condition true
        assertTrue(
            preconditionEngine.evaluate(
                orExpression,
                setOf("action_b"),
                emptySet()
            ),
            "Or expression should be true when second condition is true"
        )
        
        // Third condition true
        assertTrue(
            preconditionEngine.evaluate(
                orExpression,
                emptySet(),
                setOf("item_2")
            ),
            "Or expression should be true when third condition is true"
        )
        
        // Multiple conditions true
        assertTrue(
            preconditionEngine.evaluate(
                orExpression,
                setOf("action_a", "action_b"),
                setOf("item_2")
            ),
            "Or expression should be true when multiple conditions are true"
        )
    }
    
    @Test
    fun testOrExpressionAllFalse() {
        val orExpression = PreconditionExpression.Or(listOf(
            PreconditionExpression.ActionRequired("action_a"),
            PreconditionExpression.ActionRequired("action_b"),
            PreconditionExpression.ItemRequired("item_2")
        ))
        
        assertFalse(
            preconditionEngine.evaluate(
                orExpression,
                emptySet(),    // no actions completed
                emptySet()     // no items in inventory
            ),
            "Or expression should be false when all conditions are false"
        )
        
        // Wrong actions and items
        assertFalse(
            preconditionEngine.evaluate(
                orExpression,
                setOf("action_c"),  // different action
                setOf("item_1")     // different item
            ),
            "Or expression should be false when all conditions are false (wrong actions/items)"
        )
    }
    
    @Test
    fun testNestedComplexExpressions() {
        // Complex nested: (action_a OR action_b) AND NOT action_c AND item_1
        val complexExpression = PreconditionExpression.And(listOf(
            PreconditionExpression.Or(listOf(
                PreconditionExpression.ActionRequired("action_a"),
                PreconditionExpression.ActionRequired("action_b")
            )),
            PreconditionExpression.ActionForbidden("action_c"),
            PreconditionExpression.ItemRequired("item_1")
        ))
        
        // Should be true: action_a completed, action_c not completed, item_1 in inventory
        assertTrue(
            preconditionEngine.evaluate(
                complexExpression,
                setOf("action_a"),
                setOf("item_1")
            ),
            "Complex expression should be true when all nested conditions are satisfied"
        )
        
        // Should be true: action_b completed (satisfies OR), action_c not completed, item_1 in inventory
        assertTrue(
            preconditionEngine.evaluate(
                complexExpression,
                setOf("action_b"),
                setOf("item_1")
            ),
            "Complex expression should be true with action_b satisfying OR condition"
        )
        
        // Should be false: neither action_a nor action_b completed
        assertFalse(
            preconditionEngine.evaluate(
                complexExpression,
                emptySet(),
                setOf("item_1")
            ),
            "Complex expression should be false when OR condition is not satisfied"
        )
        
        // Should be false: action_c completed (forbidden)
        assertFalse(
            preconditionEngine.evaluate(
                complexExpression,
                setOf("action_a", "action_c"),
                setOf("item_1")
            ),
            "Complex expression should be false when forbidden action is completed"
        )
        
        // Should be false: item_1 not in inventory
        assertFalse(
            preconditionEngine.evaluate(
                complexExpression,
                setOf("action_a"),
                emptySet()
            ),
            "Complex expression should be false when required item is missing"
        )
    }
    
    @Test
    fun testGetInventoryFromCompletedActions() {
        // No actions completed
        val emptyInventory = preconditionEngine.getInventory(testGameData, emptySet())
        assertTrue(emptyInventory.isEmpty(), "Inventory should be empty when no actions completed")
        
        // Single action completed
        val singleActionInventory = preconditionEngine.getInventory(testGameData, setOf("action_a"))
        assertEquals(setOf("item_1"), singleActionInventory, "Inventory should contain item from completed action")
        
        // Multiple actions completed
        val multipleActionsInventory = preconditionEngine.getInventory(testGameData, setOf("action_a", "action_b"))
        assertEquals(
            setOf("item_1", "item_2"), 
            multipleActionsInventory, 
            "Inventory should contain items from all completed actions"
        )
        
        // Action with no rewards
        val noRewardInventory = preconditionEngine.getInventory(testGameData, setOf("action_d"))
        assertTrue(noRewardInventory.isEmpty(), "Inventory should be empty when action has no rewards")
        
        // Non-existent action
        val invalidActionInventory = preconditionEngine.getInventory(testGameData, setOf("nonexistent"))
        assertTrue(invalidActionInventory.isEmpty(), "Inventory should be empty for non-existent actions")
    }
    
    @Test
    fun testGetAvailableActionsFiltering() {
        // No actions completed - only actions with Always precondition available, plus action_c (ActionForbidden is satisfied)
        val initialAvailable = preconditionEngine.getAvailableActions(testGameData, emptySet(), emptySet())
        val availableIds = initialAvailable.map { it.id }.toSet()
        assertTrue(availableIds.contains("action_a"), "action_a should be available initially (Always precondition)")
        assertTrue(availableIds.contains("action_c"), "action_c should be available initially (ActionForbidden satisfied when action_b not completed)")
        assertEquals(2, initialAvailable.size, "action_a and action_c should be available initially")
        
        // After completing action_a - action_b becomes available, action_d becomes available (has item_1)
        val afterActionA = preconditionEngine.getAvailableActions(
            testGameData, 
            setOf("action_a"), 
            setOf("item_1")
        )
        val availableAfterA = afterActionA.map { it.id }.toSet()
        assertTrue(availableAfterA.contains("action_b"), "action_b should be available after action_a")
        assertTrue(availableAfterA.contains("action_c"), "action_c should be available (action_b not forbidden yet)")
        assertTrue(availableAfterA.contains("action_d"), "action_d should be available (has item_1)")
        assertTrue(availableAfterA.contains("action_f"), "action_f should be available (has item_1)")
        assertFalse(availableAfterA.contains("action_a"), "action_a should not be available (already completed)")
        
        // After completing action_b - action_c becomes unavailable (forbidden), action_e becomes available
        val afterActionB = preconditionEngine.getAvailableActions(
            testGameData,
            setOf("action_a", "action_b"),
            setOf("item_1", "item_2")
        )
        val availableAfterB = afterActionB.map { it.id }.toSet()
        assertFalse(availableAfterB.contains("action_c"), "action_c should not be available (action_b forbidden)")
        assertTrue(availableAfterB.contains("action_e"), "action_e should be available (has both items)")
        assertTrue(availableAfterB.contains("action_d"), "action_d should still be available")
        assertTrue(availableAfterB.contains("action_f"), "action_f should still be available")
    }
    
    @Test
    fun testEmptyGameData() {
        val emptyGameData = GameData(
            gameId = "empty",
            name = "Empty Game",
            version = "1.0.0",
            actions = emptyMap(),
            items = emptyMap()
        )
        
        val inventory = preconditionEngine.getInventory(emptyGameData, setOf("any_action"))
        assertTrue(inventory.isEmpty(), "Empty game data should return empty inventory")
        
        val available = preconditionEngine.getAvailableActions(emptyGameData, emptySet(), emptySet())
        assertTrue(available.isEmpty(), "Empty game data should return no available actions")
    }
    
    @Test
    fun testComplexActionChain() {
        // Test a complex chain: action_a -> action_b -> action_e -> complex_action
        
        // Step 1: Complete action_a
        val step1Available = preconditionEngine.getAvailableActions(
            testGameData,
            setOf("action_a"),
            setOf("item_1")
        )
        assertTrue(
            step1Available.any { it.id == "action_b" },
            "action_b should be available after action_a"
        )
        
        // Step 2: Complete action_b
        val step2Available = preconditionEngine.getAvailableActions(
            testGameData,
            setOf("action_a", "action_b"),
            setOf("item_1", "item_2")
        )
        assertTrue(
            step2Available.any { it.id == "action_e" },
            "action_e should be available after having both items"
        )
        
        // Step 3: Check complex_action availability
        // complex_action requires: (action_a OR action_b) AND NOT action_c AND item_1
        assertTrue(
            preconditionEngine.evaluate(
                testGameData.actions["complex_action"]!!.preconditions,
                setOf("action_a", "action_b"),
                setOf("item_1", "item_2")
            ),
            "complex_action should be available with current state"
        )
        
        // Verify complex_action becomes unavailable if action_c is completed
        assertFalse(
            preconditionEngine.evaluate(
                testGameData.actions["complex_action"]!!.preconditions,
                setOf("action_a", "action_b", "action_c"),
                setOf("item_1", "item_2")
            ),
            "complex_action should be unavailable if action_c is completed"
        )
    }
}