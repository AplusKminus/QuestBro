package com.questbro.domain

import kotlin.test.*

class GameActionGraphTest {
    
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        // Create comprehensive test game data
        testGameData = GameData(
            gameId = "test-game",
            name = "Test Game",
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
                "kill_boss_a" to GameAction(
                    id = "kill_boss_a",
                    name = "Kill Boss A",
                    description = "Defeat the first boss",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("key_a", "Key A")),
                    category = ActionCategory.BOSS
                ),
                "kill_boss_b" to GameAction(
                    id = "kill_boss_b",
                    name = "Kill Boss B",
                    description = "Defeat the second boss",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("key_a"),
                        PreconditionExpression.ActionForbidden("bad_choice")
                    )),
                    rewards = listOf(Reward("treasure", "Ultimate Treasure")),
                    category = ActionCategory.BOSS
                ),
                "bad_choice" to GameAction(
                    id = "bad_choice",
                    name = "Make Bad Choice",
                    description = "This locks you out of boss B",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "optional_quest" to GameAction(
                    id = "optional_quest",
                    name = "Optional Quest",
                    description = "Side quest",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ActionRequired("kill_boss_a"),
                        PreconditionExpression.ActionRequired("bad_choice")
                    )),
                    rewards = listOf(Reward("side_reward", "Side Reward")),
                    category = ActionCategory.QUEST
                ),
                "final_boss" to GameAction(
                    id = "final_boss",
                    name = "Final Boss",
                    description = "Ultimate challenge",
                    preconditions = PreconditionExpression.ActionRequired("kill_boss_b"),
                    rewards = listOf(Reward("victory", "Victory")),
                    category = ActionCategory.BOSS
                )
            ),
            items = mapOf(
                "sword" to Item("sword", "Basic Sword", "Starting weapon"),
                "key_a" to Item("key_a", "Key A", "Opens boss B area"),
                "treasure" to Item("treasure", "Ultimate Treasure", "Final reward"),
                "side_reward" to Item("side_reward", "Side Reward", "Optional item"),
                "victory" to Item("victory", "Victory", "Game completion")
            )
        )
    }
    
    @Test
    fun testGraphCreation() {
        val graph = GameActionGraph.create(testGameData)
        
        assertNotNull(graph)
        assertTrue(graph.readyGoals.isEmpty())
        assertTrue(graph.achievableGoals.isEmpty())
        assertTrue(graph.unachievableGoals.isEmpty())
        assertTrue(graph.completedGoals.isEmpty())
        assertTrue(graph.currentActions.isNotEmpty()) // Should have "start" action available
        assertTrue(graph.completedActions.isEmpty())
    }
    
    @Test
    fun testGoalCategorization() {
        val goals = setOf(
            Goal("goal1", "start", "Start the game"),
            Goal("goal2", "kill_boss_a", "Kill Boss A"),
            Goal("goal3", "kill_boss_b", "Kill Boss B")
        )
        
        // Test initial state - start is ready, others are achievable
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        assertEquals(1, graph.readyGoals.size)
        assertEquals("start", graph.readyGoals[0].action.id)
        assertEquals(0, graph.readyGoals[0].pathLength)
        
        assertEquals(2, graph.achievableGoals.size)
        assertTrue(graph.achievableGoals.any { it.action.id == "kill_boss_a" })
        assertTrue(graph.achievableGoals.any { it.action.id == "kill_boss_b" })
        
        // Test after completing start
        val graphAfterStart = graph.performAction("start")
        
        assertEquals(1, graphAfterStart.completedGoals.size)
        assertEquals("start", graphAfterStart.completedGoals[0].action.id)
        
        assertEquals(1, graphAfterStart.readyGoals.size)
        assertEquals("kill_boss_a", graphAfterStart.readyGoals[0].action.id)
        
        assertEquals(1, graphAfterStart.achievableGoals.size)
        assertEquals("kill_boss_b", graphAfterStart.achievableGoals[0].action.id)
    }
    
    @Test
    fun testUnachievableGoals() {
        val goals = setOf(
            Goal("goal1", "kill_boss_b", "Kill Boss B")
        )
        
        // Complete bad_choice first, making boss B unachievable
        val graph = GameActionGraph.create(
            testGameData,
            completedActions = setOf("start", "bad_choice"),
            goals = goals
        )
        
        assertEquals(1, graph.unachievableGoals.size)
        assertEquals("kill_boss_b", graph.unachievableGoals[0].action.id)
        assertEquals(-1, graph.unachievableGoals[0].pathLength)
        assertTrue(graph.unachievableGoals[0].blockingActions.contains("bad_choice"))
    }
    
    @Test
    fun testActionAccessors() {
        val graph = GameActionGraph.create(testGameData, completedActions = setOf("start"))
        
        // Test current actions
        val currentActions = graph.currentActions
        assertTrue(currentActions.any { it.action.id == "kill_boss_a" })
        assertTrue(currentActions.any { it.action.id == "bad_choice" })
        assertFalse(currentActions.any { it.action.id == "start" }) // Already completed
        assertFalse(currentActions.any { it.action.id == "kill_boss_b" }) // Not available yet
        
        // Test completed actions
        val completedActions = graph.completedActions
        assertEquals(1, completedActions.size)
        assertEquals("start", completedActions[0].action.id)
        assertTrue(completedActions[0].canUndo) // No other actions depend on start yet
    }
    
    @Test
    fun testActionEnablesGoals() {
        val goals = setOf(
            Goal("goal1", "kill_boss_a", "Kill Boss A"),
            Goal("goal2", "optional_quest", "Optional Quest")
        )
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        val startAction = graph.currentActions.find { it.action.id == "start" }
        assertNotNull(startAction)
        
        // Start action should enable both goals
        assertTrue(startAction.enablesGoals.isNotEmpty())
    }
    
    @Test
    fun testActionBlocksGoals() {
        val goals = setOf(
            Goal("goal1", "kill_boss_b", "Kill Boss B")
        )
        val graph = GameActionGraph.create(testGameData, completedActions = setOf("start"), goals = goals)
        
        val badChoiceAction = graph.currentActions.find { it.action.id == "bad_choice" }
        assertNotNull(badChoiceAction)
        
        // Bad choice should block the boss B goal
        assertTrue(badChoiceAction.blocksGoals.any { it.targetId == "kill_boss_b" })
    }
    
    @Test
    fun testPerformAction() {
        val graph = GameActionGraph.create(testGameData)
        
        // Perform start action
        val newGraph = graph.performAction("start")
        
        assertNotEquals(graph, newGraph) // Should be a new instance
        assertTrue(newGraph.completedActions.any { it.action.id == "start" })
        assertFalse(newGraph.currentActions.any { it.action.id == "start" })
    }
    
    @Test
    fun testPerformActionPreconditionsNotMet() {
        val graph = GameActionGraph.create(testGameData)
        
        // Try to perform boss A without completing start
        assertFailsWith<IllegalArgumentException> {
            graph.performAction("kill_boss_a")
        }
    }
    
    @Test
    fun testUndoAction() {
        val graph = GameActionGraph.create(testGameData, completedActions = setOf("start"))
        
        // Undo start action
        val newGraph = graph.undoAction("start")
        
        assertFalse(newGraph.completedActions.any { it.action.id == "start" })
        assertTrue(newGraph.currentActions.any { it.action.id == "start" })
    }
    
    @Test
    fun testCannotUndoDependentAction() {
        val graph = GameActionGraph.create(testGameData, completedActions = setOf("start", "kill_boss_a"))
        
        // Cannot undo start because kill_boss_a depends on it
        val startAction = graph.completedActions.find { it.action.id == "start" }
        assertNotNull(startAction)
        assertFalse(startAction.canUndo)
        
        assertFailsWith<IllegalArgumentException> {
            graph.undoAction("start")
        }
    }
    
    @Test
    fun testAddRemoveGoals() {
        val graph = GameActionGraph.create(testGameData)
        val newGoals = setOf(Goal("goal1", "start", "Start the game"))
        
        // Add goals
        val graphWithGoals = graph.addGoals(newGoals)
        assertEquals(1, graphWithGoals.readyGoals.size)
        
        // Remove goals
        val graphWithoutGoals = graphWithGoals.removeGoals(newGoals)
        assertTrue(graphWithoutGoals.readyGoals.isEmpty())
    }
    
    @Test
    fun testConflictDetection() {
        val existingGoals = setOf(Goal("goal1", "kill_boss_b", "Kill Boss B"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Try to add conflicting goal (bad_choice conflicts with kill_boss_b)
        val conflictingGoal = Goal("goal2", "bad_choice", "Make Bad Choice")
        val conflicts = graph.checkConflictsWhenAddingGoal(conflictingGoal)
        
        assertTrue(conflicts.isNotEmpty())
        assertTrue(conflicts.any { it.severity == ConflictSeverity.MutualExclusion })
        assertTrue(conflicts.any { 
            it.involvedGoals.contains(conflictingGoal) && 
            it.involvedGoals.any { goal -> goal.targetId == "kill_boss_b" }
        })
    }
    
    @Test
    fun testConflictDetectionNoConflicts() {
        val existingGoals = setOf(Goal("goal1", "kill_boss_a", "Kill Boss A"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Add compatible goal
        val compatibleGoal = Goal("goal2", "optional_quest", "Optional Quest")
        val conflicts = graph.checkConflictsWhenAddingGoal(compatibleGoal)
        
        assertTrue(conflicts.isEmpty())
    }
    
    @Test
    fun testInducedConflicts() {
        // Set up scenario where adding a goal makes existing goals conflict
        val existingGoals = setOf(
            Goal("goal1", "kill_boss_a", "Kill Boss A"),
            Goal("goal2", "kill_boss_b", "Kill Boss B")
        )
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Adding bad_choice should create induced conflict with kill_boss_b
        val inducingGoal = Goal("goal3", "bad_choice", "Make Bad Choice")
        val conflicts = graph.checkConflictsWhenAddingGoal(inducingGoal)
        
        assertTrue(conflicts.isNotEmpty())
        // Should detect that bad_choice makes kill_boss_b unachievable
    }
    
    @Test
    fun testUnifiedPathPlanning() {
        val goals = setOf(
            Goal("goal1", "kill_boss_a", "Kill Boss A"),
            Goal("goal2", "optional_quest", "Optional Quest")
        )
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        val unifiedPath = graph.getUnifiedPathToGoals()
        
        assertFalse(unifiedPath.isEmpty())
        // Should include start action first
        assertEquals("start", unifiedPath[0].id)
        // Should include kill_boss_a before optional_quest (dependency order)
        val bossAIndex = unifiedPath.indexOfFirst { it.id == "kill_boss_a" }
        val questIndex = unifiedPath.indexOfFirst { it.id == "optional_quest" }
        assertTrue(bossAIndex < questIndex)
    }
    
    @Test
    fun testUnifiedPathWithNoGoals() {
        val graph = GameActionGraph.create(testGameData)
        val unifiedPath = graph.getUnifiedPathToGoals()
        assertTrue(unifiedPath.isEmpty())
    }
    
    @Test
    fun testComplexDependencyChain() {
        val goals = setOf(
            Goal("goal1", "final_boss", "Final Boss") // Requires: start -> kill_boss_a -> kill_boss_b -> final_boss
        )
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        // Should categorize as achievable with correct path length
        assertEquals(1, graph.achievableGoals.size)
        val finalBossGoal = graph.achievableGoals[0]
        assertEquals("final_boss", finalBossGoal.action.id)
        assertTrue(finalBossGoal.pathLength > 0)
        
        // Unified path should respect dependency order
        val unifiedPath = graph.getUnifiedPathToGoals()
        val expectedOrder = listOf("start", "kill_boss_a", "kill_boss_b", "final_boss")
        assertEquals(expectedOrder.size, unifiedPath.size)
        expectedOrder.forEachIndexed { index, expectedId ->
            assertEquals(expectedId, unifiedPath[index].id)
        }
    }
    
    @Test
    fun testCacheInvalidation() {
        val goals = setOf(Goal("goal1", "kill_boss_a", "Kill Boss A"))
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        // Initially achievable
        assertEquals(1, graph.achievableGoals.size)
        
        // After performing start, should become ready
        val newGraph = graph.performAction("start")
        assertEquals(1, newGraph.readyGoals.size)
        assertEquals("kill_boss_a", newGraph.readyGoals[0].action.id)
    }
    
    @Test
    fun testImmutability() {
        val graph = GameActionGraph.create(testGameData)
        val originalCurrentActionsSize = graph.currentActions.size
        
        // Performing action should not modify original graph
        val newGraph = graph.performAction("start")
        assertEquals(originalCurrentActionsSize, graph.currentActions.size)
        assertNotEquals(graph.currentActions.size, newGraph.currentActions.size)
    }
    
    @Test
    fun testPathLengthCalculation() {
        val goals = setOf(
            Goal("goal1", "start", "Start"), // Path length 0 (ready)
            Goal("goal2", "kill_boss_a", "Boss A"), // Path length 1 (start -> kill_boss_a)
            Goal("goal3", "kill_boss_b", "Boss B") // Path length 3 (start -> kill_boss_a -> kill_boss_b)
        )
        val graph = GameActionGraph.create(testGameData, goals = goals)
        
        assertEquals(0, graph.readyGoals[0].pathLength)
        
        val bossAGoal = graph.achievableGoals.find { it.action.id == "kill_boss_a" }
        assertNotNull(bossAGoal)
        assertEquals(1, bossAGoal.pathLength)
        
        val bossBGoal = graph.achievableGoals.find { it.action.id == "kill_boss_b" }
        assertNotNull(bossBGoal)
        assertTrue(bossBGoal.pathLength > 1) // Requires multiple steps
    }
}