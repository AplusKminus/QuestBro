# KoSAT Integration Plan for QuestBro

## Executive Summary

This plan outlines the integration of KoSAT (pure Kotlin CDCL SAT solver) into QuestBro to enhance goal compatibility analysis, undoability detection, and path dependency resolution. The integration will use an adapter pattern to translate between domain concepts and SAT formulas.

## 1. SAT Solver Adapter Architecture

### 1.1 Core Adapter Components

**SATAdapter Interface**
```kotlin
interface SATAdapter {
    fun encode(gameData: GameData, gameRun: GameRun): SATEncoding
    fun solve(encoding: SATEncoding, query: SATQuery): SATResult
    fun decode(result: SATResult, encoding: SATEncoding): DomainResult
}
```

**SATEncoding Data Structure**
```kotlin
data class SATEncoding(
    val solver: Kosat,
    val actionVariables: Map<String, Int>,
    val itemVariables: Map<String, Int>,
    val timeStepVariables: Map<String, Map<Int, Int>>,
    val metadata: EncodingMetadata
)
```

**Variable Allocation Strategy**
- Actions: `actionId -> variable_number`
- Items: `itemId -> variable_number`
- Time steps: `(actionId, timeStep) -> variable_number` (for temporal reasoning)
- Goal states: `goalId -> variable_number`

### 1.2 Translation Layers

**Domain-to-SAT Translation**
- `PreconditionExpression` → CNF clauses
- `GameAction` dependencies → implication constraints
- `Goal` conflicts → mutual exclusion constraints
- Item requirements → logical dependencies

**SAT-to-Domain Translation**
- SAT model → action execution sequence
- Conflict analysis → goal incompatibility reports
- Unsat core → minimal conflicting requirements

## 2. Integration Points

### 2.1 Goal Compatibility Analysis

**Enhanced GameActionGraph**
```kotlin
class SATGameActionGraph(
    private val satAdapter: SATAdapter,
    gameData: GameData,
    gameRun: GameRun
) {
    fun analyzeGoalCompatibility(newGoal: Goal): GoalCompatibilityResult {
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = GoalCompatibilityQuery(newGoal, gameRun.goals)
        return satAdapter.solve(encoding, query).let { result ->
            satAdapter.decode(result, encoding)
        }
    }
}
```

**Benefits over current BFS approach:**
- Simultaneous analysis of multiple goals
- Detection of indirect conflicts through complex dependency chains
- Optimization for minimal conflict resolution
- Scalability to large action graphs

### 2.2 Undoability Analysis

**Action Undoability Detection**
```kotlin
class UndoabilityAnalyzer(private val satAdapter: SATAdapter) {
    fun analyzeUndoability(actionId: String, gameData: GameData, gameRun: GameRun): UndoabilityResult {
        val encoding = satAdapter.encode(gameData, gameRun)
        val query = UndoabilityQuery(actionId, gameRun.goals)
        return satAdapter.solve(encoding, query).let { result ->
            when (result) {
                is SATResult.Satisfiable -> UndoabilityResult.Undoable
                is SATResult.Unsatisfiable -> UndoabilityResult.NotUndoable(result.unsatCore)
            }
        }
    }
}
```

**Advanced Undoability Features:**
- Cascade effect analysis (what becomes impossible if undone)
- Conditional undoability (safe to undo if certain conditions met)
- Optimal undo sequences for goal preservation

### 2.3 Path Dependency Analysis

**Optimal Path Planning**
```kotlin
class SATPathPlanner(private val satAdapter: SATAdapter) {
    fun findOptimalPath(
        startState: GameRun,
        goals: List<Goal>,
        preferences: PathPreferences
    ): PathPlanResult {
        val encoding = satAdapter.encode(gameData, startState)
        val query = OptimalPathQuery(goals, preferences)
        return satAdapter.solve(encoding, query).let { result ->
            satAdapter.decode(result, encoding) as PathPlanResult
        }
    }
}
```

**Path Optimization Capabilities:**
- Minimal action sequences
- Preference-based path selection
- Multiple valid path enumeration
- Conflict-free path guarantees

## 3. Detailed Implementation Plan

### 3.1 Phase 1: Core SAT Adapter (Weeks 1-2)

**Dependencies Setup**
```kotlin
// build.gradle.kts
repositories {
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.UnitTestBot.kosat:kosat:main-SNAPSHOT")
}
```

**Basic Adapter Implementation**
- Create `SATAdapter` interface and `KoSATAdapter` implementation
- Implement variable allocation for actions and items
- Basic `PreconditionExpression` to CNF translation
- Simple encoding/decoding for current domain models

### 3.2 Phase 2: Goal Compatibility Enhancement (Weeks 3-4)

**Enhanced Conflict Detection**
- Replace current `MutualExclusion` detection with SAT-based analysis
- Implement `InducedConflict` detection using unsat core analysis
- Add support for complex multi-goal compatibility checking

**Integration Points**
- Modify `GameActionGraph.analyzeGoalConflicts()`
- Enhance `ConflictResult` with SAT-derived explanations
- Update UI to show detailed conflict explanations

### 3.3 Phase 3: Undoability Analysis (Weeks 5-6)

**Action Undoability Engine**
- Implement `UndoabilityAnalyzer` with SAT backend
- Add cascade effect analysis
- Support conditional undoability queries

**Integration Points**
- Enhance `GameActionGraph.completedActions` with undoability info
- Add undoability warnings in UI
- Implement "safe undo" recommendations

