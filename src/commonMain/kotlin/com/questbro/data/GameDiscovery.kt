package com.questbro.data

import com.questbro.domain.GameData

data class AvailableGame(
    val id: String,
    val name: String,
    val filePath: String
)

expect class GameDiscovery() {
    suspend fun getAvailableGames(): List<AvailableGame>
    suspend fun loadGameById(gameId: String): Result<GameData>
}

class GameManager(
    private val gameDiscovery: GameDiscovery,
    private val gameRepository: GameRepository
) {
    
    suspend fun getAvailableGames(): List<AvailableGame> {
        return gameDiscovery.getAvailableGames()
    }
    
    suspend fun createNewRunForGame(gameId: String, runName: String): Result<Pair<GameData, com.questbro.domain.GameRun>> {
        return gameDiscovery.loadGameById(gameId).fold(
            onSuccess = { gameData ->
                val gameRun = gameRepository.createNewRun(gameData, runName)
                Result.success(Pair(gameData, gameRun))
            },
            onFailure = { Result.failure(it) }
        )
    }
    
    suspend fun loadRunWithGameData(runFilePath: String): Result<Pair<GameData, com.questbro.domain.GameRun>> {
        return gameRepository.loadRun(runFilePath).fold(
            onSuccess = { gameRun ->
                gameDiscovery.loadGameById(gameRun.gameId).fold(
                    onSuccess = { gameData ->
                        Result.success(Pair(gameData, gameRun))
                    },
                    onFailure = { 
                        Result.failure(Exception("Failed to load game data for ${gameRun.gameId}: ${it.message}"))
                    }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }
}