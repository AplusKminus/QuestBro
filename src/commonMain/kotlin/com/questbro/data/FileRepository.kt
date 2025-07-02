package com.questbro.data

import com.questbro.domain.GameData
import com.questbro.domain.GameRun
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

expect class FileRepository() {
    suspend fun loadGameData(filePath: String): Result<GameData>
    suspend fun saveGameRun(filePath: String, gameRun: GameRun): Result<Unit>
    suspend fun loadGameRun(filePath: String): Result<GameRun>
    suspend fun pickFile(title: String, extensions: List<String>): String?
    suspend fun saveFile(title: String, defaultName: String, extension: String): String?
}

class GameRepository(private val fileRepository: FileRepository) {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    suspend fun loadGameData(filePath: String): Result<GameData> {
        return fileRepository.loadGameData(filePath)
    }
    
    fun createNewRun(gameData: GameData, runName: String): GameRun {
        val now = Clock.System.now().toEpochMilliseconds()
        return GameRun(
            gameId = gameData.gameId,
            gameVersion = gameData.version,
            runName = runName,
            completedActions = emptySet(),
            goals = emptyList(),
            createdAt = now,
            lastModified = now
        )
    }
    
    suspend fun updateRun(gameRun: GameRun): GameRun {
        return gameRun.copy(lastModified = Clock.System.now().toEpochMilliseconds())
    }
    
    suspend fun saveRun(filePath: String, gameRun: GameRun): Result<Unit> {
        return fileRepository.saveGameRun(filePath, gameRun)
    }
    
    suspend fun loadRun(filePath: String): Result<GameRun> {
        return fileRepository.loadGameRun(filePath)
    }
}