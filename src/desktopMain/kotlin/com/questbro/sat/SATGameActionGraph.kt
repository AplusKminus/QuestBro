package com.questbro.sat

import com.questbro.domain.*

/**
 * SAT-enhanced version of GameActionGraph that uses KoSAT for advanced goal compatibility analysis,
 * undoability detection, and path optimization.
 */
class SATGameActionGraph(
    private val satAdapter: SATAdapter,
    private val gameData: GameData,
    private val gameRun: GameRun
) {
    
    /**
     * Analyzes compatibility of a new goal with existing goals using SAT
     */
    fun analyzeGoalCompatibility(newGoal: Goal): DomainResult.GoalCompatibilityResult {
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = SATQuery.GoalCompatibilityQuery(newGoal, gameRun.goals)
        
        return when (val result = (satAdapter as KoSATAdapter).solveAndDecode(encoding, query)) {
            is DomainResult.GoalCompatibilityResult -> DomainResult.GoalCompatibilityResult(
                compatible = result.compatible,
                conflicts = if (!result.compatible) listOf(
                    ConflictInfo(
                        conflictType = ConflictType.MUTUAL_EXCLUSION,
                        involvedGoals = listOf(newGoal.id),
                        explanation = "Goal conflicts with existing goals"
                    )
                ) else emptyList(),
                requiredActions = result.requiredActions
            )
            else -> DomainResult.GoalCompatibilityResult(
                compatible = false,
                conflicts = listOf(
                    ConflictInfo(
                        conflictType = ConflictType.MUTUAL_EXCLUSION,
                        involvedGoals = listOf(newGoal.id),
                        explanation = "Unable to analyze goal compatibility"
                    )
                )
            )
        }
    }
    
    /**
     * Analyzes multiple goals simultaneously for compatibility
     */
    fun analyzeMultiGoalCompatibility(goals: List<Goal>): MultiGoalCompatibilityResult {
        val conflicts = mutableListOf<ConflictInfo>()
        val compatibleGoals = mutableListOf<Goal>()
        val incompatibleGoals = mutableListOf<Goal>()
        
        // Check each goal against all others
        for (i in goals.indices) {
            val currentGoal = goals[i]
            val otherGoals = goals.take(i) + goals.drop(i + 1)
            
            val tempGameRun = gameRun.copy(goals = otherGoals)
            val tempGraph = SATGameActionGraph(satAdapter, gameData, tempGameRun)
            
            val compatibility = tempGraph.analyzeGoalCompatibility(currentGoal)
            
            if (compatibility.compatible) {
                compatibleGoals.add(currentGoal)
            } else {
                incompatibleGoals.add(currentGoal)
                conflicts.addAll(compatibility.conflicts)
            }
        }
        
        return MultiGoalCompatibilityResult(
            compatibleGoals = compatibleGoals,
            incompatibleGoals = incompatibleGoals,
            conflicts = conflicts,
            overallCompatible = incompatibleGoals.isEmpty()
        )
    }
    
    /**
     * Analyzes whether an action can be safely undone without breaking goals
     */
    fun analyzeUndoability(actionId: String): DomainResult.UndoabilityResult {
        if (!gameRun.completedActions.contains(actionId)) {
            return DomainResult.UndoabilityResult(
                undoable = false,
                blockedBy = emptyList(),
                cascadeEffects = listOf(
                    CascadeEffect(
                        affectedGoal = "N/A",
                        reason = "Action $actionId is not completed",
                        severity = EffectSeverity.CRITICAL
                    )
                )
            )
        }
        
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = SATQuery.UndoabilityQuery(actionId, gameRun.goals)
        
        return when (val result = (satAdapter as KoSATAdapter).solveAndDecode(encoding, query)) {
            is DomainResult.UndoabilityResult -> DomainResult.UndoabilityResult(
                undoable = result.undoable,
                blockedBy = result.blockedBy,
                cascadeEffects = if (!result.undoable) listOf(
                    CascadeEffect(
                        affectedGoal = gameRun.goals.firstOrNull()?.id ?: "Unknown",
                        reason = "Action $actionId is required for achieving goals",
                        severity = EffectSeverity.HIGH
                    )
                ) else emptyList()
            )
            else -> DomainResult.UndoabilityResult(
                undoable = false,
                blockedBy = listOf(actionId),
                cascadeEffects = listOf(
                    CascadeEffect(
                        affectedGoal = "Unknown",
                        reason = "Unable to analyze undoability",
                        severity = EffectSeverity.HIGH
                    )
                )
            )
        }
    }
    
    /**
     * Finds an optimal path to achieve multiple goals simultaneously
     */
    fun findOptimalPath(
        goals: List<Goal>,
        preferences: PathPreferences = PathPreferences()
    ): DomainResult.PathPlanResult {
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = SATQuery.OptimalPathQuery(goals, preferences)
        
        return when (val result = (satAdapter as KoSATAdapter).solveAndDecode(encoding, query)) {
            is DomainResult.PathPlanResult -> result
            else -> DomainResult.PathPlanResult(
                feasible = false,
                actionSequence = emptyList(),
                alternativePaths = emptyList(),
                cost = Int.MAX_VALUE
            )
        }
    }
    
    /**
     * Analyzes what actions would become available or unavailable after performing an action
     */
    fun analyzeActionImpact(actionId: String): ActionImpactResult {
        val action = gameData.actions[actionId]
            ?: return ActionImpactResult(
                enabledActions = emptyList(),
                disabledActions = emptyList(),
                enabledGoals = emptyList(),
                disabledGoals = emptyList()
            )
        
        // Create a hypothetical game run with the action completed
        val hypotheticalRun = gameRun.copy(
            completedActions = gameRun.completedActions + actionId
        )
        
        val currentEncoding = satAdapter.encode(gameData, gameRun)
        val hypotheticalEncoding = satAdapter.encode(gameData, hypotheticalRun)
        
        // Compare what's possible in each scenario
        val enabledActions = findEnabledActions(currentEncoding, hypotheticalEncoding)
        val disabledActions = findDisabledActions(currentEncoding, hypotheticalEncoding)
        val enabledGoals = findEnabledGoals(currentEncoding, hypotheticalEncoding)
        val disabledGoals = findDisabledGoals(currentEncoding, hypotheticalEncoding)
        
        return ActionImpactResult(
            enabledActions = enabledActions,
            disabledActions = disabledActions,
            enabledGoals = enabledGoals,
            disabledGoals = disabledGoals
        )
    }
    
    /**
     * Performs comprehensive conflict analysis for all current goals
     */
    fun performComprehensiveConflictAnalysis(): ConflictAnalysisResult {
        val allConflicts = mutableListOf<ConflictInfo>()
        val conflictGroups = mutableListOf<ConflictGroup>()
        
        // Analyze pairwise conflicts between all goals
        for (i in gameRun.goals.indices) {
            for (j in i + 1 until gameRun.goals.size) {
                val goal1 = gameRun.goals[i]
                val goal2 = gameRun.goals[j]
                
                val tempRun = gameRun.copy(goals = listOf(goal1))
                val tempGraph = SATGameActionGraph(satAdapter, gameData, tempRun)
                val compatibility = tempGraph.analyzeGoalCompatibility(goal2)
                
                if (!compatibility.compatible) {
                    allConflicts.addAll(compatibility.conflicts)
                    
                    // Group conflicts by type
                    val conflictGroup = ConflictGroup(
                        conflictType = compatibility.conflicts.firstOrNull()?.conflictType ?: ConflictType.MUTUAL_EXCLUSION,
                        involvedGoals = listOf(goal1.id, goal2.id),
                        severity = determineSeverity(compatibility.conflicts),
                        resolutionSuggestions = generateResolutionSuggestions(goal1, goal2, compatibility.conflicts)
                    )
                    conflictGroups.add(conflictGroup)
                }
            }
        }
        
        return ConflictAnalysisResult(
            totalConflicts = allConflicts.size,
            conflictGroups = conflictGroups,
            conflictSummary = summarizeConflicts(allConflicts),
            resolutionPriority = prioritizeConflictResolution(conflictGroups)
        )
    }
    
    // Helper methods
    
    private fun findEnabledActions(currentEncoding: SATEncoding, hypotheticalEncoding: SATEncoding): List<String> {
        // This is a simplified implementation - in practice, we'd need to solve
        // both encodings and compare which actions become available
        return emptyList()
    }
    
    private fun findDisabledActions(currentEncoding: SATEncoding, hypotheticalEncoding: SATEncoding): List<String> {
        // This is a simplified implementation - in practice, we'd need to solve
        // both encodings and compare which actions become unavailable
        return emptyList()
    }
    
    private fun findEnabledGoals(currentEncoding: SATEncoding, hypotheticalEncoding: SATEncoding): List<String> {
        // This is a simplified implementation - in practice, we'd need to solve
        // both encodings and compare which goals become achievable
        return emptyList()
    }
    
    private fun findDisabledGoals(currentEncoding: SATEncoding, hypotheticalEncoding: SATEncoding): List<String> {
        // This is a simplified implementation - in practice, we'd need to solve
        // both encodings and compare which goals become unachievable
        return emptyList()
    }
    
    private fun determineSeverity(conflicts: List<ConflictInfo>): EffectSeverity {
        return when {
            conflicts.any { it.conflictType == ConflictType.MUTUAL_EXCLUSION } -> EffectSeverity.CRITICAL
            conflicts.any { it.conflictType == ConflictType.INDUCED_CONFLICT } -> EffectSeverity.HIGH
            conflicts.any { it.conflictType == ConflictType.RESOURCE_CONFLICT } -> EffectSeverity.MEDIUM
            else -> EffectSeverity.LOW
        }
    }
    
    private fun generateResolutionSuggestions(
        goal1: Goal,
        goal2: Goal,
        conflicts: List<ConflictInfo>
    ): List<ResolutionSuggestion> {
        val suggestions = mutableListOf<ResolutionSuggestion>()
        
        for (conflict in conflicts) {
            when (conflict.conflictType) {
                ConflictType.MUTUAL_EXCLUSION -> {
                    suggestions.add(
                        ResolutionSuggestion(
                            type = ResolutionType.REMOVE_GOAL,
                            description = "Remove one of the conflicting goals: ${goal1.description} or ${goal2.description}",
                            affectedGoals = listOf(goal1.id, goal2.id),
                            priority = ResolutionPriority.HIGH
                        )
                    )
                }
                ConflictType.INDUCED_CONFLICT -> {
                    suggestions.add(
                        ResolutionSuggestion(
                            type = ResolutionType.REORDER_GOALS,
                            description = "Consider reordering goals to avoid induced conflicts",
                            affectedGoals = listOf(goal1.id, goal2.id),
                            priority = ResolutionPriority.MEDIUM
                        )
                    )
                }
                ConflictType.RESOURCE_CONFLICT -> {
                    suggestions.add(
                        ResolutionSuggestion(
                            type = ResolutionType.FIND_ALTERNATIVE,
                            description = "Find alternative actions that provide the same outcome",
                            affectedGoals = listOf(goal1.id, goal2.id),
                            priority = ResolutionPriority.LOW
                        )
                    )
                }
            }
        }
        
        return suggestions
    }
    
    private fun summarizeConflicts(conflicts: List<ConflictInfo>): ConflictSummary {
        val mutualExclusions = conflicts.count { it.conflictType == ConflictType.MUTUAL_EXCLUSION }
        val inducedConflicts = conflicts.count { it.conflictType == ConflictType.INDUCED_CONFLICT }
        val resourceConflicts = conflicts.count { it.conflictType == ConflictType.RESOURCE_CONFLICT }
        
        return ConflictSummary(
            mutualExclusions = mutualExclusions,
            inducedConflicts = inducedConflicts,
            resourceConflicts = resourceConflicts,
            totalConflicts = conflicts.size
        )
    }
    
    private fun prioritizeConflictResolution(conflictGroups: List<ConflictGroup>): List<ConflictGroup> {
        return conflictGroups.sortedWith(compareBy(
            { it.severity },
            { it.conflictType },
            { it.involvedGoals.size }
        ))
    }
}

