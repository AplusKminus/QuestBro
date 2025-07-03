package com.questbro.domain

import kotlin.test.*

class PathAnalyzerActionSortingTest {
    
    private lateinit var preconditionEngine: PreconditionEngine
    private lateinit var pathAnalyzer: PathAnalyzer
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        preconditionEngine = PreconditionEngine()
        pathAnalyzer = PathAnalyzer(preconditionEngine)
        
        // Create test game data with actions that fall into different sorting categories
        testGameData = GameData(
            gameId = "test-game",
            name = "Action Sorting Test Game",
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
                "goal_action" to GameAction(
                    id = "goal_action",
                    name = "Goal Action",
                    description = "Action that directly fulfills a goal",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("treasure", "Goal Treasure")),
                    category = ActionCategory.QUEST
                ),
                "progress_action" to GameAction(
                    id = "progress_action",
                    name = "Progress Action",
                    description = "Action required for goal progress",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("key", "Important Key")),
                    category = ActionCategory.EXPLORATION
                ),
                "final_goal" to GameAction(
                    id = "final_goal",
                    name = "Final Goal",
                    description = "Ultimate goal requiring progress action",
                    preconditions = PreconditionExpression.ActionRequired("progress_action"),
                    rewards = listOf(Reward("victory", "Victory")),
                    category = ActionCategory.BOSS
                ),
                "neutral_action" to GameAction(
                    id = "neutral_action",
                    name = "Neutral Action",
                    description = "Action that doesn't affect goals",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("optional_item", "Optional Item")),
                    category = ActionCategory.QUEST
                ),
                "blocking_action" to GameAction(
                    id = "blocking_action",
                    name = "Blocking Action",
                    description = "Action that blocks goals",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                ),
                "blocked_goal" to GameAction(
                    id = "blocked_goal",
                    name = "Blocked Goal",
                    description = "Goal that gets blocked by blocking_action",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.ActionForbidden("blocking_action")
                    )),
                    rewards = listOf(Reward("blocked_reward", "Blocked Reward")),
                    category = ActionCategory.BOSS
                )
            ),
            items = mapOf(
                "sword" to Item("sword", "Basic Sword", "Starting weapon"),
                "treasure" to Item("treasure", "Goal Treasure", "Goal reward"),
                "key" to Item("key", "Important Key", "Required for final goal"),
                "victory" to Item("victory", "Victory", "Ultimate reward"),
                "optional_item" to Item("optional_item", "Optional Item", "Not required"),
                "blocked_reward" to Item("blocked_reward", "Blocked Reward", "Cannot be obtained")
            )
        )
    }
    
    @Test
    fun testActionSorting() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Action Sorting Test",
            completedActions = setOf("start"), // Start completed, others available
            goals = listOf(
                Goal("goal1", "goal_action", "Direct Goal"),
                Goal("goal2", "final_goal", "Progress Goal"),
                Goal("goal3", "blocked_goal", "Blocked Goal")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(testGameData, gameRun)
        
        // Filter to only available actions (excluding completed ones)
        val availableAnalyses = analyses.filter { it.isAvailable && !gameRun.completedActions.contains(it.action.id) }
        
        // Verify sorting order: directly fulfills goals first
        val firstAction = availableAnalyses.first()
        assertTrue(
            firstAction.directlyFulfillsGoals.isNotEmpty(),
            "First action should directly fulfill goals, but got: ${firstAction.action.name}"
        )
        
        // Find different categories in the sorted list
        val directlyFulfillsActions = availableAnalyses.filter { it.directlyFulfillsGoals.isNotEmpty() }
        val progressActions = availableAnalyses.filter { 
            it.directlyFulfillsGoals.isEmpty() && it.requiredForGoals.isNotEmpty() 
        }
        val neutralActions = availableAnalyses.filter { 
            it.directlyFulfillsGoals.isEmpty() && it.requiredForGoals.isEmpty() && it.wouldBreakGoals.isEmpty() 
        }
        val blockingActions = availableAnalyses.filter { it.wouldBreakGoals.isNotEmpty() }
        
        // Verify we have actions in each category
        assertTrue(directlyFulfillsActions.isNotEmpty(), "Should have actions that directly fulfill goals")
        assertTrue(progressActions.isNotEmpty(), "Should have actions that make progress on goals")
        assertTrue(neutralActions.isNotEmpty(), "Should have neutral actions")
        assertTrue(blockingActions.isNotEmpty(), "Should have actions that would block goals")
        
        // Verify sorting order by checking indices
        val allIndices = availableAnalyses.mapIndexed { index, analysis -> index to analysis }
        
        val directlyFulfillsIndices = allIndices.filter { it.second.directlyFulfillsGoals.isNotEmpty() }.map { it.first }
        val progressIndices = allIndices.filter { 
            it.second.directlyFulfillsGoals.isEmpty() && it.second.requiredForGoals.isNotEmpty() 
        }.map { it.first }
        val neutralIndices = allIndices.filter { 
            it.second.directlyFulfillsGoals.isEmpty() && it.second.requiredForGoals.isEmpty() && it.second.wouldBreakGoals.isEmpty() 
        }.map { it.first }
        val blockingIndices = allIndices.filter { it.second.wouldBreakGoals.isNotEmpty() }.map { it.first }
        
        // Verify category ordering: directly fulfills < progress < neutral < blocking
        if (directlyFulfillsIndices.isNotEmpty() && progressIndices.isNotEmpty()) {
            assertTrue(directlyFulfillsIndices.max() < progressIndices.min(), 
                "Directly fulfilling actions should come before progress actions")
        }
        if (progressIndices.isNotEmpty() && neutralIndices.isNotEmpty()) {
            assertTrue(progressIndices.max() < neutralIndices.min(), 
                "Progress actions should come before neutral actions")
        }
        if (neutralIndices.isNotEmpty() && blockingIndices.isNotEmpty()) {
            assertTrue(neutralIndices.max() < blockingIndices.min(), 
                "Neutral actions should come before blocking actions")
        }
        
        // Most importantly: blocking actions should NEVER be first
        if (blockingIndices.isNotEmpty()) {
            assertFalse(blockingIndices.contains(0), 
                "Blocking actions should never be first in the sorted list")
        }
    }
    
    @Test
    fun testComprehensiveProgressTracking() {
        // Test that actions show contribution to ALL goals they help with, including indirect ones
        val complexGameData = testGameData.copy(
            actions = testGameData.actions + mapOf(
                "step1" to GameAction(
                    id = "step1",
                    name = "Step 1",
                    description = "First step in a long chain",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("item1", "Item 1")),
                    category = ActionCategory.EXPLORATION
                ),
                "step2" to GameAction(
                    id = "step2", 
                    name = "Step 2",
                    description = "Second step requiring item1",
                    preconditions = PreconditionExpression.ItemRequired("item1"),
                    rewards = listOf(Reward("item2", "Item 2")),
                    category = ActionCategory.EXPLORATION
                ),
                "final_boss" to GameAction(
                    id = "final_boss",
                    name = "Final Boss",
                    description = "Requires both item1 and item2",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("item1"),
                        PreconditionExpression.ItemRequired("item2")
                    )),
                    rewards = listOf(Reward("victory", "Victory")),
                    category = ActionCategory.BOSS
                )
            ),
            items = testGameData.items + mapOf(
                "item1" to Item("item1", "Item 1", "First item"),
                "item2" to Item("item2", "Item 2", "Second item")
            )
        )
        
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0", 
            runName = "Comprehensive Progress Test",
            completedActions = setOf("start"), // Only start completed
            goals = listOf(
                Goal("goal1", "final_boss", "Beat Final Boss") // Goal that requires a long chain
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(complexGameData, gameRun)
        
        // Find step1 analysis - it should contribute to the final boss goal even though it's indirect
        val step1Analysis = analyses.find { it.action.id == "step1" }
        assertNotNull(step1Analysis, "Step1 analysis should exist")
        
        // Step1 should be recognized as contributing to the final boss goal
        assertTrue(
            step1Analysis.requiredForGoals.any { it.targetId == "final_boss" },
            "Step1 should be recognized as contributing to final boss goal (provides required item1)"
        )
        
        // Find step2 analysis - it should also contribute to the final boss goal
        val step2Analysis = analyses.find { it.action.id == "step2" }
        assertNotNull(step2Analysis, "Step2 analysis should exist")
        
        // Step2 should be recognized as contributing to the final boss goal
        assertTrue(
            step2Analysis.requiredForGoals.any { it.targetId == "final_boss" },
            "Step2 should be recognized as contributing to final boss goal (provides required item2)"
        )
    }
    
    @Test
    fun testWithinCategorySorting() {
        // Test sorting within categories
        val complexGameData = testGameData.copy(
            actions = testGameData.actions + mapOf(
                "multi_goal_action" to GameAction(
                    id = "multi_goal_action",
                    name = "Multi Goal Action",
                    description = "Action that fulfills multiple goals",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(
                        Reward("treasure", "Goal Treasure"),
                        Reward("victory", "Victory") // Fulfills both goals
                    ),
                    category = ActionCategory.BOSS
                ),
                "single_goal_action" to GameAction(
                    id = "single_goal_action",
                    name = "Single Goal Action",
                    description = "Action that fulfills one goal",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = listOf(Reward("treasure", "Goal Treasure")),
                    category = ActionCategory.QUEST
                )
            )
        )
        
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Within Category Sorting Test",
            completedActions = setOf("start"),
            goals = listOf(
                Goal("goal1", "goal_action", "Direct Goal 1"),
                Goal("goal2", "final_goal", "Direct Goal 2")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(complexGameData, gameRun)
        val availableAnalyses = analyses.filter { it.isAvailable && !gameRun.completedActions.contains(it.action.id) }
        
        // Find actions that directly fulfill goals
        val directlyFulfillsActions = availableAnalyses.filter { it.directlyFulfillsGoals.isNotEmpty() }
        
        // Multi-goal action should come before single-goal action (more goals fulfilled = higher priority)
        if (directlyFulfillsActions.size >= 2) {
            val firstDirectAction = directlyFulfillsActions[0]
            val secondDirectAction = directlyFulfillsActions[1]
            
            assertTrue(
                firstDirectAction.directlyFulfillsGoals.size >= secondDirectAction.directlyFulfillsGoals.size,
                "Actions fulfilling more goals should come first"
            )
        }
    }
}