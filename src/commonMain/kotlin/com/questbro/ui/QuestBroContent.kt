package com.questbro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questbro.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestBroContent(
    gameData: GameData,
    gameRun: GameRun,
    actionAnalyses: List<ActionAnalysis>,
    onActionToggle: (String) -> Unit,
    onAddGoal: (Goal) -> Unit
) {
    var showCompleted by remember { mutableStateOf(false) }
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
                Text(
                    text = "Goals (${gameRun.goals.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                
                LazyColumn {
                    items(gameRun.goals) { goal ->
                        GoalCard(goal = goal, gameData = gameData)
                    }
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
                
                LazyColumn {
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
        }
    }
}

@Composable
fun GoalCard(goal: Goal, gameData: GameData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = when (goal.type) {
                    GoalType.ACTION -> "Action Goal"
                    GoalType.ITEM -> "Item Goal"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Would break ${analysis.wouldBreakGoals.size} goal(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                if (analysis.requiredForGoals.isNotEmpty()) {
                    Text(
                        text = "Required for ${analysis.requiredForGoals.size} goal(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
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