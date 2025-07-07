package com.questbro.domain

import kotlin.test.*

/**
 * Advanced tests for PreconditionEngine covering edge cases and complex scenarios
 */
class PreconditionEngineAdvancedTest {
    
    private lateinit var engine: PreconditionEngine
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        engine = PreconditionEngine()
        
        testGameData = GameData(
            gameId = "advanced-test",
            name = "Advanced Test Game",
            version = "1.0.0",
            actions = mapOf(
                "start" to GameAction(
                    id = "start",
                    name = "Start",
                    description = "Starting action",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("basic_item", "Basic Item")),
                    category = ActionCategory.EXPLORATION
                ),
                "nested_and" to GameAction(
                    id = "nested_and",
                    name = "Nested AND",
                    description = "Action with nested AND conditions",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.And(listOf(
                            PreconditionExpression.ItemRequired("basic_item"),
                            PreconditionExpression.ActionForbidden("forbidden_action")
                        ))
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "nested_or" to GameAction(
                    id = "nested_or",
                    name = "Nested OR",
                    description = "Action with nested OR conditions",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.Or(listOf(
                            PreconditionExpression.ItemRequired("rare_item"),
                            PreconditionExpression.ActionRequired("alternative_path")
                        ))
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "complex_mixed" to GameAction(
                    id = "complex_mixed",
                    name = "Complex Mixed",
                    description = "Action with mixed AND/OR conditions",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.Or(listOf(
                            PreconditionExpression.ActionRequired("path_a"),
                            PreconditionExpression.ActionRequired("path_b")
                        )),
                        PreconditionExpression.And(listOf(
                            PreconditionExpression.ItemRequired("key_item"),
                            PreconditionExpression.ActionForbidden("blocking_action")
                        ))
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.BOSS
                ),
                "path_a" to GameAction(
                    id = "path_a",
                    name = "Path A",
                    description = "First path option",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("key_item", "Key Item")),
                    category = ActionCategory.EXPLORATION
                ),
                "path_b" to GameAction(
                    id = "path_b",
                    name = "Path B",
                    description = "Second path option",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("key_item", "Key Item")),
                    category = ActionCategory.EXPLORATION
                ),
                "forbidden_action" to GameAction(
                    id = "forbidden_action",
                    name = "Forbidden Action",
                    description = "This action blocks some paths",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "blocking_action" to GameAction(
                    id = "blocking_action",
                    name = "Blocking Action",
                    description = "This action blocks complex_mixed",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "alternative_path" to GameAction(
                    id = "alternative_path",
                    name = "Alternative Path",
                    description = "Alternative way to enable nested_or",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                )
            ),
            items = mapOf(
                "basic_item" to Item("basic_item", "Basic Item", "Starting item"),
                "key_item" to Item("key_item", "Key Item", "Important key"),
                "rare_item" to Item("rare_item", "Rare Item", "Very rare item")
            )
        )
    }
    
    @Test
    fun testNestedAndConditions() {
        // Test deeply nested AND conditions
        
        // Should fail without start action
        assertFalse(
            engine.evaluate(
                testGameData.actions["nested_and"]!!.preconditions,
                emptySet(),
                emptySet()
            ),
            "Nested AND should fail without required actions"
        )
        
        // Should fail with start but without item
        assertFalse(
            engine.evaluate(
                testGameData.actions["nested_and"]!!.preconditions,
                setOf("start"),
                emptySet()
            ),
            "Nested AND should fail without required item"
        )
        
        // Should fail if forbidden action is present
        assertFalse(
            engine.evaluate(
                testGameData.actions["nested_and"]!!.preconditions,
                setOf("start", "forbidden_action"),
                setOf("basic_item")
            ),
            "Nested AND should fail with forbidden action"
        )
        
        // Should succeed with all conditions met
        assertTrue(
            engine.evaluate(
                testGameData.actions["nested_and"]!!.preconditions,
                setOf("start"),
                setOf("basic_item")
            ),
            "Nested AND should succeed with all conditions met"
        )
    }
    
