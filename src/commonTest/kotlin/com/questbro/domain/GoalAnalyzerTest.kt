package com.questbro.domain

import kotlin.test.*

class GoalAnalyzerTest {
    
    private lateinit var preconditionEngine: PreconditionEngine
    private lateinit var goalAnalyzer: GoalAnalyzer
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        preconditionEngine = PreconditionEngine()
        goalAnalyzer = GoalAnalyzer(preconditionEngine)
        
        // Create test game data with a simple progression graph
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
                )
            ),
            items = mapOf(
                "sword" to Item("sword", "Basic Sword", "Starting weapon"),
                "key_a" to Item("key_a", "Key A", "Opens boss B area"),
                "treasure" to Item("treasure", "Ultimate Treasure", "Final reward"),
                "side_reward" to Item("side_reward", "Side Reward", "Optional item")
            )
        )
    }
    
    @Test
    fun testDirectlyAchievableActionGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = setOf("start"),
            goals = listOf(
                Goal("goal1", "kill_boss_a", "Kill Boss A")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.DIRECTLY_ACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testAchievableActionGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0", 
            runName = "Test Run",
            completedActions = emptySet(),
            goals = listOf(
                Goal("goal1", "kill_boss_a", "Kill Boss A")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.ACHIEVABLE, analyzedGoals[0].achievability)
        assertTrue(analyzedGoals[0].requiredActions.contains("start"))
    }
    
    @Test
    fun testUnachievableActionGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run", 
            completedActions = setOf("start", "bad_choice"), // This blocks boss B
            goals = listOf(
                Goal("goal1", "kill_boss_b", "Kill Boss B")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.UNACHIEVABLE, analyzedGoals[0].achievability)
        assertTrue(analyzedGoals[0].blockingActions.contains("bad_choice"))
    }
    
    @Test
    fun testDirectlyAchievableItemGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = setOf("start"), // This gives us the sword via start action
            goals = listOf(
                Goal("goal1", "start", "Get Basic Sword") // Target the action that provides the sword
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.DIRECTLY_ACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testAchievableItemGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = emptySet(),
            goals = listOf(
                Goal("goal1", "kill_boss_a", "Get Key A") // Target the action that provides key_a
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.ACHIEVABLE, analyzedGoals[0].achievability)
        assertTrue(analyzedGoals[0].requiredActions.contains("start"))
    }
    
    @Test
    fun testUnachievableItemGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = setOf("start", "bad_choice"), // Blocks treasure path
            goals = listOf(
                Goal("goal1", "kill_boss_b", "Get Ultimate Treasure") // Target the action that provides treasure
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.UNACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testOrPreconditionHandling() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = setOf("start", "bad_choice"), // Satisfies OR condition
            goals = listOf(
                Goal("goal1", "optional_quest", "Do Optional Quest")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.DIRECTLY_ACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testCompletedActionGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = setOf("start", "kill_boss_a"),
            goals = listOf(
                Goal("goal1", "kill_boss_a", "Kill Boss A")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.DIRECTLY_ACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testNonexistentGoal() {
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Test Run",
            completedActions = emptySet(),
            goals = listOf(
                Goal("goal1", "nonexistent_action", "Nonexistent Action")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals = goalAnalyzer.analyzeGoals(testGameData, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.UNACHIEVABLE, analyzedGoals[0].achievability)
    }
    
    @Test
    fun testConflictingGoals() {
        // Test scenario: Two mutually exclusive goals
        // Goal 1: assist_millicent_defeat_sisters 
        // Goal 2: assist_sisters_kill_millicent
        // These goals have ActionForbidden preconditions that make them mutually exclusive
        
        val conflictingGameData = testGameData.copy(
            actions = testGameData.actions + mapOf(
                "reach_final_area" to GameAction(
                    id = "reach_final_area",
                    name = "Reach Final Area",
                    description = "Access the final confrontation area",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "assist_millicent" to GameAction(
                    id = "assist_millicent",
                    name = "Assist Millicent",
                    description = "Help Millicent defeat her sisters",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("reach_final_area"),
                        PreconditionExpression.ActionForbidden("assist_sisters")
                    )),
                    rewards = listOf(Reward("millicent_reward", "Millicent's Reward")),
                    category = ActionCategory.QUEST
                ),
                "assist_sisters" to GameAction(
                    id = "assist_sisters",
                    name = "Assist Sisters",
                    description = "Help the sisters kill Millicent",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("reach_final_area"),
                        PreconditionExpression.ActionForbidden("assist_millicent")
                    )),
                    rewards = listOf(Reward("sisters_reward", "Sisters' Reward")),
                    category = ActionCategory.QUEST
                )
            ),
            items = testGameData.items + mapOf(
                "millicent_reward" to Item("millicent_reward", "Millicent's Reward", "Reward for helping Millicent"),
                "sisters_reward" to Item("sisters_reward", "Sisters' Reward", "Reward for helping sisters")
            )
        )
        
        // Test Case 1: Both goals should be achievable when neither action is completed
        val gameRun1 = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Conflict Test Run",
            completedActions = setOf("start", "reach_final_area"),
            goals = listOf(
                Goal("goal1", "assist_millicent", "Assist Millicent"),
                Goal("goal2", "assist_sisters", "Assist Sisters")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals1 = goalAnalyzer.analyzeGoals(conflictingGameData, gameRun1)
        
        // Both goals should be individually achievable at this point
        assertEquals(2, analyzedGoals1.size)
        assertTrue(analyzedGoals1.all { it.achievability != GoalAchievability.UNACHIEVABLE })
        
        // Test Case 2: After completing one action, the other goal should become unachievable
        val gameRun2 = GameRun(
            gameId = "test-game", 
            gameVersion = "1.0.0",
            runName = "Conflict Test Run",
            completedActions = setOf("start", "reach_final_area", "assist_millicent"), // Completed one conflicting action
            goals = listOf(
                Goal("goal1", "assist_millicent", "Assist Millicent"),
                Goal("goal2", "assist_sisters", "Assist Sisters")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzedGoals2 = goalAnalyzer.analyzeGoals(conflictingGameData, gameRun2)
        
        // First goal (assist_millicent) should be directly achievable (already completed)
        val millicent_goal = analyzedGoals2.find { it.goal.targetId == "assist_millicent" }
        assertNotNull(millicent_goal)
        assertEquals(GoalAchievability.DIRECTLY_ACHIEVABLE, millicent_goal.achievability)
        
        // Second goal (assist_sisters) should be unachievable due to the conflict
        val sisters_goal = analyzedGoals2.find { it.goal.targetId == "assist_sisters" }
        assertNotNull(sisters_goal)
        assertEquals(GoalAchievability.UNACHIEVABLE, sisters_goal.achievability)
        assertTrue(sisters_goal.blockingActions.contains("assist_millicent"))
    }
    
    @Test
    fun testSimpleGoalConflictDetection() {
        // Simple test: goal becomes unachievable when a conflicting action is completed
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Simple Conflict Test",
            completedActions = setOf("start", "bad_choice"), // bad_choice blocks kill_boss_b
            goals = emptyList(),
            createdAt = 0,
            lastModified = 0
        )
        
        // Test if a new goal for kill_boss_b would be achievable
        val newGoal = Goal("new_goal", "kill_boss_b", "Kill Boss B")
        val inventory = preconditionEngine.getInventory(testGameData, gameRun.completedActions)
        val analyzedGoal = goalAnalyzer.analyzeGoal(newGoal, testGameData, gameRun.completedActions, inventory)
        
        // Should be unachievable because bad_choice was already completed
        assertEquals(GoalAchievability.UNACHIEVABLE, analyzedGoal.achievability)
        assertTrue(analyzedGoal.blockingActions.contains("bad_choice"))
    }
    
    @Test
    fun testPathAnalyzerConflictDetection() {
        // Test the PathAnalyzer's ability to detect conflicts
        val pathAnalyzer = PathAnalyzer(preconditionEngine)
        
        // Set up a game run with a goal that has ActionForbidden preconditions
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Path Analyzer Test",
            completedActions = setOf("start"), // Only start completed
            goals = listOf(
                Goal("goal1", "kill_boss_b", "Kill Boss B") // Requires ActionForbidden("bad_choice")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(testGameData, gameRun)
        
        // Find the analysis for "bad_choice" action
        val badChoiceAnalysis = analyses.find { it.action.id == "bad_choice" }
        assertNotNull(badChoiceAnalysis)
        assertTrue(badChoiceAnalysis.isAvailable) // Should be available
        
        // This should detect that bad_choice would break the kill_boss_b goal
        assertTrue(
            badChoiceAnalysis.wouldBreakGoals.any { it.targetId == "kill_boss_b" },
            "bad_choice should be detected as breaking the kill_boss_b goal"
        )
    }
    
    @Test
    fun testStartActionNotConflictingWithBasicGoals() {
        // Test the specific issue: starting action being marked as conflicting when it shouldn't be
        val pathAnalyzer = PathAnalyzer(preconditionEngine)
        
        // Set up a run with just a basic goal (like killing Boss A)
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "Start Action Test",
            completedActions = emptySet(), // Nothing completed yet
            goals = listOf(
                Goal("goal1", "kill_boss_a", "Kill Boss A") // Simple goal that requires "start"
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(testGameData, gameRun)
        
        // Find the analysis for "start" action
        val startAnalysis = analyses.find { it.action.id == "start" }
        assertNotNull(startAnalysis)
        assertTrue(startAnalysis.isAvailable) // Should be available
        
        // The "start" action should NOT break the kill_boss_a goal (it's required for it!)
        assertFalse(
            startAnalysis.wouldBreakGoals.any { it.targetId == "kill_boss_a" },
            "start action should NOT be marked as breaking the kill_boss_a goal"
        )
        
        // In fact, start should be required for the goal
        assertTrue(
            startAnalysis.requiredForGoals.any { it.targetId == "kill_boss_a" },
            "start action should be marked as required for kill_boss_a goal"
        )
    }
    
    @Test 
    fun testPathAnalyzerWithOrConditions() {
        // Test PathAnalyzer with OR preconditions similar to Elden Ring's "reach_liurnia"
        val orGameData = testGameData.copy(
            actions = testGameData.actions + mapOf(
                "reach_area" to GameAction(
                    id = "reach_area",
                    name = "Reach Area",
                    description = "Can be reached via multiple paths",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ActionRequired("kill_boss_a"), // Path 1: kill boss
                        PreconditionExpression.ActionRequired("start")        // Path 2: just start
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                )
            )
        )
        
        val pathAnalyzer = PathAnalyzer(preconditionEngine)
        
        // Set up a run with a goal to reach the area
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0",
            runName = "OR Condition Test",
            completedActions = emptySet(), // Nothing completed yet
            goals = listOf(
                Goal("goal1", "reach_area", "Reach Area")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyses = pathAnalyzer.analyzeActions(orGameData, gameRun)
        
        // Find analysis for "start" action
        val startAnalysis = analyses.find { it.action.id == "start" }
        assertNotNull(startAnalysis)
        
        // The "start" action should NOT break the "reach_area" goal
        // because OR condition means reaching area is achievable via start OR boss kill
        assertFalse(
            startAnalysis.wouldBreakGoals.any { it.targetId == "reach_area" },
            "start action should NOT break reach_area goal (OR condition allows multiple paths)"
        )
        
        // Find analysis for "kill_boss_a" action  
        val bossAnalysis = analyses.find { it.action.id == "kill_boss_a" }
        assertNotNull(bossAnalysis)
        
        // "kill_boss_a" should also NOT break the goal
        assertFalse(
            bossAnalysis.wouldBreakGoals.any { it.targetId == "reach_area" },
            "kill_boss_a action should NOT break reach_area goal (OR condition allows multiple paths)"
        )
    }
    
    @Test
    fun testShortestPathForOrPreconditions() {
        // Create a test scenario with OR preconditions where one path is shorter
        val gameDataWithOrPaths = testGameData.copy(
            actions = testGameData.actions + mapOf(
                "short_path" to GameAction(
                    id = "short_path",
                    name = "Short Path",
                    description = "Quick route",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "long_path" to GameAction(
                    id = "long_path", 
                    name = "Long Path",
                    description = "Longer route",
                    preconditions = PreconditionExpression.ActionRequired("kill_boss_a"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "target_with_or" to GameAction(
                    id = "target_with_or",
                    name = "Target with OR",
                    description = "Can be reached via short or long path",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ActionRequired("short_path"),
                        PreconditionExpression.ActionRequired("long_path")
                    )),
                    rewards = listOf(Reward("or_reward", "OR Reward")),
                    category = ActionCategory.QUEST
                )
            )
        )
        
        val gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0.0", 
            runName = "Test Run",
            completedActions = emptySet(),
            goals = listOf(
                Goal("goal1", "target_with_or", "Target with OR")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val analyzer = GoalAnalyzer(preconditionEngine)
        val analyzedGoals = analyzer.analyzeGoals(gameDataWithOrPaths, gameRun)
        
        assertEquals(1, analyzedGoals.size)
        assertEquals(GoalAchievability.ACHIEVABLE, analyzedGoals[0].achievability)
        
        // Should choose the shorter path (2 actions needed: start -> short_path)  
        // vs the longer path (3 actions needed: start -> kill_boss_a -> long_path)
        assertEquals(2, analyzedGoals[0].requiredActions.size)
        
        // Verify it chose the shorter path by checking specific actions
        assertTrue(analyzedGoals[0].requiredActions.contains("start"))
        assertTrue(analyzedGoals[0].requiredActions.contains("short_path"))
        assertFalse(analyzedGoals[0].requiredActions.contains("kill_boss_a"))
        assertFalse(analyzedGoals[0].requiredActions.contains("long_path"))
    }
}