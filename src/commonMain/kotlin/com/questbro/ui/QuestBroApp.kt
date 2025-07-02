package com.questbro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.questbro.data.*
import com.questbro.domain.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestBroApp() {
    val scope = rememberCoroutineScope()
    val fileRepository = remember { FileRepository() }
    val gameRepository = remember { GameRepository(fileRepository) }
    val gameDiscovery = remember { GameDiscovery() }
    val gameManager = remember { GameManager(gameDiscovery, gameRepository) }
    val preconditionEngine = remember { PreconditionEngine() }
    val pathAnalyzer = remember { PathAnalyzer(preconditionEngine) }
    
    var gameData by remember { mutableStateOf<GameData?>(null) }
    var gameRun by remember { mutableStateOf<GameRun?>(null) }
    var actionAnalyses by remember { mutableStateOf<List<ActionAnalysis>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showNewRunDialog by remember { mutableStateOf(false) }
    var availableGames by remember { mutableStateOf<List<AvailableGame>>(emptyList()) }
    var selectedGameId by remember { mutableStateOf("") }
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
            
            // Main controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            availableGames = gameManager.getAvailableGames()
                            selectedGameId = availableGames.firstOrNull()?.id ?: ""
                            newRunName = ""
                            showNewRunDialog = true
                        }
                    }
                ) {
                    Text("New Run")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            fileRepository.pickFile("Load Run", listOf("json"))?.let { filePath ->
                                gameManager.loadRunWithGameData(filePath).fold(
                                    onSuccess = { (loadedGameData, loadedGameRun) ->
                                        gameData = loadedGameData
                                        gameRun = loadedGameRun
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
            if (gameData != null && gameRun != null) {
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
                    },
                    onRemoveGoal = { goal ->
                        val currentRun = gameRun!!
                        gameRun = currentRun.copy(goals = currentRun.goals.filter { it.id != goal.id })
                    }
                )
            } else {
                Card {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Welcome to QuestBro",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "Create a new run or load an existing one to get started",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            // New Run Dialog
            if (showNewRunDialog) {
                NewRunDialog(
                    availableGames = availableGames,
                    selectedGameId = selectedGameId,
                    onGameSelectionChange = { selectedGameId = it },
                    currentRunName = newRunName,
                    onRunNameChange = { newRunName = it },
                    onConfirm = {
                        scope.launch {
                            if (selectedGameId.isNotBlank() && newRunName.isNotBlank()) {
                                gameManager.createNewRunForGame(selectedGameId, newRunName.trim()).fold(
                                    onSuccess = { (loadedGameData, loadedGameRun) ->
                                        gameData = loadedGameData
                                        gameRun = loadedGameRun
                                        showNewRunDialog = false
                                        errorMessage = null
                                    },
                                    onFailure = { 
                                        errorMessage = "Failed to create new run: ${it.message}"
                                    }
                                )
                            }
                        }
                    },
                    onDismiss = { showNewRunDialog = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRunDialog(
    availableGames: List<AvailableGame>,
    selectedGameId: String,
    onGameSelectionChange: (String) -> Unit,
    currentRunName: String,
    onRunNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Run")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Game selection
                Text(
                    text = "Select a game:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = availableGames.find { it.id == selectedGameId }?.name ?: "Select game...",
                        onValueChange = { },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableGames.forEach { game ->
                            DropdownMenuItem(
                                text = { Text(game.name) },
                                onClick = {
                                    onGameSelectionChange(game.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                // Run name input
                Text(
                    text = "Enter a name for your playthrough:",
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