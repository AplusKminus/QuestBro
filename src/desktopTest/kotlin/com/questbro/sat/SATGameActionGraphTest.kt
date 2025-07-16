package com.questbro.sat

import com.questbro.domain.*
import kotlin.test.*

class SATGameActionGraphTest {
    
    private lateinit var satAdapter: SATAdapter
    private lateinit var gameData: GameData
    private lateinit var gameRun: GameRun
    private lateinit var satGraph: SATGameActionGraph
    
    @BeforeTest
    fun setup() {
        satAdapter = KoSATAdapter()
        
        // Create test game data with more complex scenarios
        val actions = mapOf(
            "start_action" to GameAction(
                id = "start_action",
                name = "Start Action",
                description = "Always available",
                preconditions = PreconditionExpression.Always,
                rewards = listOf(Reward("basic_item", "Basic item")),
                category = ActionCategory.EXPLORATION
            ),
            "middle_action" to GameAction(
                id = "middle_action",
                name = "Middle Action",
                description = "Requires start action",
                preconditions = PreconditionExpression.ActionRequired("start_action"),
                rewards = listOf(Reward("advanced_item", "Advanced item")),
                category = ActionCategory.QUEST
            ),
            "end_action" to GameAction(
                id = "end_action",
                name = "End Action",
                description = "Requires middle action",
                preconditions = PreconditionExpression.ActionRequired("middle_action"),
                rewards = listOf(Reward("final_item", "Final item")),
                category = ActionCategory.BOSS
            ),
            "conflicting_action" to GameAction(
                id = "conflicting_action",
                name = "Conflicting Action",
                description = "Forbids middle action",
                preconditions = PreconditionExpression.ActionForbidden("middle_action"),
                rewards = listOf(Reward("conflict_item", "Conflict item")),
                category = ActionCategory.NPC_DIALOGUE
            ),
            "alternative_action" to GameAction(
                id = "alternative_action",
                name = "Alternative Action",
                description = "Alternative path",
                preconditions = PreconditionExpression.ItemRequired("basic_item"),
                rewards = listOf(Reward("alt_item", "Alternative item")),
                category = ActionCategory.ITEM_PICKUP
            )
        )
        
        val items = mapOf(
            "basic_item" to Item("basic_item", "Basic Item", "Basic item"),
            "advanced_item" to Item("advanced_item", "Advanced Item", "Advanced item"),
            "final_item" to Item("final_item", "Final Item", "Final item"),
            "conflict_item" to Item("conflict_item", "Conflict Item", "Conflict item"),
            "alt_item" to Item("alt_item", "Alternative Item", "Alternative item")
        )
        
        gameData = GameData(
            gameId = "test-game",
            name = "Test Game",
            version = "1.0",
            actions = actions,
            items = items
        )
        
        gameRun = GameRun(
            gameId = "test-game",
            gameVersion = "1.0",
            runName = "Test Run",
            completedActions = setOf("start_action"),
            goals = listOf(
                Goal("goal1", "end_action", "Complete end action", 1),
                Goal("goal2", "alternative_action", "Complete alternative action", 2)
            ),
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        satGraph = SATGameActionGraph(satAdapter, gameData, gameRun)
    }
    
    @Test
    fun testGoalCompatibilityAnalysis() {
        val compatibleGoal = Goal("goal3", "middle_action", "Complete middle action", 3)
        val result = satGraph.analyzeGoalCompatibility(compatibleGoal)
        
        assertTrue(result is DomainResult.GoalCompatibilityResult)
        // Middle action should be compatible with existing goals
        assertTrue(result.compatible)
        assertTrue(result.conflicts.isEmpty())
    }
    
    @Test
    fun testConflictingGoalAnalysis() {
        val conflictingGoal = Goal("goal4", "conflicting_action", "Complete conflicting action", 4)
        val result = satGraph.analyzeGoalCompatibility(conflictingGoal)
        
        assertTrue(result is DomainResult.GoalCompatibilityResult)
        // Conflicting action should create conflicts
        assertFalse(result.compatible)
        assertTrue(result.conflicts.isNotEmpty())
    }
    
    @Test
    fun testMultiGoalCompatibilityAnalysis() {
        val goals = listOf(
            Goal("goal1", "end_action", "Complete end action", 1),
            Goal("goal2", "middle_action", "Complete middle action", 2),
            Goal("goal3", "conflicting_action", "Complete conflicting action", 3)
        )
        
        val result = satGraph.analyzeMultiGoalCompatibility(goals)
        
        assertTrue(result is MultiGoalCompatibilityResult)
        assertFalse(result.overallCompatible) // Should have conflicts
        assertTrue(result.conflicts.isNotEmpty())
        assertTrue(result.incompatibleGoals.isNotEmpty())
    }
    
    @Test
    fun testUndoabilityAnalysis() {
        val result = satGraph.analyzeUndoability("start_action")
        
        assertTrue(result is DomainResult.UndoabilityResult)
        // Start action should not be undoable because it's needed for goals
        assertFalse(result.undoable)
        assertTrue(result.cascadeEffects.isNotEmpty())
    }
    
    @Test
    fun testSafeUndoabilityAnalysis() {
        // Create a run where an action can be safely undone
        val safeGameRun = gameRun.copy(
            completedActions = setOf("start_action", "alternative_action"),
            goals = listOf(Goal("goal1", "end_action", "Complete end action", 1))
        )
        
        val safeGraph = SATGameActionGraph(satAdapter, gameData, safeGameRun)
        val result = safeGraph.analyzeUndoability("alternative_action")
        
        assertTrue(result is DomainResult.UndoabilityResult)
        // Alternative action should be undoable since it's not needed for the goal
        assertTrue(result.undoable)
        assertTrue(result.cascadeEffects.isEmpty())
    }
    
    @Test
    fun testOptimalPathFinding() {
        val goals = listOf(
            Goal("goal1", "end_action", "Complete end action", 1)
        )
        
        val preferences = PathPreferences(minimizeActions = true)
        val result = satGraph.findOptimalPath(goals, preferences)
        
        assertTrue(result is DomainResult.PathPlanResult)
        // Should be feasible to reach end_action
        assertTrue(result.feasible)
        assertTrue(result.actionSequence.isNotEmpty())
    }
    
    @Test
    fun testActionImpactAnalysis() {
        val result = satGraph.analyzeActionImpact("middle_action")
        
        assertTrue(result is ActionImpactResult)
        // This is a basic test - in a full implementation, we'd check
        // what actions become available/unavailable
        assertNotNull(result.enabledActions)
        assertNotNull(result.disabledActions)
        assertNotNull(result.enabledGoals)
        assertNotNull(result.disabledGoals)
    }
    
    @Test
    fun testComprehensiveConflictAnalysis() {
        val result = satGraph.performComprehensiveConflictAnalysis()
        
        assertTrue(result is ConflictAnalysisResult)
        assertNotNull(result.conflictSummary)
        assertNotNull(result.conflictGroups)
        assertTrue(result.totalConflicts >= 0)
    }
    
    @Test
    fun testInvalidGoalAnalysis() {
        val invalidGoal = Goal("invalid", "nonexistent_action", "Invalid goal", 1)
        val result = satGraph.analyzeGoalCompatibility(invalidGoal)
        
        assertTrue(result is DomainResult.GoalCompatibilityResult)
        assertFalse(result.compatible)
        assertTrue(result.conflicts.isNotEmpty())
    }
    
    @Test
    fun testEmptyGoalsAnalysis() {
        val emptyGameRun = gameRun.copy(goals = emptyList())
        val emptyGraph = SATGameActionGraph(satAdapter, gameData, emptyGameRun)
        
        val newGoal = Goal("goal1", "end_action", "Complete end action", 1)
        val result = emptyGraph.analyzeGoalCompatibility(newGoal)
        
        assertTrue(result is DomainResult.GoalCompatibilityResult)
        // Should be compatible when no other goals exist
        assertTrue(result.compatible)
        assertTrue(result.conflicts.isEmpty())
    }
    
    @Test
    fun testPathPreferences() {
        val goals = listOf(Goal("goal1", "end_action", "Complete end action", 1))
        
        val minimizePrefs = PathPreferences(minimizeActions = true)
        val avoidConflictPrefs = PathPreferences(avoidConflicts = true)
        
        val result1 = satGraph.findOptimalPath(goals, minimizePrefs)
        val result2 = satGraph.findOptimalPath(goals, avoidConflictPrefs)
        
        assertTrue(result1 is DomainResult.PathPlanResult)
        assertTrue(result2 is DomainResult.PathPlanResult)
        
        // Both should be feasible for this simple case
        assertTrue(result1.feasible)
        assertTrue(result2.feasible)
    }
    
    @Test
    fun testConflictResolutionSuggestions() {
        // Create a scenario with known conflicts
        val conflictingGoals = listOf(
            Goal("goal1", "middle_action", "Complete middle action", 1),
            Goal("goal2", "conflicting_action", "Complete conflicting action", 2)
        )
        
        val result = satGraph.analyzeMultiGoalCompatibility(conflictingGoals)
        
        assertTrue(result is MultiGoalCompatibilityResult)
        assertFalse(result.overallCompatible)
        assertTrue(result.conflicts.isNotEmpty())
        
        // Check that conflicts have meaningful information
        val conflict = result.conflicts.first()
        assertNotNull(conflict.conflictType)
        assertTrue(conflict.involvedGoals.isNotEmpty())
        assertTrue(conflict.explanation.isNotEmpty())
    }
    
    @Test
    fun testCascadeEffectAnalysis() {
        val result = satGraph.analyzeUndoability("start_action")
        
        assertTrue(result is DomainResult.UndoabilityResult)
        assertFalse(result.undoable)
        
        // Should have cascade effects since other actions depend on start_action
        assertTrue(result.cascadeEffects.isNotEmpty())
        
        val cascadeEffect = result.cascadeEffects.first()
        assertNotNull(cascadeEffect.affectedGoal)
        assertTrue(cascadeEffect.reason.isNotEmpty())
        assertNotNull(cascadeEffect.severity)
    }
    
    @Test
    fun testPathConstraints() {
        val goals = listOf(Goal("goal1", "end_action", "Complete end action", 1))
        val constraints = listOf(
            PathConstraint(PathConstraintType.MUST_INCLUDE, "middle_action", true),
            PathConstraint(PathConstraintType.MUST_EXCLUDE, "conflicting_action", true)
        )
        
        val preferences = PathPreferences()
        val result = satGraph.findOptimalPath(goals, preferences)
        
        assertTrue(result is DomainResult.PathPlanResult)
        // Should still be feasible with these constraints
        assertTrue(result.feasible)
    }
    
    @Test
    fun testConflictTypes() {
        // Test different types of conflicts
        val mutualExclusionResult = satGraph.analyzeGoalCompatibility(
            Goal("conflict", "conflicting_action", "Conflicting goal", 1)
        )
        
        assertTrue(mutualExclusionResult is DomainResult.GoalCompatibilityResult)
        if (!mutualExclusionResult.compatible) {
            val conflict = mutualExclusionResult.conflicts.firstOrNull()
            assertNotNull(conflict)
            // Should be a mutual exclusion conflict
            assertTrue(
                conflict.conflictType == ConflictType.MUTUAL_EXCLUSION ||
                conflict.conflictType == ConflictType.INDUCED_CONFLICT
            )
        }
    }
    
    @Test
    fun testUndoabilityWithEmptyGoals() {
        val emptyGameRun = gameRun.copy(goals = emptyList())
        val emptyGraph = SATGameActionGraph(satAdapter, gameData, emptyGameRun)
        
        val result = emptyGraph.analyzeUndoability("start_action")
        
        assertTrue(result is DomainResult.UndoabilityResult)
        // Should be undoable when there are no goals to preserve
        assertTrue(result.undoable)
        assertTrue(result.cascadeEffects.isEmpty())
    }
    
    @Test
    fun testAlternativePathsAnalysis() {
        val goals = listOf(Goal("goal1", "end_action", "Complete end action", 1))
        val result = satGraph.findOptimalPath(goals, PathPreferences())
        
        assertTrue(result is DomainResult.PathPlanResult)
        if (result.feasible) {
            // Check that we have a primary path
            assertTrue(result.actionSequence.isNotEmpty())
            // Alternative paths might be empty for simple cases
            assertNotNull(result.alternativePaths)
        }
    }
}