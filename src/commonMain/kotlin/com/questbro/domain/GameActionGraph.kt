package com.questbro.domain

/**
 * Core domain class representing an immutable snapshot of game state (progress + goals).
 * Provides the primary interface for querying and transforming game progression.
 */
data class GameActionGraph private constructor(
    private val gameData: GameData,
    private val completedActionIds: Set<String>,
    private val activeGoals: Set<Goal>,
    private val pathCache: PathCache = PathCache()
) {
    
    companion object {
        /**
         * Creates a new GameActionGraph from initial state
         */
        fun create(
            gameData: GameData,
            completedActions: Set<String> = emptySet(),
            goals: Set<Goal> = emptySet()
        ): GameActionGraph {
            return GameActionGraph(gameData, completedActions, goals).refreshCaches()
        }
    }
    
    // ============================================================================
    // Goal Accessors
    // ============================================================================
    
    /**
     * Goals that can be completed immediately (no additional actions required)
     */
    val readyGoals: List<GoalWithPathInfo> by lazy {
        getGoalsWithPathInfo().filter { it.pathLength == 0 && !completedActionIds.contains(it.goal.targetId) }
    }
    
    /**
     * Goals that can be achieved with additional actions
     */
    val achievableGoals: List<GoalWithPathInfo> by lazy {
        getGoalsWithPathInfo().filter { it.pathLength > 0 && !completedActionIds.contains(it.goal.targetId) }
    }
    
    /**
     * Goals that cannot be achieved due to conflicting completed actions or invalid target actions
     */
    val unachievableGoals: List<GoalWithPathInfo> by lazy {
        activeGoals.mapNotNull { goal ->
            val action = gameData.actions[goal.targetId]
            if (action == null) {
                // Invalid goal - target action doesn't exist
                GoalWithPathInfo(
                    goal = goal,
                    action = GameAction(
                        id = goal.targetId,
                        name = "Invalid Action",
                        description = "Action does not exist",
                        preconditions = PreconditionExpression.ActionRequired("__invalid_action__"),
                        rewards = emptyList(),
                        category = ActionCategory.EXPLORATION
                    ),
                    pathLength = -1, // Unachievable
                    path = null,
                    blockingActions = emptyList()
                )
            } else {
                val pathInfo = pathCache.getPathInfo(goal.targetId)
                if (pathInfo?.isAchievable == false) {
                    GoalWithPathInfo(
                        goal = goal,
                        action = action,
                        pathLength = -1, // Unachievable
                        path = null,
                        blockingActions = pathInfo.blockingActions
                    )
                } else null
            }
        }
    }
    
    /**
     * Goals that have already been completed
     */
    val completedGoals: List<GoalWithPathInfo> by lazy {
        activeGoals.mapNotNull { goal ->
            if (completedActionIds.contains(goal.targetId)) {
                gameData.actions[goal.targetId]?.let { action ->
                    GoalWithPathInfo(
                        goal = goal,
                        action = action,
                        pathLength = 0,
                        path = emptyList(),
                        blockingActions = emptyList()
                    )
                }
            } else null
        }
    }
    
    // ============================================================================
    // Action Accessors
    // ============================================================================
    
    /**
     * Actions that can be performed in the current state
     */
    val currentActions: List<AvailableAction> by lazy {
        val preconditionEngine = PreconditionEngine()
        val inventory = preconditionEngine.getInventory(gameData, completedActionIds)
        
        gameData.actions.values
            .filter { action ->
                !completedActionIds.contains(action.id) &&
                preconditionEngine.evaluate(action.preconditions, completedActionIds, inventory)
            }
            .map { action ->
                AvailableAction(
                    action = action,
                    enablesGoals = calculateEnabledGoals(action),
                    blocksGoals = calculateBlockedGoals(action)
                )
            }
    }
    
    /**
     * Actions that have been completed
     */
    val completedActions: List<CompletedAction> by lazy {
        completedActionIds.mapNotNull { actionId ->
            gameData.actions[actionId]?.let { action ->
                CompletedAction(
                    action = action,
                    canUndo = canUndoAction(actionId)
                )
            }
        }
    }
    
    // ============================================================================
    // Graph Transformations
    // ============================================================================
    
    /**
     * Returns a new graph with the specified action performed
     */
    fun performAction(actionId: String): GameActionGraph {
        require(gameData.actions.containsKey(actionId)) { "Action $actionId not found" }
        require(!completedActionIds.contains(actionId)) { "Action $actionId already completed" }
        
        val preconditionEngine = PreconditionEngine()
        val inventory = preconditionEngine.getInventory(gameData, completedActionIds)
        val action = gameData.actions[actionId]!!
        
        require(preconditionEngine.evaluate(action.preconditions, completedActionIds, inventory)) {
            "Action $actionId preconditions not met"
        }
        
        return GameActionGraph(
            gameData = gameData,
            completedActionIds = completedActionIds + actionId,
            activeGoals = activeGoals
        ).refreshCaches()
    }
    
    /**
     * Returns a new graph with the specified action undone
     */
    fun undoAction(actionId: String): GameActionGraph {
        require(completedActionIds.contains(actionId)) { "Action $actionId not completed" }
        require(canUndoAction(actionId)) { "Action $actionId cannot be undone safely" }
        
        return GameActionGraph(
            gameData = gameData,
            completedActionIds = completedActionIds - actionId,
            activeGoals = activeGoals
        ).refreshCaches()
    }
    
    /**
     * Returns a new graph with the specified goals added
     */
    fun addGoals(newGoals: Set<Goal>): GameActionGraph {
        return GameActionGraph(
            gameData = gameData,
            completedActionIds = completedActionIds,
            activeGoals = activeGoals + newGoals
        ).refreshCaches()
    }
    
    /**
     * Returns a new graph with the specified goals removed
     */
    fun removeGoals(goalsToRemove: Set<Goal>): GameActionGraph {
        return GameActionGraph(
            gameData = gameData,
            completedActionIds = completedActionIds,
            activeGoals = activeGoals - goalsToRemove
        ).refreshCaches()
    }
    
    // ============================================================================
    // Conflict Detection
    // ============================================================================
    
    /**
     * Checks what conflicts would be introduced by adding a new goal
     */
    fun checkConflictsWhenAddingGoal(newGoal: Goal): List<Conflict> {
        gameData.actions[newGoal.targetId] 
            ?: return listOf(Conflict(
                severity = ConflictSeverity.MutualExclusion,
                involvedGoals = setOf(newGoal),
                description = "Goal action ${newGoal.targetId} not found"
            ))
        
        val conflicts = mutableListOf<Conflict>()
        
        // Check direct mutual exclusions
        val directConflicts = findDirectConflicts(newGoal)
        conflicts.addAll(directConflicts)
        
        // Check induced conflicts (when new goal makes existing goals conflict)
        val inducedConflicts = findInducedConflicts(newGoal)
        conflicts.addAll(inducedConflicts)
        
        return conflicts
    }
    
    // ============================================================================
    // Multi-Goal Path Planning
    // ============================================================================
    
    /**
     * Returns a minimal action sequence that completes all achievable goals
     */
    fun getUnifiedPathToGoals(): List<GameAction> {
        val allAchievableGoals = readyGoals + achievableGoals
        if (allAchievableGoals.isEmpty()) return emptyList()
        
        return findOptimalUnifiedPath(allAchievableGoals)
    }
    
    // ============================================================================
    // Private Implementation
    // ============================================================================
    
    private fun refreshCaches(): GameActionGraph {
        val newCache = buildPathCache()
        return copy(pathCache = newCache)
    }
    
    private fun buildPathCache(): PathCache {
        val cache = PathCache()
        val preconditionEngine = PreconditionEngine()
        
        for (goal in activeGoals) {
            val pathInfo = calculatePathInfo(goal, preconditionEngine)
            cache.setPathInfo(goal.targetId, pathInfo)
        }
        
        return cache
    }
    
    private fun calculatePathInfo(goal: Goal, preconditionEngine: PreconditionEngine): CachedPathInfo {
        val targetAction = gameData.actions[goal.targetId]
        if (targetAction == null) {
            return CachedPathInfo(
                isAchievable = false,
                pathLength = -1,
                path = null,
                blockingActions = emptyList()
            )
        }
        
        // Already completed
        if (completedActionIds.contains(goal.targetId)) {
            return CachedPathInfo(
                isAchievable = true,
                pathLength = 0,
                path = emptyList(),
                blockingActions = emptyList()
            )
        }
        
        val inventory = preconditionEngine.getInventory(gameData, completedActionIds)
        
        // Check if directly achievable (preconditions already met)
        if (preconditionEngine.evaluate(targetAction.preconditions, completedActionIds, inventory)) {
            return CachedPathInfo(
                isAchievable = true,
                pathLength = 0,
                path = emptyList(),
                blockingActions = emptyList()
            )
        }
        
        // Calculate actual path using BFS
        return calculatePathUsingBFS(targetAction, preconditionEngine)
    }
    
    private fun calculatePathUsingBFS(targetAction: GameAction, preconditionEngine: PreconditionEngine): CachedPathInfo {
        // Check if permanently blocked by forbidden actions
        val forbiddenActions = preconditionEngine.extractForbiddenActions(targetAction.preconditions)
        val blockingActions = forbiddenActions.filter { completedActionIds.contains(it) }
        
        if (blockingActions.isNotEmpty()) {
            return CachedPathInfo(
                isAchievable = false,
                pathLength = -1,
                path = null,
                blockingActions = blockingActions
            )
        }
        
        // BFS to find shortest path
        val queue = mutableListOf<Pair<String, List<GameAction>>>() // (actionId, path to reach it)
        val visited = mutableSetOf<String>()
        val startInventory = preconditionEngine.getInventory(gameData, completedActionIds)
        
        // Initialize with all currently available actions
        for (action in gameData.actions.values) {
            if (!completedActionIds.contains(action.id) && 
                preconditionEngine.evaluate(action.preconditions, completedActionIds, startInventory)) {
                queue.add(action.id to emptyList())
                visited.add(action.id)
            }
        }
        
        while (queue.isNotEmpty()) {
            val (currentActionId, pathToReachIt) = queue.removeAt(0)
            val currentAction = gameData.actions[currentActionId] ?: continue
            
            // Check if this action leads to our target
            if (currentActionId == targetAction.id) {
                return CachedPathInfo(
                    isAchievable = true,
                    pathLength = pathToReachIt.size,
                    path = pathToReachIt,
                    blockingActions = emptyList()
                )
            }
            
            // Simulate performing this action and see what becomes available
            val simulatedCompleted = completedActionIds + currentActionId
            val simulatedInventory = preconditionEngine.getInventory(gameData, simulatedCompleted)
            
            for (nextAction in gameData.actions.values) {
                if (!simulatedCompleted.contains(nextAction.id) && 
                    !visited.contains(nextAction.id) &&
                    preconditionEngine.evaluate(nextAction.preconditions, simulatedCompleted, simulatedInventory)) {
                    
                    queue.add(nextAction.id to (pathToReachIt + currentAction))
                    visited.add(nextAction.id)
                }
            }
        }
        
        // No path found
        return CachedPathInfo(
            isAchievable = false,
            pathLength = -1,
            path = null,
            blockingActions = emptyList()
        )
    }
    
    private fun getGoalsWithPathInfo(): List<GoalWithPathInfo> {
        return activeGoals.mapNotNull { goal ->
            val action = gameData.actions[goal.targetId] ?: return@mapNotNull null
            val pathInfo = pathCache.getPathInfo(goal.targetId) ?: return@mapNotNull null
            
            if (pathInfo.isAchievable && pathInfo.pathLength >= 0) {
                GoalWithPathInfo(
                    goal = goal,
                    action = action,
                    pathLength = pathInfo.pathLength,
                    path = pathInfo.path,
                    blockingActions = emptyList()
                )
            } else null
        }
    }
    
    private fun calculateEnabledGoals(action: GameAction): Map<Goal, List<List<GameAction>>> {
        val result = mutableMapOf<Goal, List<List<GameAction>>>()
        
        for (goal in activeGoals) {
            val pathInfo = pathCache.getPathInfo(goal.targetId)
            if (pathInfo?.isAchievable == true && pathInfo.path != null) {
                if (pathInfo.path.any { it.id == action.id }) {
                    // This action is part of a path to the goal
                    result[goal] = listOf(pathInfo.path)
                }
            }
        }
        
        return result
    }
    
    private fun calculateBlockedGoals(action: GameAction): List<Goal> {
        val preconditionEngine = PreconditionEngine()
        val forbiddenActions = preconditionEngine.extractForbiddenActions(action.preconditions)
        
        // Simple approach: check if this action would directly forbid any goal actions
        return activeGoals.filter { goal ->
            forbiddenActions.contains(goal.targetId) ||
            wouldActionBreakGoal(action, goal)
        }
    }
    
    private fun wouldActionBreakGoal(action: GameAction, goal: Goal): Boolean {
        val goalAction = gameData.actions[goal.targetId] ?: return false
        val preconditionEngine = PreconditionEngine()
        val goalForbiddenActions = preconditionEngine.extractForbiddenActions(goalAction.preconditions)
        
        return goalForbiddenActions.contains(action.id)
    }
    
    private fun canUndoAction(actionId: String): Boolean {
        // An action can be undone if no other completed actions depend on it
        val preconditionEngine = PreconditionEngine()
        
        return completedActionIds.none { otherActionId ->
            if (otherActionId == actionId) return@none false
            
            val otherAction = gameData.actions[otherActionId] ?: return@none false
            val dependencies = preconditionEngine.extractRequiredActions(otherAction.preconditions)
            dependencies.contains(actionId)
        }
    }
    
    private fun findDirectConflicts(newGoal: Goal): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()
        val newGoalAction = gameData.actions[newGoal.targetId] ?: return conflicts
        
        val preconditionEngine = PreconditionEngine()
        val newGoalForbiddenActions = preconditionEngine.extractForbiddenActions(newGoalAction.preconditions)
        
        for (existingGoal in activeGoals) {
            // Check if new goal forbids existing goal's action
            if (newGoalForbiddenActions.contains(existingGoal.targetId)) {
                conflicts.add(Conflict(
                    severity = ConflictSeverity.MutualExclusion,
                    involvedGoals = setOf(newGoal, existingGoal),
                    description = "Goal ${newGoal.description} forbids action ${existingGoal.targetId}"
                ))
            }
            
            // Check if existing goal forbids new goal's action
            val existingAction = gameData.actions[existingGoal.targetId]
            if (existingAction != null) {
                val existingForbiddenActions = preconditionEngine.extractForbiddenActions(existingAction.preconditions)
                if (existingForbiddenActions.contains(newGoal.targetId)) {
                    conflicts.add(Conflict(
                        severity = ConflictSeverity.MutualExclusion,
                        involvedGoals = setOf(newGoal, existingGoal),
                        description = "Existing goal ${existingGoal.description} forbids action ${newGoal.targetId}"
                    ))
                }
            }
        }
        
        return conflicts
    }
    
    private fun findInducedConflicts(newGoal: Goal): List<Conflict> {
        // Simulate adding the new goal and check if previously compatible goals become incompatible
        val simulatedGraph = addGoals(setOf(newGoal))
        val conflicts = mutableListOf<Conflict>()
        
        for (existingGoal in activeGoals) {
            val currentPathInfo = pathCache.getPathInfo(existingGoal.targetId)
            val simulatedPathInfo = simulatedGraph.pathCache.getPathInfo(existingGoal.targetId)
            
            if (currentPathInfo?.isAchievable == true && simulatedPathInfo?.isAchievable == false) {
                conflicts.add(Conflict(
                    severity = ConflictSeverity.InducedConflict,
                    involvedGoals = setOf(newGoal, existingGoal),
                    description = "Adding ${newGoal.description} makes ${existingGoal.description} unachievable"
                ))
            }
        }
        
        return conflicts
    }
    
    private fun findOptimalUnifiedPath(goals: List<GoalWithPathInfo>): List<GameAction> {
        val allRequiredActions = mutableSetOf<GameAction>()
        
        // Collect all actions needed for all goals
        for (goalInfo in goals) {
            goalInfo.path?.let { path ->
                allRequiredActions.addAll(path)
            }
        }
        
        // Add goal actions that are achievable
        for (goalInfo in goals) {
            // Add the goal action itself if it's not completed
            val goalAction = gameData.actions[goalInfo.goal.targetId]
            if (goalAction != null && !completedActionIds.contains(goalAction.id)) {
                allRequiredActions.add(goalAction)
            }
        }
        
        // Remove already completed actions
        val pendingActions = allRequiredActions.filter { !completedActionIds.contains(it.id) }
        
        // Sort by dependency order using topological sort
        return topologicalSort(pendingActions)
    }
    
    private fun topologicalSort(actions: List<GameAction>): List<GameAction> {
        val actionMap = actions.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val graph = mutableMapOf<String, MutableList<String>>()
        
        // Initialize
        for (action in actions) {
            inDegree[action.id] = 0
            graph[action.id] = mutableListOf()
        }
        
        // Build dependency graph - include both action and item dependencies
        val preconditionEngine = PreconditionEngine()
        for (action in actions) {
            // Direct action dependencies
            val requiredActions = preconditionEngine.extractRequiredActions(action.preconditions)
            for (requiredId in requiredActions) {
                if (actionMap.containsKey(requiredId)) {
                    graph[requiredId]?.add(action.id)
                    inDegree[action.id] = inDegree[action.id]!! + 1
                }
            }
            
            // Item dependencies - find actions that provide required items
            val requiredItems = preconditionEngine.extractRequiredItems(action.preconditions)
            for (requiredItemId in requiredItems) {
                // Find actions that provide this item
                for (potentialProvider in actions) {
                    if (potentialProvider.rewards.any { it.itemId == requiredItemId }) {
                        if (actionMap.containsKey(potentialProvider.id) && potentialProvider.id != action.id) {
                            graph[potentialProvider.id]?.add(action.id)
                            inDegree[action.id] = inDegree[action.id]!! + 1
                        }
                    }
                }
            }
        }
        
        // Kahn's algorithm
        val queue = mutableListOf<String>()
        val result = mutableListOf<GameAction>()
        
        // Find actions with no dependencies
        for ((actionId, degree) in inDegree) {
            if (degree == 0) {
                queue.add(actionId)
            }
        }
        
        while (queue.isNotEmpty()) {
            val currentId = queue.removeAt(0)
            val currentAction = actionMap[currentId] ?: continue
            result.add(currentAction)
            
            for (dependentId in graph[currentId] ?: emptyList()) {
                inDegree[dependentId] = inDegree[dependentId]!! - 1
                if (inDegree[dependentId] == 0) {
                    queue.add(dependentId)
                }
            }
        }
        
        return result
    }
}

