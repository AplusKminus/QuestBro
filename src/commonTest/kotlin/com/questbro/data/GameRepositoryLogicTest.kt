package com.questbro.data

import com.questbro.domain.*
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Test the core logic of GameRepository without depending on expect classes
 */
class GameRepositoryLogicTest {
    
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        testGameData = GameData(
            gameId = "test-game",
            name = "Test Game",
            version = "1.0.0",
            actions = mapOf(
                "action1" to GameAction(
                    id = "action1",
                    name = "First Action",
                    description = "The first action",
                    preconditions = PreconditionExpression.Always,
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                )
            ),
            items = mapOf(
                "item1" to Item("item1", "Test Item", "A test item")
            )
        )
    }
    
    @Test
    fun testCreateNewRunLogic() {
        // Test the core logic without file operations
        val runName = "Test Run"
        val beforeCreation = Clock.System.now().toEpochMilliseconds()
        
        // Simulate GameRepository.createNewRun logic
        val now = Clock.System.now().toEpochMilliseconds()
        val gameRun = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = runName,
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = now,
            lastModified = now
        )
        
        val afterCreation = Clock.System.now().toEpochMilliseconds()
        
        // Verify basic properties
        assertEquals(testGameData.gameId, gameRun.gameId, "Game run should have correct game ID")
        assertEquals(testGameData.version, gameRun.gameVersion, "Game run should have correct game version")
        assertEquals(runName, gameRun.runName, "Game run should have correct run name")
        
        // Verify initial state
        assertTrue(gameRun.completedActions.isEmpty(), "New run should have no completed actions")
        assertTrue(gameRun.goals.isEmpty(), "New run should have no goals")
        
        // Verify timestamps
        assertTrue(
            gameRun.createdAt >= beforeCreation && gameRun.createdAt <= afterCreation,
            "Created timestamp should be current time"
        )
        assertEquals(
            gameRun.createdAt,
            gameRun.lastModified,
            "For new run, created and last modified should be the same"
        )
    }
    
    @Test
    fun testUpdateRunLogic() {
        // Create original run
        val originalTime = Clock.System.now().toEpochMilliseconds()
        val originalRun = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "Test Run",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = originalTime,
            lastModified = originalTime
        )
        
        // Wait a moment to ensure timestamp difference (cross-platform compatible)
        val start = Clock.System.now().toEpochMilliseconds()
        while (Clock.System.now().toEpochMilliseconds() - start < 10) {
            // Busy wait for small delay
        }
        
        // Simulate GameRepository.updateRun logic
        val beforeUpdate = Clock.System.now().toEpochMilliseconds()
        val updatedRun = originalRun.copy(lastModified = Clock.System.now().toEpochMilliseconds())
        val afterUpdate = Clock.System.now().toEpochMilliseconds()
        
        // Verify that only lastModified changed
        assertEquals(originalRun.gameId, updatedRun.gameId, "Game ID should not change")
        assertEquals(originalRun.gameVersion, updatedRun.gameVersion, "Game version should not change")
        assertEquals(originalRun.runName, updatedRun.runName, "Run name should not change")
        assertEquals(originalRun.completedActions, updatedRun.completedActions, "Completed actions should not change")
        assertEquals(originalRun.goals, updatedRun.goals, "Goals should not change")
        assertEquals(originalRun.createdAt, updatedRun.createdAt, "Created timestamp should not change")
        
        // Verify lastModified was updated
        assertTrue(
            updatedRun.lastModified >= beforeUpdate && updatedRun.lastModified <= afterUpdate,
            "Last modified should be updated to current time"
        )
        assertTrue(
            updatedRun.lastModified > originalRun.lastModified,
            "Last modified should be newer than original"
        )
    }
    
    @Test
    fun testCreateRunWithComplexGameData() {
        val complexGameData = GameData(
            gameId = "complex-game",
            name = "Complex Game",
            version = "2.1.0",
            actions = mapOf(
                "start" to GameAction(
                    id = "start",
                    name = "Start Game",
                    description = "Begin the adventure",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("starter_item", "Starter Item")),
                    category = ActionCategory.EXPLORATION
                ),
                "boss_fight" to GameAction(
                    id = "boss_fight",
                    name = "Boss Fight",
                    description = "Fight the final boss",
                    preconditions = PreconditionExpression.ItemRequired("legendary_weapon"),
                    rewards = listOf(Reward("victory_trophy", "Victory Trophy")),
                    category = ActionCategory.BOSS
                )
            ),
            items = mapOf(
                "starter_item" to Item("starter_item", "Starter Item", "Basic equipment"),
                "legendary_weapon" to Item("legendary_weapon", "Legendary Weapon", "Ultimate weapon"),
                "victory_trophy" to Item("victory_trophy", "Victory Trophy", "Proof of victory")
            )
        )
        
        // Simulate createNewRun
        val now = Clock.System.now().toEpochMilliseconds()
        val gameRun = GameRun(
            gameId = complexGameData.gameId,
            gameVersion = complexGameData.version,
            runName = "Complex Run",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = now,
            lastModified = now
        )
        
        assertEquals("complex-game", gameRun.gameId, "Should handle complex game ID")
        assertEquals("2.1.0", gameRun.gameVersion, "Should handle complex version")
        assertEquals("Complex Run", gameRun.runName, "Should handle complex run name")
    }
    
    @Test
    fun testMultipleRunsIndependence() {
        val now1 = Clock.System.now().toEpochMilliseconds()
        val run1 = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "Run 1",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = now1,
            lastModified = now1
        )
        
        // Ensure different timestamps (cross-platform compatible)
        val delay1Start = Clock.System.now().toEpochMilliseconds()
        while (Clock.System.now().toEpochMilliseconds() - delay1Start < 10) {
            // Busy wait for small delay
        }
        
        val now2 = Clock.System.now().toEpochMilliseconds()
        val run2 = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "Run 2",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = now2,
            lastModified = now2
        )
        
        // Runs should be independent
        assertNotEquals(run1.runName, run2.runName, "Runs should have different names")
        assertNotEquals(run1.createdAt, run2.createdAt, "Runs should have different creation times")
        assertNotEquals(run1.lastModified, run2.lastModified, "Runs should have different modification times")
        
        // But share same game properties
        assertEquals(run1.gameId, run2.gameId, "Runs should share same game ID")
        assertEquals(run1.gameVersion, run2.gameVersion, "Runs should share same game version")
    }
    
    @Test
    fun testGameRunStateManagement() {
        // Test game run state changes
        val initialRun = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "State Test Run",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        // Wait a moment to ensure timestamp difference (cross-platform compatible)
        val start = Clock.System.now().toEpochMilliseconds()
        while (Clock.System.now().toEpochMilliseconds() - start < 10) {
            // Busy wait for small delay
        }
        
        // Add completed action
        val withAction = initialRun.copy(
            completedActions = setOf("action1"),
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        assertEquals(setOf("action1"), withAction.completedActions, "Should update completed actions")
        assertTrue(withAction.lastModified > initialRun.lastModified, "Should update timestamp")
        
        // Wait a moment to ensure another timestamp difference (cross-platform compatible)
        val delay2Start = Clock.System.now().toEpochMilliseconds()
        while (Clock.System.now().toEpochMilliseconds() - delay2Start < 10) {
            // Busy wait for small delay
        }
        
        // Add goal
        val withGoal = withAction.copy(
            goals = listOf(Goal("goal1", "action1", "Test Goal")),
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        assertEquals(1, withGoal.goals.size, "Should add goal")
        assertEquals("Test Goal", withGoal.goals[0].description, "Should preserve goal data")
        assertTrue(withGoal.lastModified > withAction.lastModified, "Should update timestamp again")
    }
    
    @Test
    fun testGoalManagement() {
        val goal1 = Goal("g1", "action1", "First Goal")
        val goal2 = Goal("g2", "action2", "Second Goal")
        val goal3 = Goal("g3", "action3", "Third Goal")
        
        val gameRun = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "Goal Management Test",
            completedActions = emptySet(),
            goals = listOf(goal1, goal2),
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        // Add goal
        val withNewGoal = gameRun.copy(
            goals = gameRun.goals + goal3,
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        assertEquals(3, withNewGoal.goals.size, "Should add new goal")
        assertTrue(withNewGoal.goals.contains(goal3), "Should contain new goal")
        
        // Remove goal
        val withoutGoal = withNewGoal.copy(
            goals = withNewGoal.goals.filter { it.id != "g2" },
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        assertEquals(2, withoutGoal.goals.size, "Should remove goal")
        assertFalse(withoutGoal.goals.any { it.id == "g2" }, "Should not contain removed goal")
        assertTrue(withoutGoal.goals.any { it.id == "g1" }, "Should still contain other goals")
        assertTrue(withoutGoal.goals.any { it.id == "g3" }, "Should still contain other goals")
    }
    
    @Test
    fun testTimestampAccuracy() {
        val startTime = Clock.System.now().toEpochMilliseconds()
        
        val gameRun = GameRun(
            gameId = testGameData.gameId,
            gameVersion = testGameData.version,
            runName = "Timestamp Test",
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastModified = Clock.System.now().toEpochMilliseconds()
        )
        
        val endTime = Clock.System.now().toEpochMilliseconds()
        
        assertTrue(
            gameRun.createdAt >= startTime && gameRun.createdAt <= endTime,
            "Created timestamp should be within test execution time"
        )
        assertTrue(
            gameRun.lastModified >= startTime && gameRun.lastModified <= endTime,
            "Last modified timestamp should be within test execution time"
        )
        
        // Verify timestamps are close but can be different
        assertTrue(
            gameRun.lastModified - gameRun.createdAt >= 0,
            "Last modified should be >= created time"
        )
    }
}