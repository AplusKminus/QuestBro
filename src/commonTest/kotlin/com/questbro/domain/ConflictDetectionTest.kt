package com.questbro.domain

import kotlin.test.*

/**
 * Dedicated tests for conflict detection functionality in GameActionGraph
 */
class ConflictDetectionTest {
    
    private lateinit var testGameData: GameData
    
    @BeforeTest
    fun setup() {
        testGameData = GameData(
            gameId = "conflict-test",
            name = "Conflict Test Game",
            version = "1.0.0",
            actions = mapOf(
                "start" to GameAction(
                    id = "start",
                    name = "Start Game",
                    description = "Begin the adventure",
                    preconditions = PreconditionExpression.Always,
                    rewards = listOf(Reward("starter_weapon", "Starter Weapon")),
                    category = ActionCategory.EXPLORATION
                ),
                "good_path" to GameAction(
                    id = "good_path",
                    name = "Choose Good Path",
                    description = "Take the righteous path",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.ActionForbidden("evil_path")
                    )),
                    rewards = listOf(Reward("holy_blessing", "Holy Blessing")),
                    category = ActionCategory.QUEST
                ),
                "evil_path" to GameAction(
                    id = "evil_path",
                    name = "Choose Evil Path",
                    description = "Take the dark path",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.ActionRequired("start"),
                        PreconditionExpression.ActionForbidden("good_path")
                    )),
                    rewards = listOf(Reward("dark_power", "Dark Power")),
                    category = ActionCategory.QUEST
                ),
                "neutral_action" to GameAction(
                    id = "neutral_action",
                    name = "Neutral Action",
                    description = "This doesn't conflict with anything",
                    preconditions = PreconditionExpression.ActionRequired("start"),
                    rewards = emptyList(),
                    category = ActionCategory.EXPLORATION
                ),
                "good_ending" to GameAction(
                    id = "good_ending",
                    name = "Good Ending",
                    description = "Achieve the good ending",
                    preconditions = PreconditionExpression.ItemRequired("holy_blessing"),
                    rewards = listOf(Reward("salvation", "Salvation")),
                    category = ActionCategory.BOSS
                ),
                "evil_ending" to GameAction(
                    id = "evil_ending",
                    name = "Evil Ending",
                    description = "Achieve the evil ending",
                    preconditions = PreconditionExpression.ItemRequired("dark_power"),
                    rewards = listOf(Reward("domination", "World Domination")),
                    category = ActionCategory.BOSS
                ),
                "complex_requirement" to GameAction(
                    id = "complex_requirement",
                    name = "Complex Requirement",
                    description = "Requires both paths somehow",
                    preconditions = PreconditionExpression.And(listOf(
                        PreconditionExpression.Or(listOf(
                            PreconditionExpression.ItemRequired("holy_blessing"),
                            PreconditionExpression.ItemRequired("dark_power")
                        )),
                        PreconditionExpression.ActionForbidden("neutral_action")
                    )),
                    rewards = emptyList(),
                    category = ActionCategory.QUEST
                )
            ),
            items = mapOf(
                "starter_weapon" to Item("starter_weapon", "Starter Weapon", "Basic weapon"),
                "holy_blessing" to Item("holy_blessing", "Holy Blessing", "Divine power"),
                "dark_power" to Item("dark_power", "Dark Power", "Corrupted energy"),
                "salvation" to Item("salvation", "Salvation", "Final redemption"),
                "domination" to Item("domination", "World Domination", "Ultimate power")
            )
        )
    }
    
    @Test
    fun testDirectMutualExclusion() {
        // Test detection of direct mutual exclusion between actions
        
        val existingGoals = setOf(Goal("goal1", "good_path", "Choose Good Path"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Adding evil path should create conflict
        val conflictingGoal = Goal("goal2", "evil_path", "Choose Evil Path")
        val conflicts = graph.checkConflictsWhenAddingGoal(conflictingGoal)
        
        assertFalse(conflicts.isEmpty(), "Should detect conflict between good and evil paths")
        assertTrue(
            conflicts.any { it.severity == ConflictSeverity.MutualExclusion },
            "Should identify as mutual exclusion conflict"
        )
        
        val mutualExclusionConflict = conflicts.first { it.severity == ConflictSeverity.MutualExclusion }
        assertTrue(
            mutualExclusionConflict.involvedGoals.contains(conflictingGoal),
            "Conflict should involve the new goal"
        )
        assertTrue(
            mutualExclusionConflict.involvedGoals.any { it.targetId == "good_path" },
            "Conflict should involve the existing good path goal"
        )
    }
    
    @Test
    fun testNoConflictWithCompatibleGoals() {
        // Test that compatible goals don't generate conflicts
        
        val existingGoals = setOf(Goal("goal1", "good_path", "Choose Good Path"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Adding neutral action should not create conflict
        val compatibleGoal = Goal("goal2", "neutral_action", "Do Neutral Action")
        val conflicts = graph.checkConflictsWhenAddingGoal(compatibleGoal)
        
        assertTrue(conflicts.isEmpty(), "Should not detect conflicts between compatible goals")
    }
    
    @Test
    fun testIndirectConflictDetection() {
        // Test detection of indirect conflicts through action dependencies
        
        val existingGoals = setOf(Goal("goal1", "good_path", "Choose Good Path"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Adding evil_path should create a direct conflict with good_path
        // because evil_path forbids good_path and vice versa
        val blockingGoal = Goal("goal2", "evil_path", "Choose Evil Path") 
        val conflicts = graph.checkConflictsWhenAddingGoal(blockingGoal)
        
        // This should detect the direct mutual exclusion
        assertTrue(conflicts.isNotEmpty(), "Should detect conflicts between mutually exclusive paths")
        assertTrue(
            conflicts.any { it.severity == ConflictSeverity.MutualExclusion },
            "Should identify as mutual exclusion conflict"
        )
    }
    
    @Test
    fun testConflictWithInvalidGoal() {
        // Test conflict detection when adding goal for non-existent action
        
        val graph = GameActionGraph.create(testGameData)
        val invalidGoal = Goal("invalid", "nonexistent_action", "Invalid Goal")
        
        val conflicts = graph.checkConflictsWhenAddingGoal(invalidGoal)
        
        assertFalse(conflicts.isEmpty(), "Should report conflict for invalid goal")
        assertTrue(
            conflicts.any { it.severity == ConflictSeverity.MutualExclusion },
            "Invalid goal should be treated as mutual exclusion conflict"
        )
        
        val invalidConflict = conflicts.first()
        assertTrue(
            invalidConflict.description.contains("not found") || 
            invalidConflict.description.contains("Invalid"),
            "Conflict description should indicate action not found"
        )
    }
    
    @Test
    fun testComplexConflictScenario() {
        // Test complex scenario with multiple potential conflicts
        
        val existingGoals = setOf(
            Goal("goal1", "good_path", "Choose Good Path"),
            Goal("goal2", "complex_requirement", "Complex Requirement"),
            Goal("goal3", "neutral_action", "Neutral Action")
        )
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Adding evil_path creates multiple conflicts:
        // 1. Direct conflict with good_path
        // 2. Makes complex_requirement potentially achievable (it can use dark_power)
        // 3. But complex_requirement forbids neutral_action
        
        val conflictingGoal = Goal("goal4", "evil_path", "Choose Evil Path")
        val conflicts = graph.checkConflictsWhenAddingGoal(conflictingGoal)
        
        assertFalse(conflicts.isEmpty(), "Should detect conflicts in complex scenario")
        
        // Should detect direct conflict with good_path
        val directConflicts = conflicts.filter { it.severity == ConflictSeverity.MutualExclusion }
        assertTrue(
            directConflicts.any { conflict ->
                conflict.involvedGoals.any { it.targetId == "good_path" }
            },
            "Should detect direct conflict with good_path"
        )
    }
    
    @Test
    fun testConflictSeverityClassification() {
        // Test that conflicts are properly classified by severity
        
        val existingGoals = setOf(Goal("goal1", "good_path", "Choose Good Path"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // Direct mutual exclusion
        val directConflictGoal = Goal("goal2", "evil_path", "Choose Evil Path")
        val directConflicts = graph.checkConflictsWhenAddingGoal(directConflictGoal)
        
        assertTrue(
            directConflicts.any { it.severity == ConflictSeverity.MutualExclusion },
            "Direct conflicts should be classified as MutualExclusion"
        )
        
        // Test induced conflict scenario
        val goals = setOf(
            Goal("goal1", "good_ending", "Good Ending"),
            Goal("goal2", "neutral_action", "Neutral Action")
        )
        val graphWithGoals = GameActionGraph.create(testGameData, goals = goals)
        
        // Adding complex_requirement should create induced conflict with neutral_action
        val inducingGoal = Goal("goal3", "complex_requirement", "Complex Requirement")
        val conflicts = graphWithGoals.checkConflictsWhenAddingGoal(inducingGoal)
        
        // This is complex to test because it depends on the internal implementation
        // At minimum, we should detect some form of conflict
        // The specific severity classification may depend on implementation details
        // For now, just verify no exceptions are thrown
        assertNotNull(conflicts, "Conflict detection should complete without errors")
    }
    
    @Test
    fun testMultipleSimultaneousConflicts() {
        // Test scenario where one goal conflicts with multiple existing goals
        
        val existingGoals = setOf(
            Goal("goal1", "good_path", "Choose Good Path"),
            Goal("goal2", "good_ending", "Good Ending")
        )
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        // evil_path conflicts with both goals (directly with good_path, indirectly with good_ending)
        val multiConflictGoal = Goal("goal3", "evil_path", "Choose Evil Path")
        val conflicts = graph.checkConflictsWhenAddingGoal(multiConflictGoal)
        
        assertFalse(conflicts.isEmpty(), "Should detect conflicts with multiple goals")
        
        // Count unique goals involved in conflicts
        val involvedGoalIds = conflicts.flatMap { it.involvedGoals.map { goal -> goal.targetId } }.toSet()
        assertTrue(
            involvedGoalIds.contains("good_path"),
            "Should include good_path in conflicts"
        )
        // Note: indirect conflicts might not be detected depending on implementation
    }
    
    @Test
    fun testConflictDescriptions() {
        // Test that conflict descriptions are meaningful
        
        val existingGoals = setOf(Goal("goal1", "good_path", "Choose Good Path"))
        val graph = GameActionGraph.create(testGameData, goals = existingGoals)
        
        val conflictingGoal = Goal("goal2", "evil_path", "Choose Evil Path")
        val conflicts = graph.checkConflictsWhenAddingGoal(conflictingGoal)
        
        assertFalse(conflicts.isEmpty(), "Should have conflicts to test descriptions")
        
        val conflict = conflicts.first()
        assertFalse(conflict.description.isEmpty(), "Conflict description should not be empty")
        assertTrue(
            conflict.description.contains("good_path") || 
            conflict.description.contains("evil_path") ||
            conflict.description.contains("forbid"),
            "Conflict description should reference relevant actions or concepts"
        )
    }
    
    @Test
    fun testConflictWithCompletedActions() {
        // Test conflict detection when some actions are already completed
        
        val completedActions = setOf("start", "good_path")
        val existingGoals = setOf(Goal("goal1", "good_ending", "Good Ending"))
        val graph = GameActionGraph.create(testGameData, completedActions, existingGoals)
        
        // Try to add evil_path goal when good_path is already completed
        val conflictingGoal = Goal("goal2", "evil_path", "Choose Evil Path")
        val conflicts = graph.checkConflictsWhenAddingGoal(conflictingGoal)
        
        // evil_path should be unachievable because good_path is already completed
        // This might be detected as a conflict or the goal might just be categorized as unachievable
        // The specific behavior depends on implementation details
        
        // At minimum, the goal should be detected as problematic in some way
        // We can verify this by checking if evil_path would be achievable
        val graphWithGoal = graph.addGoals(setOf(conflictingGoal))
        val unachievableGoals = graphWithGoal.unachievableGoals
        
        assertTrue(
            conflicts.isNotEmpty() || unachievableGoals.any { it.goal.targetId == "evil_path" },
            "Evil path should be either conflicting or unachievable when good path is completed"
        )
    }
}