### 3.4 Phase 4: Path Dependency & Optimization (Weeks 7-8)

**Advanced Path Planning**
- Implement optimal path finding with SAT
- Add support for path preferences (shortest, safest, etc.)
- Multi-objective optimization support

**Integration Points**
- Replace BFS-based path finding in `GameActionGraph`
- Add path optimization options to UI
- Implement "what-if" analysis for action sequences

## 4. Technical Specifications

### 4.1 SAT Encoding Strategies

**Action Encoding**
```kotlin
// For each action A: variable A represents "action A is completed"
fun encodeAction(action: GameAction): Int = solver.addVariable()

// For precondition "A requires B": A → B (¬A ∨ B)
fun encodePrecondition(action: GameAction, dependency: ActionRequired) {
    solver.addClause(-actionVariables[action.id]!!, actionVariables[dependency.actionId]!!)
}
```

**Temporal Encoding** (for advanced scenarios)
```kotlin
// For time-sensitive analysis: A_t represents "action A completed at time t"
fun encodeTemporalAction(actionId: String, timeStep: Int): Int {
    return timeStepVariables[actionId]?.get(timeStep) ?: solver.addVariable()
}
```

**Goal Encoding**
```kotlin
// For goal G targeting action A: G ↔ A
fun encodeGoal(goal: Goal) {
    val goalVar = solver.addVariable()
    val actionVar = actionVariables[goal.targetId]!!
    solver.addClause(-goalVar, actionVar)  // G → A
    solver.addClause(goalVar, -actionVar)  // A → G
}
```

### 4.2 Query Types

**Goal Compatibility Query**
```kotlin
data class GoalCompatibilityQuery(
    val newGoal: Goal,
    val existingGoals: List<Goal>
) : SATQuery
```

**Undoability Query**
```kotlin
data class UndoabilityQuery(
    val actionId: String,
    val preserveGoals: List<Goal>
) : SATQuery
```

**Optimal Path Query**
```kotlin
data class OptimalPathQuery(
    val goals: List<Goal>,
    val preferences: PathPreferences,
    val constraints: List<PathConstraint>
) : SATQuery
```

### 4.3 Performance Optimizations

**Incremental Solving**
- Reuse SAT solver instances across queries
- Maintain base clauses for game structure
- Add/remove assumptions for different queries

**Caching Strategy**
- Cache SAT encodings for unchanged game states
- Invalidate cache on game state changes
- Selective re-encoding for partial updates

**Solver Configuration**
```kotlin
fun createOptimizedSolver(): Kosat {
    return Kosat(mutableListOf(), 0).apply {
        // Configure for typical QuestBro problem sizes
        // Enable relevant heuristics
    }
}
```

## 5. Integration Benefits

### 5.1 Enhanced Capabilities

**Goal Management**
- Simultaneous multi-goal analysis
- Complex conflict detection beyond current AND/OR logic
- Optimization for minimal conflicts

**Path Planning**
- Guaranteed optimal solutions
- Multiple solution enumeration
- Preference-based optimization

**Undoability Analysis**
- Precise cascade effect analysis
- Conditional safety analysis
- Minimal disruption recommendations

### 5.2 Scalability Improvements

**Performance**
- SAT solvers scale better than BFS for complex constraints
- Incremental solving reduces repeated computation
- Native optimization algorithms

**Expressiveness**
- Support for complex temporal constraints
- Quantified expressions (at least N, exactly N)
- Resource constraints and optimization

### 5.3 Maintainability

**Clear Separation**
- SAT adapter isolates complexity
- Domain logic remains unchanged
- Easy to swap SAT solver implementations

**Testing**
- SAT queries are deterministic and testable
- Encoding correctness can be verified
- Performance benchmarks possible

## 6. Migration Strategy

### 6.1 Backward Compatibility

**Gradual Migration**
- Keep existing `PreconditionEngine` as fallback
- Add SAT-based alternatives alongside existing methods
- Feature flags for SAT vs. traditional analysis

**Performance Comparison**
- Benchmark SAT vs. BFS for typical use cases
- Identify optimal thresholds for SAT usage
- Maintain both implementations during transition

### 6.2 Testing Strategy

**Unit Tests**
- Test SAT adapter encoding/decoding correctness
- Verify query result consistency with existing logic
- Performance regression testing

**Integration Tests**
- End-to-end goal compatibility scenarios
- Complex undoability test cases
- Path optimization verification

## 7. Risk Assessment

### 7.1 Technical Risks

**SAT Solver Dependency**
- Mitigation: Abstract behind adapter interface
- Fallback: Keep existing logic as backup

**Performance Overhead**
- Mitigation: Incremental solving and caching
- Monitoring: Performance benchmarks and profiling

**Complexity**
- Mitigation: Comprehensive testing and documentation
- Training: Team education on SAT concepts

### 7.2 Implementation Risks

**Integration Complexity**
- Mitigation: Phased rollout with feature flags
- Testing: Extensive integration testing

**Maintenance Burden**
- Mitigation: Clean architecture and documentation
- Support: Active KoSAT community and maintenance

## Conclusion

Integrating KoSAT into QuestBro will significantly enhance the application's analytical capabilities while maintaining clean architecture through the adapter pattern. The phased implementation approach ensures manageable complexity while delivering incremental value. The SAT-based approach will provide more accurate, scalable, and feature-rich analysis of game progression scenarios.