package com.questbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.questbro.domain.*

// Helper functions for conflict detection
private fun extractForbiddenActions(expression: PreconditionExpression): Set<String> {
    return when (expression) {
        is PreconditionExpression.ActionForbidden -> setOf(expression.actionId)
        is PreconditionExpression.And -> expression.expressions.flatMap { extractForbiddenActions(it) }.toSet()
        is PreconditionExpression.Or -> expression.expressions.flatMap { extractForbiddenActions(it) }.toSet()
        else -> emptySet()
    }
}

private fun extractRequiredActions(expression: PreconditionExpression): Set<String> {
    return when (expression) {
        is PreconditionExpression.ActionRequired -> setOf(expression.actionId)
        is PreconditionExpression.And -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
        is PreconditionExpression.Or -> expression.expressions.flatMap { extractRequiredActions(it) }.toSet()
        else -> emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestBroContent(
    gameData: GameData,
    gameRun: GameRun,
    actionAnalyses: List<ActionAnalysis>,
    onActionToggle: (String) -> Unit,
    onAddGoal: (Goal) -> Unit,
    onRemoveGoal: (Goal) -> Unit
) {
    var showCompleted by remember { mutableStateOf(false) }
    var showGoalSearch by remember { mutableStateOf(false) }
    
    // Initialize goal-related objects
    val goalAnalyzer = remember { GoalAnalyzer(PreconditionEngine()) }
    val goalSearch = remember { GoalSearch() }
    val searchableGoals = remember(gameData) { goalSearch.createSearchableGoals(gameData) }
    val analyzedGoals = remember(gameData, gameRun) { goalAnalyzer.analyzeGoals(gameData, gameRun) }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Goals panel
        Card(
            modifier = Modifier.weight(0.3f).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Goals header with add button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Goals (${gameRun.goals.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    IconButton(
                        onClick = { showGoalSearch = true }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Goal")
                    }
                }
                
                if (analyzedGoals.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No goals set\nClick + to add goals",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    CategorizedGoalsList(
                        analyzedGoals = analyzedGoals,
                        onRemoveGoal = onRemoveGoal
                    )
                }
            }
        }
        
        // Actions panel
        Card(
            modifier = Modifier.weight(0.7f).fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with view toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCompleted) 
                            "Completed Actions (${gameRun.completedActions.size})"
                        else 
                            "Available Actions (${actionAnalyses.count { it.isAvailable && !gameRun.completedActions.contains(it.action.id) }})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            onClick = { showCompleted = false },
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Available")
                                }
                            },
                            selected = !showCompleted
                        )
                        FilterChip(
                            onClick = { showCompleted = true },
                            label = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Completed")
                                }
                            },
                            selected = showCompleted
                        )
                    }
                }
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showCompleted) {
                        // Show completed actions with undo option
                        items(actionAnalyses.filter { gameRun.completedActions.contains(it.action.id) }) { analysis ->
                            CompletedActionCard(
                                analysis = analysis,
                                onUndo = { onActionToggle(analysis.action.id) }
                            )
                        }
                    } else {
                        // Show only available, uncompleted actions
                        items(actionAnalyses.filter { it.isAvailable && !gameRun.completedActions.contains(it.action.id) }) { analysis ->
                            AvailableActionCard(
                                analysis = analysis,
                                onComplete = { onActionToggle(analysis.action.id) }
                            )
                        }
                    }
                }
            }
            
            // Goal Search Dialog
            if (showGoalSearch) {
                GoalSearchDialog(
                    searchableGoals = searchableGoals,
                    existingGoals = gameRun.goals,
                    gameData = gameData,
                    gameRun = gameRun,
                    onGoalSelected = { goal ->
                        onAddGoal(goal)
                        showGoalSearch = false
                    },
                    onDismiss = { showGoalSearch = false }
                )
            }
        }
    }
}

