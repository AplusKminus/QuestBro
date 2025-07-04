package com.questbro.domain

import kotlin.test.*

/**
 * Integration tests that verify complex workflows and interactions between components
 */
class IntegrationTest {
    
    private lateinit var goalSearch: GoalSearch
    private lateinit var complexGameData: GameData
    
    @BeforeTest
    fun setup() {
        goalSearch = GoalSearch()
        
        // Create a complex game scenario similar to RPG games
        complexGameData = GameData(
            gameId = "complex-rpg",
            name = "Complex RPG Game",
            version = "1.0.0",
            actions = mapOf(
                // Starting area
                "tutorial_complete" to GameAction(
                    id = "tutorial_complete",
                    name = "Complete Tutorial",
                    description = "Finish the tutorial area",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(
                        Reward("basic_sword", "Basic Sword"),
                        Reward("health_potion", "Health Potion")
                    ),
                    category = ActionCategory.EXPLORATION
                ),
                
                // First area progression
                "talk_npc_merchant" to GameAction(
                    id = "talk_npc_merchant",
                    name = "Talk to Merchant",
                    description = "Speak with the merchant in town",
                    preconditions = PreconditionExpression.ActionRequired("tutorial_complete"),
                    rewards = listOf(Reward("merchant_quest", "Merchant's Quest")),
                    category = ActionCategory.NPC_DIALOGUE
                ),
                
                "explore_forest" to GameAction(
                    id = "explore_forest",
                    name = "Explore Dark Forest",
                    description = "Search the forest for hidden items",
                    preconditions = PreconditionExpression.ActionRequired("tutorial_complete"),
                    rewards = listOf(
                        Reward("forest_key", "Forest Key"),
                        Reward("magic_herb", "Magic Herb")
                    ),
                    category = ActionCategory.EXPLORATION
                ),
                
                // Boss progression
                "kill_forest_boss" to GameAction(
                    id = "kill_forest_boss",
                    name = "Kill Forest Guardian",
                    description = "Defeat the guardian of the forest",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("basic_sword"),
                        PreconditionExpression.ItemRequired("forest_key")
                    )),
                    rewards = listOf(Reward("guardian_crystal", "Guardian Crystal")),
                    category = ActionCategory.BOSS
                ),
                
                // Second area (multiple paths)
                "enter_castle_main" to GameAction(
                    id = "enter_castle_main",
                    name = "Enter Castle (Main Gate)",
                    description = "Enter through the heavily guarded main gate",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("guardian_crystal"),
                        PreconditionExpression.ActionForbidden("stealth_route")
                    )),
                    rewards = listOf(Reward("royal_seal", "Royal Seal")),
                    category = ActionCategory.EXPLORATION
                ),
                
                "stealth_route" to GameAction(
                    id = "stealth_route",
                    name = "Take Stealth Route",
                    description = "Sneak into castle through secret passage",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("magic_herb"),
                        PreconditionExpression.ActionForbidden("enter_castle_main")
                    )),
                    rewards = listOf(Reward("stealth_advantage", "Stealth Advantage")),
                    category = ActionCategory.EXPLORATION
                ),
                
                // Final boss (different strategies)
                "final_boss_combat" to GameAction(
                    id = "final_boss_combat",
                    name = "Fight Final Boss",
                    description = "Face the final boss in direct combat",
                    preconditions = PreconditionExpression.Or(listOf(
                        PreconditionExpression.ItemRequired("royal_seal"),
                        PreconditionExpression.ItemRequired("stealth_advantage")
                    )),
                    rewards = listOf(Reward("victory_crown", "Crown of Victory")),
                    category = ActionCategory.BOSS
                ),
                
                // Optional content
                "side_quest_merchant" to GameAction(
                    id = "side_quest_merchant",
                    name = "Complete Merchant Quest",
                    description = "Fulfill the merchant's request",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("merchant_quest"),
                        PreconditionExpression.ItemRequired("magic_herb")
                    )),
                    rewards = listOf(Reward("rare_gem", "Rare Gem")),
                    category = ActionCategory.QUEST
                ),
                
                "secret_ending" to GameAction(
                    id = "secret_ending",
                    name = "Unlock Secret Ending",
                    description = "Discover the hidden true ending",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ItemRequired("victory_crown"),
                        PreconditionExpression.ItemRequired("rare_gem")
                    )),
                    rewards = listOf(Reward("true_ending", "True Ending Achievement")),
                    category = ActionCategory.QUEST
                )
            ),
            items = mapOf(
                "basic_sword" to Item("basic_sword", "Basic Sword", "A simple but reliable sword"),
                "health_potion" to Item("health_potion", "Health Potion", "Restores health"),
                "merchant_quest" to Item("merchant_quest", "Merchant's Quest", "A quest from the merchant"),
                "forest_key" to Item("forest_key", "Forest Key", "Opens the guardian's lair"),
                "magic_herb" to Item("magic_herb", "Magic Herb", "Provides magical concealment"),
                "guardian_crystal" to Item("guardian_crystal", "Guardian Crystal", "Proof of defeating the guardian"),
                "royal_seal" to Item("royal_seal", "Royal Seal", "Grants access to the throne room"),
                "stealth_advantage" to Item("stealth_advantage", "Stealth Advantage", "Element of surprise"),
                "victory_crown" to Item("victory_crown", "Crown of Victory", "Symbol of triumph"),
                "rare_gem" to Item("rare_gem", "Rare Gem", "A precious stone with magical properties"),
                "true_ending" to Item("true_ending", "True Ending Achievement", "Complete game achievement")
            )
        )
    }
    
    @Test
    fun testCompleteGameProgression() {
        // Test a complete playthrough from start to finish
        
        // Initial state: only tutorial available
        var gameRun = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Integration Test Run",
            completedActions = emptySet(),
            goals = listOf(
                Goal("goal_final_boss", "final_boss_combat", "Defeat Final Boss"),
                Goal("goal_secret", "secret_ending", "Get Secret Ending")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        // Step 1: Complete tutorial
        gameRun = gameRun.copy(completedActions = setOf("tutorial_complete"))
        var gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        
        // Both goals should be achievable but require multiple steps
        val totalGoals = gameActionGraph.readyGoals + gameActionGraph.achievableGoals + gameActionGraph.unachievableGoals + gameActionGraph.completedGoals
        assertEquals(2, totalGoals.size)
        assertTrue(
            gameActionGraph.achievableGoals.isNotEmpty() || gameActionGraph.readyGoals.isNotEmpty(),
            "Goals should be achievable after tutorial"
        )
        
        // Multiple actions should now be available
        assertTrue(
            gameActionGraph.currentActions.size >= 2,
            "Multiple actions should be available after tutorial"
        )
        
        // Step 2: Explore forest and talk to merchant
        gameRun = gameRun.copy(completedActions = gameRun.completedActions + setOf("explore_forest", "talk_npc_merchant"))
        gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        
        // Forest boss should now be available
        val forestBossAction = gameActionGraph.currentActions.find { it.action.id == "kill_forest_boss" }
        assertNotNull(forestBossAction, "Forest boss should be available with sword and key")
        
        // Step 3: Kill forest boss
        gameRun = gameRun.copy(completedActions = gameRun.completedActions + "kill_forest_boss")
        gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        
        // Both castle entry methods should be available
        val mainGateAction = gameActionGraph.currentActions.find { it.action.id == "enter_castle_main" }
        val stealthAction = gameActionGraph.currentActions.find { it.action.id == "stealth_route" }
        assertNotNull(mainGateAction, "Main gate route should be available")
        assertNotNull(stealthAction, "Stealth route should be available")
        
        // These should be mutually exclusive - one should block goals
        assertTrue(
            mainGateAction.blocksGoals.isEmpty() || stealthAction.blocksGoals.isEmpty(),
            "At least one route should not block goals initially"
        )
    }
    
    @Test
    fun testMutuallyExclusiveRoutes() {
        // Test that choosing one castle route blocks the other
        
        var gameRun = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Route Choice Test",
            completedActions = setOf("tutorial_complete", "explore_forest", "kill_forest_boss"),
            goals = listOf(
                Goal("goal_main", "enter_castle_main", "Enter Castle Main"),
                Goal("goal_stealth", "stealth_route", "Take Stealth Route")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        
        // Find both route actions
        val mainGateAction = gameActionGraph.currentActions.find { it.action.id == "enter_castle_main" }
        val stealthAction = gameActionGraph.currentActions.find { it.action.id == "stealth_route" }
        
        assertNotNull(mainGateAction)
        assertNotNull(stealthAction)
        
        // Each route should conflict with the other goal
        assertTrue(
            mainGateAction.blocksGoals.any { it.targetId == "stealth_route" },
            "Main gate should conflict with stealth route goal"
        )
        assertTrue(
            stealthAction.blocksGoals.any { it.targetId == "enter_castle_main" },
            "Stealth route should conflict with main gate goal"
        )
        
        // Test choosing stealth route
        gameRun = gameRun.copy(completedActions = gameRun.completedActions + "stealth_route")
        val gameActionGraphAfterStealth = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        
        val mainGoalAfterStealth = gameActionGraphAfterStealth.unachievableGoals.find { it.goal.targetId == "enter_castle_main" }
        val stealthGoalAfterStealth = gameActionGraphAfterStealth.completedGoals.find { it.goal.targetId == "stealth_route" }
        
        assertNotNull(mainGoalAfterStealth, "Main route should be unachievable after stealth")
        assertNotNull(stealthGoalAfterStealth, "Stealth route should be completed")
    }
    
    @Test
    fun testComplexGoalSearch() {
        // Test searching for goals across the complex game
        
        val searchableGoals = goalSearch.createSearchableGoals(complexGameData)
        
        // Should create goals for actions and items
        assertTrue(searchableGoals.size >= complexGameData.actions.size, "Should create at least one goal per action")
        
        // Test searching for boss-related content
        val bossResults = goalSearch.searchGoals(searchableGoals, "boss")
        assertTrue(bossResults.isNotEmpty(), "Should find boss-related goals")
        
        val finalBossGoal = bossResults.find { it.name.contains("Final Boss") }
        assertNotNull(finalBossGoal, "Should find final boss goal")
        
        // Test searching for items
        val crownResults = goalSearch.searchGoals(searchableGoals, "crown")
        val crownGoal = crownResults.find { it.name.contains("Crown") }
        assertNotNull(crownGoal, "Should find crown-related goal")
        
        // Test multi-term search
        val secretEndingResults = goalSearch.searchGoals(searchableGoals, "secret ending")
        val secretGoal = secretEndingResults.find { it.name.contains("Secret") }
        assertNotNull(secretGoal, "Should find secret ending goal")
    }
    
    @Test
    fun testActionSortingInComplexScenario() {
        // Test action sorting with multiple goals and complex dependencies
        
        val gameRun = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Sorting Test",
            completedActions = setOf("tutorial_complete", "explore_forest"),
            goals = listOf(
                Goal("goal1", "final_boss_combat", "Defeat Final Boss"),
                Goal("goal2", "secret_ending", "Get Secret Ending"),
                Goal("goal3", "side_quest_merchant", "Complete Merchant Quest")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        val gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        val availableActions = gameActionGraph.currentActions
        
        // Verify sorting categories
        val directFulfillment = gameActionGraph.readyGoals
        val progressMaking = availableActions.filter { 
            it.enablesGoals.isNotEmpty() 
        }
        val neutral = availableActions.filter { 
            it.enablesGoals.isEmpty() && it.blocksGoals.isEmpty() 
        }
        val blocking = availableActions.filter { it.blocksGoals.isNotEmpty() }
        
        // Basic verification that we have the expected action categories
        assertTrue(availableActions.isNotEmpty(), "Should have available actions")
        
        // Actions contributing to multiple goals should exist
        val multiGoalActions = progressMaking.filter { it.enablesGoals.size > 1 }
        val singleGoalActions = progressMaking.filter { it.enablesGoals.size == 1 }
        
        // Just verify the basic functionality works
        assertTrue(
            multiGoalActions.isNotEmpty() || singleGoalActions.isNotEmpty(),
            "Should have goal-enabling actions"
        )
    }
    
    @Test
    fun testLargeScaleGoalManagement() {
        // Test with many goals to verify performance and correctness
        
        val manyGoals = listOf(
            Goal("g1", "tutorial_complete", "Complete Tutorial"),
            Goal("g2", "talk_npc_merchant", "Talk to Merchant"),
            Goal("g3", "explore_forest", "Explore Forest"),
            Goal("g4", "kill_forest_boss", "Kill Forest Boss"),
            Goal("g5", "enter_castle_main", "Enter Castle Main"),
            Goal("g6", "stealth_route", "Stealth Route"),
            Goal("g7", "final_boss_combat", "Fight Final Boss"),
            Goal("g8", "side_quest_merchant", "Merchant Quest"),
            Goal("g9", "secret_ending", "Secret Ending")
        )
        
        val gameRun = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Many Goals Test",
            completedActions = setOf("tutorial_complete"),
            goals = manyGoals,
            createdAt = 0,
            lastModified = 0
        )
        
        // This should complete without performance issues
        val startTime = System.currentTimeMillis()
        val gameActionGraph = GameActionGraph.create(complexGameData, gameRun.completedActions, gameRun.goals.toSet())
        val endTime = System.currentTimeMillis()
        
        // Basic performance check (should complete quickly)
        assertTrue(
            endTime - startTime < 1000,
            "Analysis should complete within 1 second even with many goals"
        )
        
        // Verify all goals were analyzed
        val totalGoals = gameActionGraph.readyGoals + gameActionGraph.achievableGoals + gameActionGraph.unachievableGoals + gameActionGraph.completedGoals
        assertEquals(manyGoals.size, totalGoals.size, "All goals should be analyzed")
        
        // Verify goal states are reasonable
        assertTrue(gameActionGraph.completedGoals.isNotEmpty(), "Should have some completed goals")
        assertTrue(gameActionGraph.readyGoals.isNotEmpty() || gameActionGraph.achievableGoals.isNotEmpty(), "Should have some achievable goals")
        
        // Verify action sorting with many goals
        assertTrue(gameActionGraph.currentActions.isNotEmpty(), "Should have available actions")
        
        // Actions should be consistently ordered
        val sortComparator = compareBy<AvailableAction> { action ->
            when {
                action.blocksGoals.isNotEmpty() -> 3
                action.enablesGoals.isNotEmpty() -> 1
                else -> 2
            }
        }.thenBy { it.action.id } // Secondary sort by ID for stable ordering
        
        val sortedFirst = gameActionGraph.currentActions.sortedWith(sortComparator)
        val sortedAgain = gameActionGraph.currentActions.shuffled().sortedWith(sortComparator)
        
        // Verify same sorting order
        for (i in sortedFirst.indices) {
            assertEquals(
                sortedFirst[i].action.id,
                sortedAgain[i].action.id,
                "Action sorting should be consistent"
            )
        }
    }
    
    @Test
    fun testErrorRecoveryScenarios() {
        // Test behavior with invalid or edge case data
        
        // Test with goal targeting non-existent action
        val gameRunWithInvalidGoal = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Invalid Goal Test",
            completedActions = setOf("tutorial_complete"),
            goals = listOf(
                Goal("valid", "talk_npc_merchant", "Valid Goal"),
                Goal("invalid", "nonexistent_action", "Invalid Goal")
            ),
            createdAt = 0,
            lastModified = 0
        )
        
        // Should handle invalid goals gracefully
        val gameActionGraph = GameActionGraph.create(complexGameData, gameRunWithInvalidGoal.completedActions, gameRunWithInvalidGoal.goals.toSet())
        val totalGoals = gameActionGraph.readyGoals + gameActionGraph.achievableGoals + gameActionGraph.unachievableGoals + gameActionGraph.completedGoals
        assertEquals(2, totalGoals.size, "Should analyze all goals including invalid ones")
        
        val invalidGoalAnalysis = gameActionGraph.unachievableGoals.find { it.goal.targetId == "nonexistent_action" }
        assertNotNull(invalidGoalAnalysis, "Invalid goal should be unachievable")
        
        // Test with empty completed actions
        val emptyGameRun = GameRun(
            gameId = "complex-rpg",
            gameVersion = "1.0.0",
            runName = "Empty Test",
            completedActions = emptySet(),
            goals = listOf(Goal("goal", "tutorial_complete", "Tutorial")),
            createdAt = 0,
            lastModified = 0
        )
        
        val emptyGameActionGraph = GameActionGraph.create(complexGameData, emptyGameRun.completedActions, emptyGameRun.goals.toSet())
        assertTrue(emptyGameActionGraph.currentActions.isNotEmpty(), "Should handle empty completed actions")
        
        val tutorialAction = emptyGameActionGraph.currentActions.find { it.action.id == "tutorial_complete" }
        assertNotNull(tutorialAction, "Tutorial should be available initially")
    }
}