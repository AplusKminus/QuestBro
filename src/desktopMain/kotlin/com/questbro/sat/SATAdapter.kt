package com.questbro.sat

import com.questbro.domain.GameData
import com.questbro.domain.GameRun
import com.questbro.domain.Goal
import org.kosat.Kosat

interface SATAdapter {
    fun encode(gameData: GameData, gameRun: GameRun): SATEncoding
    fun solve(encoding: SATEncoding, query: SATQuery): SATResult
    fun decode(result: SATResult, encoding: SATEncoding): DomainResult
}

data class SATEncoding(
    val solver: Kosat,
    val actionVariables: Map<String, Int>,
    val itemVariables: Map<String, Int>,
    val timeStepVariables: Map<String, Map<Int, Int>>,
    val goalVariables: Map<String, Int>,
    val metadata: EncodingMetadata
)

data class EncodingMetadata(
    val variableCount: Int,
    val clauseCount: Int,
    val actionCount: Int,
    val itemCount: Int,
    val encodingTime: Long
)

sealed class SATQuery {
    data class GoalCompatibilityQuery(
        val newGoal: Goal,
        val existingGoals: List<Goal>
    ) : SATQuery()
    
    data class UndoabilityQuery(
        val actionId: String,
        val preserveGoals: List<Goal>
    ) : SATQuery()
    
    data class OptimalPathQuery(
        val goals: List<Goal>,
        val preferences: PathPreferences,
        val constraints: List<PathConstraint> = emptyList()
    ) : SATQuery()
}

data class PathPreferences(
    val minimizeActions: Boolean = true,
    val avoidConflicts: Boolean = true,
    val preferredActions: List<String> = emptyList(),
    val avoidedActions: List<String> = emptyList()
)

data class PathConstraint(
    val type: PathConstraintType,
    val actionId: String,
    val value: Any
)

enum class PathConstraintType {
    MUST_INCLUDE,
    MUST_EXCLUDE,
    MUST_COMPLETE_BEFORE,
    MUST_COMPLETE_AFTER
}

sealed class SATResult {
    data class Satisfiable(val model: Map<Int, Boolean>) : SATResult()
    data class Unsatisfiable(val unsatCore: List<Int>) : SATResult()
    data class Unknown(val reason: String) : SATResult()
}

sealed class DomainResult {
    data class GoalCompatibilityResult(
        val compatible: Boolean,
        val conflicts: List<ConflictInfo> = emptyList(),
        val requiredActions: List<String> = emptyList()
    ) : DomainResult()
    
    data class UndoabilityResult(
        val undoable: Boolean,
        val blockedBy: List<String> = emptyList(),
        val cascadeEffects: List<CascadeEffect> = emptyList()
    ) : DomainResult()
    
    data class PathPlanResult(
        val feasible: Boolean,
        val actionSequence: List<String> = emptyList(),
        val alternativePaths: List<List<String>> = emptyList(),
        val cost: Int = 0
    ) : DomainResult()
}

data class ConflictInfo(
    val conflictType: ConflictType,
    val involvedGoals: List<String>,
    val explanation: String
)

enum class ConflictType {
    MUTUAL_EXCLUSION,
    INDUCED_CONFLICT,
    RESOURCE_CONFLICT
}

data class CascadeEffect(
    val affectedGoal: String,
    val reason: String,
    val severity: EffectSeverity
)

enum class EffectSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}