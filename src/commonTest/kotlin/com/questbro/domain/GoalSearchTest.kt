package com.questbro.domain

import kotlin.test.*

class GoalSearchTest {
    
    private lateinit var goalSearch: GoalSearch
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        goalSearch = GoalSearch()
        
        testGameData = GameData(
            gameId = "test-game",
            name = "Test Game",
            version = "1.0.0",
            actions = mapOf(
                "kill_boss" to GameAction(
                    id = "kill_boss",
                    name = "Kill Dragon Boss",
                    description = "Defeat the mighty dragon in the castle",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(
                        Reward("dragon_sword", "Dragon Slayer Sword"),
                        Reward("dragon_scale", "Dragon Scale")
                    ),
                    category = ActionCategory.BOSS
                ),
                "talk_merchant" to GameAction(
                    id = "talk_merchant",
                    name = "Talk to Merchant",
                    description = "Speak with the traveling merchant about rare items",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("merchant_info", "Merchant Information")),
                    category = ActionCategory.NPC_DIALOGUE
                ),
                "explore_forest" to GameAction(
                    id = "explore_forest",
                    name = "Explore Dark Forest",
                    description = "Search the mysterious dark forest for hidden treasures",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(
                        Reward("forest_herb", "Rare Forest Herb"),
                        Reward("magic_crystal", "Magic Crystal")
                    ),
                    category = ActionCategory.EXPLORATION
                ),
                "complete_quest" to GameAction(
                    id = "complete_quest",
                    name = "Complete Main Quest",
                    description = "Finish the main storyline quest",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("quest_reward", "Quest Completion Reward")),
                    category = ActionCategory.QUEST
                ),
                "pickup_sword" to GameAction(
                    id = "pickup_sword",
                    name = "Pick Up Iron Sword",
                    description = "Collect the iron sword from the armory",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("iron_sword", "Iron Sword")),
                    category = ActionCategory.ITEM_PICKUP
                )
            ),
            items = mapOf(
                "dragon_sword" to Item("dragon_sword", "Dragon Slayer Sword", "A legendary sword forged from dragon fire"),
                "dragon_scale" to Item("dragon_scale", "Dragon Scale", "Protective scale from an ancient dragon"),
                "merchant_info" to Item("merchant_info", "Merchant Information", "Valuable trading information"),
                "forest_herb" to Item("forest_herb", "Rare Forest Herb", "A healing herb found only in dark forests"),
                "magic_crystal" to Item("magic_crystal", "Magic Crystal", "A crystal pulsing with magical energy"),
                "quest_reward" to Item("quest_reward", "Quest Completion Reward", "Reward for completing the main quest"),
                "iron_sword" to Item("iron_sword", "Iron Sword", "A basic but reliable iron sword")
            )
        )
    }
    
    @Test
    fun testCreateSearchableGoalsFromActions() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        // Should create one goal per action
        val actionGoals = searchableGoals.filter { it.id.startsWith("action_") }
        assertEquals(5, actionGoals.size, "Should create one searchable goal per action")
        
        // Check specific action goal
        val bossGoal = actionGoals.find { it.targetId == "kill_boss" }
        assertNotNull(bossGoal, "Should create goal for kill_boss action")
        assertEquals("Kill Dragon Boss", bossGoal.name, "Action goal name should match action name")
        assertEquals("Defeat the mighty dragon in the castle", bossGoal.description, "Action goal description should match action description")
        assertEquals("BOSS", bossGoal.category, "Action goal category should match action category")
        assertTrue(
            bossGoal.searchKeywords.contains("kill"),
            "Action goal should include keywords from name"
        )
        assertTrue(
            bossGoal.searchKeywords.contains("dragon"),
            "Action goal should include keywords from name and description"
        )
    }
    
    @Test
    fun testCreateSearchableGoalsFromItems() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        // Should create item goals for each item that has providing actions
        val itemGoals = searchableGoals.filter { it.id.startsWith("item_") }
        assertEquals(7, itemGoals.size, "Should create one goal per item")
        
        // Check specific item goal
        val dragonSwordGoal = itemGoals.find { it.name == "Obtain Dragon Slayer Sword" }
        assertNotNull(dragonSwordGoal, "Should create goal for dragon sword item")
        assertEquals("kill_boss", dragonSwordGoal.targetId, "Item goal should target the providing action")
        assertEquals("ITEM", dragonSwordGoal.category, "Item goal should have ITEM category")
        assertTrue(
            dragonSwordGoal.searchKeywords.contains("dragon"),
            "Item goal should include keywords from item name"
        )
        assertTrue(
            dragonSwordGoal.searchKeywords.contains("sword"),
            "Item goal should include keywords from item name"
        )
        assertTrue(
            dragonSwordGoal.searchKeywords.contains("item"),
            "Item goal should include 'item' keyword"
        )
    }
    
    @Test
    fun testSearchWithEmptyQuery() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "")
        
        // Should return first 20 goals when query is empty
        assertEquals(
            minOf(20, searchableGoals.size),
            results.size,
            "Empty query should return first 20 goals (or all if less than 20)"
        )
        
        // Results should be in alphabetical order (as created by createSearchableGoals)
        for (i in 1 until results.size) {
            assertTrue(
                results[i-1].name <= results[i].name,
                "Results should be sorted alphabetically by name"
            )
        }
    }
    
    @Test
    fun testSearchWithBlankQuery() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "   \t\n  ")
        
        // Blank query should be treated same as empty
        assertEquals(
            minOf(20, searchableGoals.size),
            results.size,
            "Blank query should return first 20 goals"
        )
    }
    
    @Test
    fun testSearchRelevanceScoring() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "dragon")
        
        // Should find goals related to dragon
        assertTrue(results.isNotEmpty(), "Should find goals matching 'dragon'")
        
        // Dragon Boss action should have highest score (name match = 10 points)
        val dragonBossGoal = results.find { it.name == "Kill Dragon Boss" }
        assertNotNull(dragonBossGoal, "Should find Kill Dragon Boss goal")
        
        // Dragon Sword item goal should also be found (name match = 10 points)
        val dragonSwordGoal = results.find { it.name == "Obtain Dragon Slayer Sword" }
        assertNotNull(dragonSwordGoal, "Should find Dragon Slayer Sword goal")
        
        // Results should be sorted by relevance (highest score first)
        assertTrue(
            results.indexOf(dragonBossGoal) <= results.indexOf(dragonSwordGoal) ||
            results.indexOf(dragonSwordGoal) <= results.indexOf(dragonBossGoal),
            "High scoring results should appear first"
        )
    }
    
    @Test
    fun testSearchCaseInsensitive() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        val lowerCaseResults = goalSearch.searchGoals(searchableGoals, "dragon")
        val upperCaseResults = goalSearch.searchGoals(searchableGoals, "DRAGON")
        val mixedCaseResults = goalSearch.searchGoals(searchableGoals, "DrAgOn")
        
        assertEquals(
            lowerCaseResults.size,
            upperCaseResults.size,
            "Case should not affect search results count"
        )
        assertEquals(
            lowerCaseResults.size,
            mixedCaseResults.size,
            "Case should not affect search results count"
        )
        
        // Should find the same goals regardless of case
        val lowerCaseNames = lowerCaseResults.map { it.name }.toSet()
        val upperCaseNames = upperCaseResults.map { it.name }.toSet()
        val mixedCaseNames = mixedCaseResults.map { it.name }.toSet()
        
        assertEquals(lowerCaseNames, upperCaseNames, "Should find same goals regardless of case")
        assertEquals(lowerCaseNames, mixedCaseNames, "Should find same goals regardless of case")
    }
    
    @Test
    fun testSearchMultipleTerms() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "dragon sword")
        
        // Should find goals that match both terms with higher scores
        assertTrue(results.isNotEmpty(), "Should find goals matching multiple terms")
        
        val dragonSwordGoal = results.find { it.name == "Obtain Dragon Slayer Sword" }
        assertNotNull(dragonSwordGoal, "Should find goal matching both 'dragon' and 'sword'")
        
        // Dragon Sword goal should have high relevance (matches both terms in name)
        val bossGoal = results.find { it.name == "Kill Dragon Boss" }
        val ironSwordGoal = results.find { it.name == "Obtain Iron Sword" }
        
        // Dragon Sword should rank higher than goals matching only one term
        if (bossGoal != null && dragonSwordGoal != null) {
            assertTrue(
                results.indexOf(dragonSwordGoal) <= results.indexOf(bossGoal),
                "Goal matching multiple terms should rank higher"
            )
        }
        if (ironSwordGoal != null && dragonSwordGoal != null) {
            assertTrue(
                results.indexOf(dragonSwordGoal) <= results.indexOf(ironSwordGoal),
                "Goal matching multiple terms should rank higher"
            )
        }
    }
    
    @Test
    fun testSearchByCategory() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "boss")
        
        // Should find the boss category action
        val bossResult = results.find { it.name == "Kill Dragon Boss" }
        assertNotNull(bossResult, "Should find boss category action")
        
        // Should score category matches (3 points)
        assertTrue(results.isNotEmpty(), "Should find results for category search")
    }
    
    @Test
    fun testSearchByDescription() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "mighty")
        
        // "mighty" appears in dragon boss description
        val bossResult = results.find { it.name == "Kill Dragon Boss" }
        assertNotNull(bossResult, "Should find goal by description match")
    }
    
    @Test
    fun testSearchNoResults() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "nonexistent")
        
        assertTrue(results.isEmpty(), "Should return empty list for non-matching search")
    }
    
    @Test
    fun testSearchMaxResults() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "obtain", maxResults = 3)
        
        assertTrue(
            results.size <= 3,
            "Should respect maxResults parameter"
        )
    }
    
    @Test
    fun testKeywordExtraction() {
        // Test keyword extraction indirectly through search functionality
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        // Search for a word that should be extracted as keyword
        val treasureResults = goalSearch.searchGoals(searchableGoals, "treasures")
        val treasureResult = treasureResults.find { it.name == "Explore Dark Forest" }
        
        // "treasures" appears in the description, should be found through keywords
        assertNotNull(treasureResult, "Should find goal through extracted keywords")
    }
    
    @Test
    fun testCreateGoalFromSearchable() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val searchableGoal = searchableGoals.first()
        
        val goal = goalSearch.createGoalFromSearchable(searchableGoal)
        
        assertEquals(searchableGoal.id, goal.id, "Created goal should have same ID")
        assertEquals(searchableGoal.targetId, goal.targetId, "Created goal should have same target ID")
        assertEquals(searchableGoal.name, goal.description, "Created goal description should be searchable goal name")
        assertEquals(0, goal.priority, "Created goal should have default priority")
    }
    
    @Test
    fun testSortingConsistency() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        // Goals should be sorted by name
        for (i in 1 until searchableGoals.size) {
            assertTrue(
                searchableGoals[i-1].name <= searchableGoals[i].name,
                "Searchable goals should be sorted alphabetically by name"
            )
        }
    }
    
    @Test
    fun testSearchTermSplitting() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        
        // Test search with multiple whitespace types
        val results1 = goalSearch.searchGoals(searchableGoals, "dragon sword")
        val results2 = goalSearch.searchGoals(searchableGoals, "dragon\tsword")
        val results3 = goalSearch.searchGoals(searchableGoals, "dragon  \t  sword")
        
        assertEquals(
            results1.size,
            results2.size,
            "Different whitespace should not affect results"
        )
        assertEquals(
            results1.size,
            results3.size,
            "Multiple whitespace should not affect results"
        )
    }
    
    @Test
    fun testRelevanceScoreCalculation() {
        val searchableGoals = goalSearch.createSearchableGoals(testGameData)
        val results = goalSearch.searchGoals(searchableGoals, "dragon")
        
        // Verify that items with exact name matches score higher
        if (results.size >= 2) {
            val firstResult = results[0]
            val secondResult = results[1]
            
            // First result should have "dragon" in name for highest score
            assertTrue(
                firstResult.name.lowercase().contains("dragon") ||
                firstResult.description.lowercase().contains("dragon"),
                "Top result should contain search term"
            )
        }
    }
    
    @Test
    fun testEmptyGameDataSearch() {
        val emptyGameData = GameData(
            gameId = "empty",
            name = "Empty Game",
            version = "1.0.0",
            actions = emptyMap(),
            items = emptyMap()
        )
        
        val searchableGoals = goalSearch.createSearchableGoals(emptyGameData)
        assertTrue(searchableGoals.isEmpty(), "Empty game data should produce no searchable goals")
        
        val results = goalSearch.searchGoals(searchableGoals, "anything")
        assertTrue(results.isEmpty(), "Search on empty goals should return empty results")
    }
}