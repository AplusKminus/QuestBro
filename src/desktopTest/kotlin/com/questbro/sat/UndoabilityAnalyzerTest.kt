package com.questbro.sat

import com.questbro.domain.*
import kotlin.test.*

class UndoabilityAnalyzerTest {
    
    private lateinit var analyzer: UndoabilityAnalyzer
    private lateinit var satAdapter: SATAdapter
    private lateinit var gameData: GameData
    private lateinit var gameRun: GameRun
    
    @BeforeTest
    fun setup() {
        satAdapter = KoSATAdapter()
        analyzer = UndoabilityAnalyzer(satAdapter)
        
        // Create test game data with dependency chains
        val actions = mapOf(
            "foundation" to GameAction(
                id = "foundation",
                name = "Foundation Action",
                description = "Base action for chain",
                preconditions = PreconditionExpression.Always,
                rewards = listOf(Reward("foundation_item", "Foundation item")),
                category = ActionCategory.EXPLORATION
            ),
            "dependent1" to GameAction(
                id = "dependent1",
                name = "First Dependent",
                description = "Depends on foundation",
                preconditions = PreconditionExpression.ActionRequired("foundation"),
                rewards = listOf(Reward("dependent1_item", "Dependent item 1")),
                category = ActionCategory.QUEST
            ),
            "dependent2" to GameAction(
                id = "dependent2",
                name = "Second Dependent",
                description = "Depends on dependent1",
                preconditions = PreconditionExpression.ActionRequired("dependent1"),
                rewards = listOf(Reward("dependent2_item", "Dependent item 2")),
                category = ActionCategory.BOSS
            ),
            "independent" to GameAction(
                id = "independent",
                name = "Independent Action",
                description = "No dependencies",
                preconditions = PreconditionExpression.Always,
                rewards = listOf(Reward("independent_item", "Independent item")),
                category = ActionCategory.ITEM_PICKUP
            ),
            "item_dependent" to GameAction(
                id = "item_dependent",
                name = "Item Dependent",
                description = "Depends on foundation item",
                preconditions = PreconditionExpression.ItemRequired("foundation_item"),
                rewards = listOf(Reward("final_item", "Final item")),
                category = ActionCategory.NPC_DIALOGUE
            )
        )
        
        val items = mapOf(
            "foundation_item" to Item("foundation_item", "Foundation Item", "Foundation item"),
            "dependent1_item" to Item("dependent1_item", "Dependent Item 1", "Dependent item 1"),
            "dependent2_item" to Item("dependent2_item", "Dependent Item 2", "Dependent item 2"),
            "independent_item" to Item("independent_item", "Independent Item", "Independent item"),
            "final_item" to Item("final_item", "Final Item", "Final item")
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
            completedActions = setOf("foundation", "dependent1", "independent"),
            goals = listOf(
                Goal("goal1", "dependent2", "Complete dependent2", 1),
                Goal("goal2", "item_dependent", "Complete item dependent", 2)
            ),
            createdAt = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
    }
    
    @Test
    fun testBasicUndoabilityAnalysis() {
        val result = analyzer.analyzeUndoability("independent", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertEquals("independent", result.actionId)
        assertTrue(result.undoable) // Should be undoable as it has no dependencies
        assertEquals(UndoabilityReason.SAFE_TO_UNDO, result.reason)
        assertTrue(result.cascadeEffects.isEmpty())
    }
    
    @Test
    fun testDependentActionUndoability() {
        val result = analyzer.analyzeUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertEquals("foundation", result.actionId)
        assertFalse(result.undoable) // Should not be undoable due to dependencies
        assertEquals(UndoabilityReason.WOULD_BREAK_GOALS, result.reason)
        assertTrue(result.cascadeEffects.isNotEmpty())
        assertTrue(result.blockedBy.isNotEmpty())
    }
    
    @Test
    fun testNotCompletedActionUndoability() {
        val result = analyzer.analyzeUndoability("dependent2", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertEquals("dependent2", result.actionId)
        assertFalse(result.undoable)
        assertEquals(UndoabilityReason.NOT_COMPLETED, result.reason)
        assertTrue(result.cascadeEffects.isEmpty())
    }
    
    @Test
    fun testConditionalUndoabilityAnalysis() {
        val result = analyzer.analyzeConditionalUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is ConditionalUndoabilityResult)
        assertEquals("foundation", result.actionId)
        assertFalse(result.unconditionallyUndoable)
        assertTrue(result.conditions.isNotEmpty())
        assertTrue(result.worstCaseEffects.isNotEmpty())
        
        // Should have conditions like undoing dependent actions first
        val condition = result.conditions.first()
        assertNotNull(condition.type)
        assertTrue(condition.description.isNotEmpty())
    }
    
    @Test
    fun testSafeActionConditionalUndoability() {
        val result = analyzer.analyzeConditionalUndoability("independent", gameData, gameRun)
        
        assertTrue(result is ConditionalUndoabilityResult)
        assertEquals("independent", result.actionId)
        assertTrue(result.unconditionallyUndoable)
        assertTrue(result.conditions.isEmpty())
        assertTrue(result.worstCaseEffects.isEmpty())
    }
    
    @Test
    fun testOptimalUndoSequence() {
        val actionsToUndo = listOf("foundation", "dependent1", "independent")
        val result = analyzer.analyzeOptimalUndoSequence(actionsToUndo, gameData, gameRun)
        
        assertTrue(result is UndoSequenceResult)
        assertEquals(actionsToUndo, result.actionIds)
        
        if (result.feasible) {
            assertTrue(result.optimalSequence.isNotEmpty())
            assertTrue(result.totalCost >= 0)
        } else {
            assertTrue(result.warnings.isNotEmpty())
        }
    }
    
    @Test
    fun testUndoSequenceWithDependencies() {
        val actionsToUndo = listOf("dependent1", "foundation")
        val result = analyzer.analyzeOptimalUndoSequence(actionsToUndo, gameData, gameRun)
        
        assertTrue(result is UndoSequenceResult)
        
        if (result.feasible) {
            // Should undo dependent1 before foundation
            val sequence = result.optimalSequence
            val foundationIndex = sequence.indexOf("foundation")
            val dependent1Index = sequence.indexOf("dependent1")
            
            if (foundationIndex >= 0 && dependent1Index >= 0) {
                assertTrue(dependent1Index < foundationIndex)
            }
        }
    }
    
    @Test
    fun testComprehensiveUndoabilityAnalysis() {
        val result = analyzer.performComprehensiveUndoabilityAnalysis(gameData, gameRun)
        
        assertTrue(result is ComprehensiveUndoabilityResult)
        assertEquals(gameRun.completedActions.size, result.totalActions)
        assertTrue(result.undoableActions.isNotEmpty() || result.nonUndoableActions.isNotEmpty())
        assertNotNull(result.undoabilityReport)
        
        // Check report structure
        val report = result.undoabilityReport
        assertEquals(gameRun.completedActions.size, report.totalActions)
        assertTrue(report.undoableActions + report.nonUndoableActions == report.totalActions)
    }
    
    @Test
    fun testCascadeEffectSeverity() {
        val result = analyzer.analyzeUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertTrue(result.cascadeEffects.isNotEmpty())
        
        val cascadeEffect = result.cascadeEffects.first()
        assertNotNull(cascadeEffect.severity)
        assertTrue(cascadeEffect.reason.isNotEmpty())
        assertTrue(cascadeEffect.affectedGoal.isNotEmpty())
    }
    
    @Test
    fun testAlternativeActionsDiscovery() {
        val result = analyzer.analyzeUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        // Foundation action might have alternatives that provide similar items
        assertNotNull(result.alternativeActions)
    }
    
    @Test
    fun testDependencyChainBuilding() {
        val result = analyzer.performComprehensiveUndoabilityAnalysis(gameData, gameRun)
        
        assertTrue(result is ComprehensiveUndoabilityResult)
        assertTrue(result.dependencyChains.isNotEmpty())
        
        val chain = result.dependencyChains.first()
        assertNotNull(chain.rootAction)
        assertTrue(chain.actions.isNotEmpty())
        assertTrue(chain.depth > 0)
    }
    
    @Test
    fun testCriticalActionsIdentification() {
        val result = analyzer.performComprehensiveUndoabilityAnalysis(gameData, gameRun)
        
        assertTrue(result is ComprehensiveUndoabilityResult)
        // Should identify foundation as critical due to its dependencies
        assertTrue(result.criticalActions.contains("foundation"))
    }
    
    @Test
    fun testUndoWarnings() {
        val unsafeActions = listOf("foundation", "dependent1")
        val result = analyzer.analyzeOptimalUndoSequence(unsafeActions, gameData, gameRun)
        
        assertTrue(result is UndoSequenceResult)
        
        if (!result.feasible) {
            assertTrue(result.warnings.isNotEmpty())
            
            val warning = result.warnings.first()
            assertNotNull(warning.severity)
            assertTrue(warning.message.isNotEmpty())
            assertNotNull(warning.suggestedAction)
        }
    }
    
    @Test
    fun testEmptyGoalsUndoability() {
        val emptyGoalsRun = gameRun.copy(goals = emptyList())
        val result = analyzer.analyzeUndoability("foundation", gameData, emptyGoalsRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        // Should be undoable when no goals to preserve
        assertTrue(result.undoable)
        assertTrue(result.cascadeEffects.isEmpty())
    }
    
    @Test
    fun testUndoabilityConditionTypes() {
        val result = analyzer.analyzeConditionalUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is ConditionalUndoabilityResult)
        
        if (result.conditions.isNotEmpty()) {
            val condition = result.conditions.first()
            assertTrue(
                condition.type == UndoabilityConditionType.UNDO_DEPENDENT_ACTION ||
                condition.type == UndoabilityConditionType.COMPLETE_ALTERNATIVE_ACTION ||
                condition.type == UndoabilityConditionType.REMOVE_GOAL ||
                condition.type == UndoabilityConditionType.MODIFY_CONSTRAINTS
            )
        }
    }
    
    @Test
    fun testUndoabilityReasonBreakdown() {
        val result = analyzer.performComprehensiveUndoabilityAnalysis(gameData, gameRun)
        
        assertTrue(result is ComprehensiveUndoabilityResult)
        val reasonBreakdown = result.undoabilityReport.reasonBreakdown
        
        // Should have different reasons for different actions
        assertTrue(reasonBreakdown.containsKey(UndoabilityReason.SAFE_TO_UNDO) ||
                  reasonBreakdown.containsKey(UndoabilityReason.WOULD_BREAK_GOALS))
    }
    
    @Test
    fun testSeverityBreakdown() {
        val result = analyzer.performComprehensiveUndoabilityAnalysis(gameData, gameRun)
        
        assertTrue(result is ComprehensiveUndoabilityResult)
        val severityBreakdown = result.undoabilityReport.severityBreakdown
        
        // Should have severity information for cascade effects
        assertNotNull(severityBreakdown)
    }
    
    @Test
    fun testUndoabilityWithItemDependencies() {
        // Test undoing an action that provides items needed by other actions
        val result = analyzer.analyzeUndoability("foundation", gameData, gameRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertFalse(result.undoable)
        
        // Should detect that item_dependent goal depends on foundation_item
        val hasItemDependencyEffect = result.cascadeEffects.any { effect ->
            effect.affectedGoal == "goal2" // item_dependent goal
        }
        assertTrue(hasItemDependencyEffect)
    }
    
    @Test
    fun testUndoSequenceCost() {
        val actionsToUndo = listOf("independent")
        val result = analyzer.analyzeOptimalUndoSequence(actionsToUndo, gameData, gameRun)
        
        assertTrue(result is UndoSequenceResult)
        if (result.feasible) {
            assertTrue(result.totalCost >= 0)
            // Simple cost should be related to number of actions
            assertTrue(result.totalCost >= actionsToUndo.size)
        }
    }
    
    @Test
    fun testUndoabilityWithComplexPreconditions() {
        // Create action with complex preconditions
        val complexAction = GameAction(
            id = "complex",
            name = "Complex Action",
            description = "Complex preconditions",
            preconditions = PreconditionExpression.And(
                listOf(
                    PreconditionExpression.ActionRequired("foundation"),
                    PreconditionExpression.ItemRequired("dependent1_item")
                )
            ),
            rewards = listOf(Reward("complex_item", "Complex item")),
            category = ActionCategory.BOSS
        )
        
        val complexGameData = gameData.copy(
            actions = gameData.actions + ("complex" to complexAction)
        )
        
        val complexRun = gameRun.copy(
            completedActions = gameRun.completedActions + "complex",
            goals = gameRun.goals + Goal("complex_goal", "complex", "Complex goal", 3)
        )
        
        val result = analyzer.analyzeUndoability("foundation", complexGameData, complexRun)
        
        assertTrue(result is UndoabilityAnalysisResult)
        assertFalse(result.undoable) // Should be blocked by complex action
        assertTrue(result.cascadeEffects.isNotEmpty())
    }
}