@Composable
fun CategorizedGoalsList(
    analyzedGoals: List<AnalyzedGoal>,
    onRemoveGoal: (Goal) -> Unit
) {
    val directlyAchievable = analyzedGoals.filter { it.achievability == GoalAchievability.DIRECTLY_ACHIEVABLE }
    val achievable = analyzedGoals.filter { it.achievability == GoalAchievability.ACHIEVABLE }
    val unachievable = analyzedGoals.filter { it.achievability == GoalAchievability.UNACHIEVABLE }
    val completed = analyzedGoals.filter { it.achievability == GoalAchievability.COMPLETED }
    
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (directlyAchievable.isNotEmpty()) {
            item {
                GoalCategoryHeader(
                    title = "Ready (${directlyAchievable.size})",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(directlyAchievable) { analyzedGoal ->
                AnalyzedGoalCard(
                    analyzedGoal = analyzedGoal,
                    onRemoveGoal = onRemoveGoal
                )
            }
        }
        
        if (achievable.isNotEmpty()) {
            item {
                GoalCategoryHeader(
                    title = "Achievable (${achievable.size})",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(achievable) { analyzedGoal ->
                AnalyzedGoalCard(
                    analyzedGoal = analyzedGoal,
                    onRemoveGoal = onRemoveGoal
                )
            }
        }
        
        if (unachievable.isNotEmpty()) {
            item {
                GoalCategoryHeader(
                    title = "Blocked (${unachievable.size})",
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(unachievable) { analyzedGoal ->
                AnalyzedGoalCard(
                    analyzedGoal = analyzedGoal,
                    onRemoveGoal = onRemoveGoal
                )
            }
        }
        
        if (completed.isNotEmpty()) {
            item {
                GoalCategoryHeader(
                    title = "Completed (${completed.size})",
                    color = Color(0xFF4CAF50) // Green color
                )
            }
            items(completed) { analyzedGoal ->
                AnalyzedGoalCard(
                    analyzedGoal = analyzedGoal,
                    onRemoveGoal = onRemoveGoal
                )
            }
        }
    }
}

@Composable
fun GoalCategoryHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun AnalyzedGoalCard(
    analyzedGoal: AnalyzedGoal,
    onRemoveGoal: (Goal) -> Unit
) {
    val goal = analyzedGoal.goal
    val borderColor = when (analyzedGoal.achievability) {
        GoalAchievability.DIRECTLY_ACHIEVABLE -> MaterialTheme.colorScheme.primary
        GoalAchievability.ACHIEVABLE -> MaterialTheme.colorScheme.secondary
        GoalAchievability.UNACHIEVABLE -> MaterialTheme.colorScheme.error
        GoalAchievability.COMPLETED -> Color(0xFF4CAF50) // Green color
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (analyzedGoal.achievability) {
                GoalAchievability.DIRECTLY_ACHIEVABLE -> MaterialTheme.colorScheme.primaryContainer
                GoalAchievability.ACHIEVABLE -> MaterialTheme.colorScheme.secondaryContainer
                GoalAchievability.UNACHIEVABLE -> MaterialTheme.colorScheme.errorContainer
                GoalAchievability.COMPLETED -> Color(0xFFE8F5E8) // Light green background
            }
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = goal.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            
                // Show required actions for achievable goals
                if (analyzedGoal.achievability == GoalAchievability.ACHIEVABLE && analyzedGoal.requiredActions.isNotEmpty()) {
                    Text(
                        text = "Requires: ${analyzedGoal.requiredActions.size} action(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                // Show unachievable status
                if (analyzedGoal.achievability == GoalAchievability.UNACHIEVABLE) {
                    Text(
                        text = "Unachievable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                // Show completed status
                if (analyzedGoal.achievability == GoalAchievability.COMPLETED) {
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50) // Green color
                    )
                }
            }
            
            // Remove button
            IconButton(
                onClick = { onRemoveGoal(goal) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove goal",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun GoalSearchDialog(
    searchableGoals: List<SearchableGoal>,
    existingGoals: List<Goal>,
    gameData: GameData,
    gameRun: GameRun,
    onGoalSelected: (Goal) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val goalSearch = remember { GoalSearch() }
    val goalAnalyzer = remember { GoalAnalyzer(PreconditionEngine()) }
    val existingTargetIds = remember(existingGoals) {
        existingGoals.map { it.targetId }.toSet()
    }
    
    data class AnalyzedSearchableGoal(
        val searchableGoal: SearchableGoal,
        val isCompatible: Boolean,
        val conflictingGoals: List<Goal>
    )
    
    val searchResults = remember(searchQuery, searchableGoals, existingTargetIds, gameRun) {
        val filteredGoals = searchableGoals.filter { searchableGoal ->
            !existingTargetIds.contains(searchableGoal.targetId)
        }
        val searchedGoals = goalSearch.searchGoals(filteredGoals, searchQuery)
        
        // Analyze compatibility and conflicts for each goal
        searchedGoals.map { searchableGoal ->
            val tempGoal = goalSearch.createGoalFromSearchable(searchableGoal)
            val analyzedGoal = goalAnalyzer.analyzeGoal(
                tempGoal,
                gameData,
                gameRun.completedActions,
                PreconditionEngine().getInventory(gameData, gameRun.completedActions)
            )
            
            // Check for conflicts by analyzing which existing goals would be affected when adding this new goal
            val conflictingGoals = mutableListOf<Goal>()
            
            // Use PathAnalyzer to determine which actions would break existing goals when the new goal is present
            val simulatedGameRun = gameRun.copy(goals = gameRun.goals + tempGoal)
            val pathAnalyzer = PathAnalyzer(PreconditionEngine())
            val actionAnalyses = pathAnalyzer.analyzeActions(gameData, simulatedGameRun)
            
            // Find actions that would break existing goals and check if they're required for the new goal
            for (analysis in actionAnalyses) {
                if (analysis.isAvailable) {
                    // Check if this action would break any existing goals
                    val brokenExistingGoals = analysis.wouldBreakGoals.filter { brokenGoal ->
                        // Only count goals that were in the original game run (not the new goal itself)
                        gameRun.goals.any { it.targetId == brokenGoal.targetId }
                    }
                    
                    // Check if this action is required for the new goal
                    val isRequiredForNewGoal = analysis.requiredForGoals.any { requiredGoal ->
                        requiredGoal.targetId == tempGoal.targetId
                    }
                    
                    // If the action is required for the new goal and breaks existing goals, those are conflicts
                    if (isRequiredForNewGoal && brokenExistingGoals.isNotEmpty()) {
                        conflictingGoals.addAll(brokenExistingGoals)
                    }
                }
            }
            
            AnalyzedSearchableGoal(
                searchableGoal = searchableGoal,
                isCompatible = analyzedGoal.achievability != GoalAchievability.UNACHIEVABLE,
                conflictingGoals = conflictingGoals.distinctBy { it.targetId }
            ).also {
                // Debug logging for conflict detection
                if (conflictingGoals.isNotEmpty()) {
                    println("DEBUG: Goal '${searchableGoal.name}' conflicts with: ${conflictingGoals.map { it.description }}")
                }
            }
        }
    }
    
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Goal") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search goals...") },
                    placeholder = { Text("e.g., boss, sword, quest") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(searchResults) { analyzedSearchableGoal ->
                        val searchableGoal = analyzedSearchableGoal.searchableGoal
                        val isCompatible = analyzedSearchableGoal.isCompatible
                        val conflictingGoals = analyzedSearchableGoal.conflictingGoals
                        val hasConflicts = conflictingGoals.isNotEmpty()
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isCompatible && !hasConflicts) Modifier.clickable {
                                        onGoalSelected(goalSearch.createGoalFromSearchable(searchableGoal))
                                    } else Modifier
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    !isCompatible -> MaterialTheme.colorScheme.surfaceVariant
                                    hasConflicts -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                    else -> MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = if (hasConflicts && isCompatible) {
                                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            } else null
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = searchableGoal.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCompatible) 
                                            MaterialTheme.colorScheme.onSurface 
                                        else 
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = searchableGoal.category,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCompatible) 
                                            MaterialTheme.colorScheme.onSurfaceVariant 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    
                                    if (!isCompatible) {
                                        Text(
                                            text = "Incompatible with current run",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else if (hasConflicts) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = "Conflicts",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "Would conflict with ${conflictingGoals.size} goal(s):",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            
                                            // List conflicting goals
                                            conflictingGoals.forEach { goal ->
                                                Text(
                                                    text = "• ${goal.description}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                if (!isCompatible || hasConflicts) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = if (!isCompatible) "Incompatible" else "Has conflicts",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@Composable
fun AvailableActionCard(
    analysis: ActionAnalysis,
    onComplete: () -> Unit
) {
    val action = analysis.action
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (analysis.wouldBreakGoals.isNotEmpty()) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = false,
                onCheckedChange = { onComplete() }
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = action.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                action.completionCriteria?.let { criteria ->
                    Text(
                        text = criteria,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (action.rewards.isNotEmpty()) {
                    Text(
                        text = "Rewards: ${action.rewards.joinToString(", ") { it.description }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                if (analysis.wouldBreakGoals.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Would block ${analysis.wouldBreakGoals.size} goal(s):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        // List the specific goals that would be blocked
                        analysis.wouldBreakGoals.forEach { goal ->
                            Text(
                                text = "• ${goal.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
                
                if (analysis.requiredForGoals.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Required",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Required for ${analysis.requiredForGoals.size} goal(s):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // List the specific goals this action is required for
                        analysis.requiredForGoals.forEach { goal ->
                            Text(
                                text = "• ${goal.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            
            Text(
                text = action.category.name,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun CompletedActionCard(
    analysis: ActionAnalysis,
    onUndo: () -> Unit
) {
    val action = analysis.action
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onUndo,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Undo completion",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = action.name,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                if (action.rewards.isNotEmpty()) {
                    Text(
                        text = "Obtained: ${action.rewards.joinToString(", ") { it.description }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Text(
                text = action.category.name,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}