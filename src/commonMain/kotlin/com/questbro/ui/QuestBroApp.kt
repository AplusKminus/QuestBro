package com.questbro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questbro.data.FileRepository
import com.questbro.data.GameRepository
import com.questbro.domain.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestBroApp() {
    val scope = rememberCoroutineScope()
    val fileRepository = remember { FileRepository() }
    val gameRepository = remember { GameRepository(fileRepository) }
    val preconditionEngine = remember { PreconditionEngine() }
    val pathAnalyzer = remember { PathAnalyzer(preconditionEngine) }
    
    var gameData by remember { mutableStateOf<GameData?>(null) }
    var gameRun by remember { mutableStateOf<GameRun?>(null) }
    var actionAnalyses by remember { mutableStateOf<List<ActionAnalysis>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewRunDialog by remember { mutableStateOf(false) }
    var newRunName by remember { mutableStateOf("") }
    
    fun refreshAnalyses() {
        val data = gameData
        val run = gameRun
        if (data != null && run != null) {
            actionAnalyses = pathAnalyzer.analyzeActions(data, run)
        }
    }
    
    LaunchedEffect(gameData, gameRun) {
        refreshAnalyses()
    }
    
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Column {
                Text(
                    text = "QuestBro - RPG Navigation System",
                    style = MaterialTheme.typography.headlineMedium
                )
                
                // Show current game and run info
                val currentGame = gameData
                val currentRun = gameRun
                if (currentGame != null && currentRun != null) {
                    Text(
                        text = "${currentGame.name} - ${currentRun.runName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // File controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            fileRepository.pickFile("Load Game Data", listOf("json"))?.let { filePath ->
                                gameRepository.loadGameData(filePath).fold(
                                    onSuccess = { 
                                        gameData = it
                                        errorMessage = null
                                    },
                                    onFailure = { errorMessage = "Failed to load game data: ${it.message}" }
                                )
                            }
                        }
                    }
                ) {
                    Text("Load Game Data")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            fileRepository.pickFile("Load Run", listOf("json"))?.let { filePath ->
                                gameRepository.loadRun(filePath).fold(
                                    onSuccess = { 
                                        gameRun = it
                                        errorMessage = null
                                    },
                                    onFailure = { errorMessage = "Failed to load run: ${it.message}" }
                                )
                            }
                        }
                    }
                ) {
                    Text("Load Run")
                }
                
                Button(
                    onClick = {
                        if (gameData != null) {
                            newRunName = ""
                            showNewRunDialog = true
                        }
                    },
                    enabled = gameData != null
                ) {
                    Text("New Run")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            val run = gameRun
                            if (run != null) {
                                fileRepository.saveFile("Save Run", run.runName, "json")?.let { filePath ->
                                    gameRepository.saveRun(filePath, run).fold(
                                        onSuccess = { errorMessage = null },
                                        onFailure = { errorMessage = "Failed to save run: ${it.message}" }
                                    )
                                }
                            }
                        }
                    },
                    enabled = gameRun != null
                ) {
                    Text("Save Run")
                }
            }
            
            // Error message
            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Main content
            when {
                gameData == null -> {
                    Card {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Load a game data file to get started",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                gameRun == null -> {
                    Card {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create a new run or load an existing one",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
                else -> {
                    QuestBroContent(
                        gameData = gameData!!,
                        gameRun = gameRun!!,
                        actionAnalyses = actionAnalyses,
                        onActionToggle = { actionId ->
                            val currentRun = gameRun!!
                            gameRun = if (currentRun.completedActions.contains(actionId)) {
                                currentRun.copy(completedActions = currentRun.completedActions - actionId)
                            } else {
                                currentRun.copy(completedActions = currentRun.completedActions + actionId)
                            }
                        },
                        onAddGoal = { goal ->
                            val currentRun = gameRun!!
                            gameRun = currentRun.copy(goals = currentRun.goals + goal)
                        }
                    )
                }
            }
            
            // New Run Dialog
            if (showNewRunDialog) {
                NewRunDialog(
                    currentRunName = newRunName,
                    onRunNameChange = { newRunName = it },
                    onConfirm = {
                        val data = gameData
                        if (data != null && newRunName.isNotBlank()) {
                            gameRun = gameRepository.createNewRun(data, newRunName.trim())
                            showNewRunDialog = false
                        }
                    },
                    onDismiss = { showNewRunDialog = false }
                )
            }
        }
    }
}

@Composable
fun NewRunDialog(
    currentRunName: String,
    onRunNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Run")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a name for your new playthrough:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = currentRunName,
                    onValueChange = onRunNameChange,
                    label = { Text("Run Name") },
                    placeholder = { Text("e.g., First Playthrough, Mage Build, 100% Completion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = currentRunName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}