    @Test
    fun testNestedOrConditions() {
        // Test deeply nested OR conditions
        
        // Should succeed with start action (first OR branch)
        assertTrue(
            engine.evaluate(
                testGameData.actions["nested_or"]!!.preconditions,
                setOf("start"),
                emptySet()
            ),
            "Nested OR should succeed with start action"
        )
        
        // Should succeed with rare item (nested OR branch)
        assertTrue(
            engine.evaluate(
                testGameData.actions["nested_or"]!!.preconditions,
                emptySet(),
                setOf("rare_item")
            ),
            "Nested OR should succeed with rare item"
        )
        
        // Should succeed with alternative path (nested OR branch)
        assertTrue(
            engine.evaluate(
                testGameData.actions["nested_or"]!!.preconditions,
                setOf("alternative_path"),
                emptySet()
            ),
            "Nested OR should succeed with alternative path"
        )
        
        // Should fail with no conditions met
        assertFalse(
            engine.evaluate(
                testGameData.actions["nested_or"]!!.preconditions,
                emptySet(),
                emptySet()
            ),
            "Nested OR should fail with no conditions met"
        )
    }
    
    @Test
    fun testComplexMixedConditions() {
        // Test complex mixed AND/OR conditions
        val action = testGameData.actions["complex_mixed"]!!
        
        // Should fail without path
        assertFalse(
            engine.evaluate(
                action.preconditions,
                emptySet(),
                setOf("key_item")
            ),
            "Complex mixed should fail without path"
        )
        
        // Should fail without key item
        assertFalse(
            engine.evaluate(
                action.preconditions,
                setOf("path_a"),
                emptySet()
            ),
            "Complex mixed should fail without key item"
        )
        
        // Should fail with blocking action
        assertFalse(
            engine.evaluate(
                action.preconditions,
                setOf("path_a", "blocking_action"),
                setOf("key_item")
            ),
            "Complex mixed should fail with blocking action"
        )
        
        // Should succeed with path A and key item
        assertTrue(
            engine.evaluate(
                action.preconditions,
                setOf("path_a"),
                setOf("key_item")
            ),
            "Complex mixed should succeed with path A and key item"
        )
        
        // Should succeed with path B and key item
        assertTrue(
            engine.evaluate(
                action.preconditions,
                setOf("path_b"),
                setOf("key_item")
            ),
            "Complex mixed should succeed with path B and key item"
        )
    }
    
    @Test
    fun testGetAvailableActionsWithComplexScenario() {
        // Test getAvailableActions with complex dependencies
        
        // Initial state - only start should be available
        val initial = engine.getAvailableActions(testGameData, emptySet(), emptySet())
        assertEquals(1, initial.size, "Should have only start action initially")
        assertEquals("start", initial[0].id, "Start should be available")
        
        // After start - several actions should become available
        val afterStart = engine.getAvailableActions(
            testGameData, 
            setOf("start"), 
            setOf("basic_item")
        )
        val availableIds = afterStart.map { it.id }.toSet()
        assertTrue(availableIds.contains("path_a"), "Path A should be available after start")
        assertTrue(availableIds.contains("path_b"), "Path B should be available after start")
        assertTrue(availableIds.contains("nested_and"), "Nested AND should be available after start")
        assertTrue(availableIds.contains("nested_or"), "Nested OR should be available after start")
        assertTrue(availableIds.contains("forbidden_action"), "Forbidden action should be available after start")
        assertTrue(availableIds.contains("blocking_action"), "Blocking action should be available after start")
        assertTrue(availableIds.contains("alternative_path"), "Alternative path should be available after start")
        
        // After taking one path - complex_mixed should become available
        val afterPathA = engine.getAvailableActions(
            testGameData,
            setOf("start", "path_a"),
            setOf("basic_item", "key_item")
        )
        val pathAIds = afterPathA.map { it.id }.toSet()
        assertTrue(pathAIds.contains("complex_mixed"), "Complex mixed should be available after path A")
        
        // After blocking action - complex_mixed should NOT be available
        val afterBlocking = engine.getAvailableActions(
            testGameData,
            setOf("start", "path_a", "blocking_action"),
            setOf("basic_item", "key_item")
        )
        val blockingIds = afterBlocking.map { it.id }.toSet()
        assertFalse(blockingIds.contains("complex_mixed"), "Complex mixed should not be available after blocking action")
    }
    
