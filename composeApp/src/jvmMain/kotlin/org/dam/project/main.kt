package org.dam.project

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.dam.project.ui.App
import org.dam.project.util.LoggerConfig

fun main() {
    // Setup file logging
    org.dam.project.util.LoggerConfig.setupLogging("client.log")
    
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Tic-Tac-Toe",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp))
    ) {
        App()
    }
}
}