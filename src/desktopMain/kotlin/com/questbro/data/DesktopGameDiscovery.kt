package com.questbro.data

import com.questbro.domain.GameData
import kotlinx.serialization.json.Json
import java.io.File

actual class GameDiscovery {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    actual suspend fun getAvailableGames(): List<AvailableGame> {
        val gamesDir = File("data/games")
        if (!gamesDir.exists() || !gamesDir.isDirectory) {
            return emptyList()
        }
        
        return gamesDir.listFiles { file ->
            file.isFile && file.extension == "json"
        }?.mapNotNull { file ->
            try {
                val content = file.readText()
                val gameData = json.decodeFromString<GameData>(content)
                AvailableGame(
                    id = gameData.gameId,
                    name = gameData.name,
                    filePath = file.absolutePath
                )
            } catch (e: Exception) {
                // Skip invalid game files
                null
            }
        } ?: emptyList()
    }
    
    actual suspend fun loadGameById(gameId: String): Result<GameData> {
        val gamesDir = File("data/games")
        val gameFile = File(gamesDir, "$gameId.json")
        
        return try {
            if (!gameFile.exists()) {
                return Result.failure(Exception("Game file not found: $gameId"))
            }
            
            val content = gameFile.readText()
            val gameData = json.decodeFromString<GameData>(content)
            Result.success(gameData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}