// ============================================================================
// Supporting Data Classes
// ============================================================================

/**
 * Information about a goal and the path to achieve it
 */
data class GoalWithPathInfo(
    val goal: Goal,
    val action: GameAction,
    val pathLength: Int, // -1 for unachievable, 0 for ready/completed, >0 for achievable
    val path: List<GameAction>?, // null for unachievable
    val blockingActions: List<String> = emptyList() // Actions that prevent this goal
)

/**
 * Information about an available action
 */
data class AvailableAction(
    val action: GameAction,
    val enablesGoals: Map<Goal, List<List<GameAction>>>, // Goals this action helps achieve with paths
    val blocksGoals: List<Goal> // Goals this action would make unachievable
)

/**
 * Information about a completed action
 */
data class CompletedAction(
    val action: GameAction,
    val canUndo: Boolean // Whether this action can be safely undone
)

/**
 * Represents a conflict between goals
 */
data class Conflict(
    val severity: ConflictSeverity,
    val involvedGoals: Set<Goal>,
    val description: String
)

/**
 * Severity levels for conflicts
 */
enum class ConflictSeverity {
    MutualExclusion, // Goals directly conflict via ActionForbidden
    InducedConflict  // Adding new goal makes existing goals unachievable
}

/**
 * Internal cache for path information
 */