    @Test
    fun testInventoryCalculation() {
        // Test inventory calculation with multiple actions and rewards
        
        // No actions completed
        val emptyInventory = engine.getInventory(testGameData, emptySet())
        assertTrue(emptyInventory.isEmpty(), "Empty inventory with no actions")
        
        // After start action
        val afterStart = engine.getInventory(testGameData, setOf("start"))
        assertEquals(setOf("basic_item"), afterStart, "Should have basic item after start")
        
        // After path A (adds key_item)
        val afterPathA = engine.getInventory(testGameData, setOf("start", "path_a"))
        assertEquals(setOf("basic_item", "key_item"), afterPathA, "Should have both items after path A")
        
        // After both paths (should still have same items, no duplicates)
        val afterBothPaths = engine.getInventory(testGameData, setOf("start", "path_a", "path_b"))
        assertEquals(setOf("basic_item", "key_item"), afterBothPaths, "Should have unique items even from multiple sources")
        
        // Invalid action ID should be ignored
        val withInvalid = engine.getInventory(testGameData, setOf("start", "invalid_action"))
        assertEquals(setOf("basic_item"), withInvalid, "Should ignore invalid action IDs")
    }
    
    @Test
    fun testEmptyExpressions() {
        // Test edge cases with empty expressions
        
        // Empty AND should evaluate to true
        assertTrue(
            engine.evaluate(
                PreconditionExpression.And(emptyList()),
                emptySet(),
                emptySet()
            ),
            "Empty AND should be true"
        )
        
        // Empty OR should evaluate to false
        assertFalse(
            engine.evaluate(
                PreconditionExpression.Or(emptyList()),
                emptySet(),
                emptySet()
            ),
            "Empty OR should be false"
        )
    }
    
    @Test
    fun testPerformanceWithDeepNesting() {
        // Test performance with deeply nested expressions
        
        fun createDeepNested(depth: Int): PreconditionExpression {
            if (depth <= 0) return PreconditionExpression.Always
            return PreconditionExpression.And(listOf(
                PreconditionExpression.Always,
                createDeepNested(depth - 1)
            ))
        }
        
        val deepExpression = createDeepNested(100)
        val startTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        val result = engine.evaluate(deepExpression, emptySet(), emptySet())
        
        val endTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        val duration = endTime - startTime
        
        assertTrue(result, "Deep nested expression should evaluate to true")
        assertTrue(duration < 100, "Deep nested evaluation should complete quickly (took ${duration}ms)")
    }
    
    @Test
    fun testCircularLogicResistance() {
        // Test that the engine handles potentially circular logic gracefully
        
        // Create actions that could theoretically create circular dependencies
        val circularGameData = GameData(
            gameId = "circular-test",
            name = "Circular Test",
            version = "1.0.0",
            actions = mapOf(
                "action_a" to GameAction(
                    id = "action_a",
                    name = "Action A",
                    description = "Requires B to be forbidden",
                    preconditions = PreconditionExpression.ActionForbidden("action_b"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "action_b" to GameAction(
                    id = "action_b",
                    name = "Action B", 
                    description = "Requires A to be forbidden",
                    preconditions = PreconditionExpression.ActionForbidden("action_a"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                )
            ),
            items = emptyMap()
        )
        
        // Both actions should be available initially (neither is completed)
        val available = engine.getAvailableActions(circularGameData, emptySet(), emptySet())
        assertEquals(2, available.size, "Both mutually exclusive actions should be available initially")
        
        // After completing A, B should not be available
        val afterA = engine.getAvailableActions(circularGameData, setOf("action_a"), emptySet())
        assertEquals(0, afterA.size, "No actions should be available after completing A")
        
        // After completing B, A should not be available
        val afterB = engine.getAvailableActions(circularGameData, setOf("action_b"), emptySet())
        assertEquals(0, afterB.size, "No actions should be available after completing B")
    }
}