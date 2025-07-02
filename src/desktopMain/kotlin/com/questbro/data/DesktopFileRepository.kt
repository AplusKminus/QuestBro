package com.questbro.data

import com.questbro.domain.GameData
import com.questbro.domain.GameRun
import kotlinx.serialization.json.Json
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual class FileRepository {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    actual suspend fun loadGameData(filePath: String): Result<GameData> {
        return try {
            val content = File(filePath).readText()
            val gameData = json.decodeFromString<GameData>(content)
            Result.success(gameData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    actual suspend fun saveGameRun(filePath: String, gameRun: GameRun): Result<Unit> {
        return try {
            val content = json.encodeToString(GameRun.serializer(), gameRun)
            File(filePath).writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    actual suspend fun loadGameRun(filePath: String): Result<GameRun> {
        return try {
            val content = File(filePath).readText()
            val gameRun = json.decodeFromString<GameRun>(content)
            Result.success(gameRun)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    actual suspend fun pickFile(title: String, extensions: List<String>): String? {
        val fileChooser = JFileChooser().apply {
            dialogTitle = title
            if (extensions.isNotEmpty()) {
                fileFilter = FileNameExtensionFilter(
                    "${extensions.joinToString(", ")} files",
                    *extensions.toTypedArray()
                )
            }
        }
        
        return if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile.absolutePath
        } else null
    }
    
    actual suspend fun saveFile(title: String, defaultName: String, extension: String): String? {
        val fileChooser = JFileChooser().apply {
            dialogTitle = title
            selectedFile = File("$defaultName.$extension")
            fileFilter = FileNameExtensionFilter("$extension files", extension)
        }
        
        return if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            if (!file.name.endsWith(".$extension")) {
                "${file.absolutePath}.$extension"
            } else {
                file.absolutePath
            }
        } else null
    }
}