private data class PathCache(
    private val pathInfoCache: MutableMap<String, CachedPathInfo> = mutableMapOf()
) {
    fun getPathInfo(goalId: String): CachedPathInfo? = pathInfoCache[goalId]
    
    fun setPathInfo(goalId: String, info: CachedPathInfo) {
        pathInfoCache[goalId] = info
    }
}

/**
 * Cached information about a path to a goal
 */
private data class CachedPathInfo(
    val isAchievable: Boolean,
    val pathLength: Int,
    val path: List<GameAction>?,
    val blockingActions: List<String>
)

// ============================================================================
// Extension Functions for PreconditionEngine
// ============================================================================

private fun PreconditionEngine.extractRequiredActions(expression: PreconditionExpression): Set<String> {
    return when (expression) {
        is PreconditionExpression.ActionRequired -> setOf(expression.actionId)
        is PreconditionExpression.And -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
        is PreconditionExpression.Or -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
        else -> emptySet()
    }
}

private fun PreconditionEngine.extractForbiddenActions(expression: PreconditionExpression): Set<String> {
    return when (expression) {
        is PreconditionExpression.ActionForbidden -> setOf(expression.actionId)
        is PreconditionExpression.And -> expression.expressions.flatMap { extractForbiddenActions(it) }.toSet()
        is PreconditionExpression.Or -> expression.expressions.flatMap { extractForbiddenActions(it) }.toSet()
        else -> emptySet()
    }
}

private fun PreconditionEngine.extractRequiredItems(expression: PreconditionExpression): Set<String> {
    return when (expression) {
        is PreconditionExpression.ItemRequired -> setOf(expression.itemId)
        is PreconditionExpression.And -> expression.expressions.flatMap { extractRequiredItems(it) }.toSet()
        is PreconditionExpression.Or -> expression.expressions.flatMap { extractRequiredItems(it) }.toSet()
        else -> emptySet()
    }
}