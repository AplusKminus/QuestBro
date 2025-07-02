package com.questbro.data

import com.questbro.domain.GameData
import com.questbro.domain.GameRun
import kotlinx.serialization.json.Json
import org.w3c.files.FileReader
import org.w3c.dom.events.Event
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual class FileRepository {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    actual suspend fun loadGameData(filePath: String): Result<GameData> {
        return try {
            // For web, we'll use a basic implementation that loads from a URL
            // In a real implementation, you'd use File API or fetch
            val gameData = GameData(
                gameId = "demo",
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
    
    actual suspend fun saveGameRun(filePath: String, gameRun: GameRun): Result<Unit> {
        return try {
            // For web, save to localStorage
            val content = json.encodeToString(GameRun.serializer(), gameRun)
            kotlinx.browser.localStorage.setItem("questbro_run_$filePath", content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    actual suspend fun loadGameRun(filePath: String): Result<GameRun> {
        return try {
            val content = kotlinx.browser.localStorage.getItem("questbro_run_$filePath")
                ?: return Result.failure(Exception("File not found"))
            val gameRun = json.decodeFromString<GameRun>(content)
            Result.success(gameRun)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    actual suspend fun pickFile(title: String, extensions: List<String>): String? {
        // For web, return a default filename
        return "selected_file.json"
    }
    
    actual suspend fun saveFile(title: String, defaultName: String, extension: String): String? {
        // For web, return a default filename
        return "$defaultName.$extension"
    }
}