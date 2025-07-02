package com.questbro.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.questbro.ui.QuestBroApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "QuestBro - RPG Navigation System"
    ) {
        QuestBroApp()
    }
}