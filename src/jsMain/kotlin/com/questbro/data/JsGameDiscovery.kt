package com.questbro.data

import com.questbro.domain.GameData

actual class GameDiscovery {
    
    actual suspend fun getAvailableGames(): List<AvailableGame> {
        // For web, return a hardcoded list - in a real app this would fetch from a server
        return listOf(
            AvailableGame(
                id = "elden-ring",
                name = "Elden Ring",
                filePath = "games/elden-ring.json"
            )
        )
    }
    
    actual suspend fun loadGameById(gameId: String): Result<GameData> {
        // For web, return a basic demo game - in a real app this would fetch from a server
        return try {
            val gameData = GameData(
                gameId = gameId,
                name = "Demo Game",
                version = "1.0.0",
                actions = emptyMap(),
                items = emptyMap()
            )
            Result.success(gameData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}