// Additional data classes for SAT-enhanced functionality

data class GoalCompatibilityResult(
    val compatible: Boolean,
    val conflicts: List<ConflictInfo> = emptyList(),
    val requiredActions: List<String> = emptyList()
)

data class MultiGoalCompatibilityResult(
    val compatibleGoals: List<Goal>,
    val incompatibleGoals: List<Goal>,
    val conflicts: List<ConflictInfo>,
    val overallCompatible: Boolean
)

data class UndoabilityResult(
    val undoable: Boolean,
    val blockedBy: List<String> = emptyList(),
    val cascadeEffects: List<CascadeEffect> = emptyList()
)

data class ActionImpactResult(
    val enabledActions: List<String>,
    val disabledActions: List<String>,
    val enabledGoals: List<String>,
    val disabledGoals: List<String>
)

data class ConflictAnalysisResult(
    val totalConflicts: Int,
    val conflictGroups: List<ConflictGroup>,
    val conflictSummary: ConflictSummary,
    val resolutionPriority: List<ConflictGroup>
)

data class ConflictGroup(
    val conflictType: ConflictType,
    val involvedGoals: List<String>,
    val severity: EffectSeverity,
    val resolutionSuggestions: List<ResolutionSuggestion>
)

data class ConflictSummary(
    val mutualExclusions: Int,
    val inducedConflicts: Int,
    val resourceConflicts: Int,
    val totalConflicts: Int
)

data class ResolutionSuggestion(
    val type: ResolutionType,
    val description: String,
    val affectedGoals: List<String>,
    val priority: ResolutionPriority
)

enum class ResolutionType {
    REMOVE_GOAL,
    REORDER_GOALS,
    FIND_ALTERNATIVE,
    MODIFY_CONSTRAINTS
}

enum class ResolutionPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}