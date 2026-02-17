package org.dam.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dam.project.client.AppUiState
import org.dam.project.client.GameClient
import org.dam.project.client.NetworkClient
import org.dam.project.client.Screen
import org.dam.project.ui.screens.ConfigScreen
import org.dam.project.ui.screens.ErrorScreen
import org.dam.project.ui.screens.GameScreen
import org.dam.project.ui.screens.MainMenuScreen
import org.dam.project.ui.screens.RecordsScreen
import org.dam.project.ui.theme.MedievalTheme

/**
 * Root composable for the application.
 * Manages GameClient instance and observes UI state.
 */
@Composable
fun App() {
    // Create GameClient instance (singleton pattern)
    val gameClient = remember { GameClient(NetworkClient(), org.dam.project.client.JvmClientSettings()) }
    
    // Create coroutine scope at composable level
    val scope = rememberCoroutineScope()

    // Trigger connection on startup
    LaunchedEffect(Unit) {
        gameClient.connect("localhost", 5678, "Player")
    }
    
    // Observe UI state
    val uiState by gameClient.uiState.collectAsState()
    
    // Apply theme
    MedievalTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // Route to appropriate screen based on state
            when (val state = uiState) {
                is AppUiState.Loading -> {
                    LoadingScreen(state.message)
                }
                is AppUiState.Content -> {
                    when (val screen = state.currentScreen) {
                        is Screen.Menu -> MainMenuScreen(gameClient)
                        is Screen.Records -> RecordsScreen(gameClient)
                        is Screen.Config -> ConfigScreen(gameClient)
                        is Screen.Game -> GameScreen(gameClient, screen.matchId)
                        is Screen.WaitingForMatch -> org.dam.project.ui.screens.WaitingScreen(gameClient)
                    }
                }
                is AppUiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        canRetry = state.canRetry,
                        onRetry = {
                            // Retry connection using scope from composable level
                            scope.launch {
                                gameClient.connect("localhost", 5678, "Player")
                            }
                        },
                        onBack = {
                            gameClient.navigateTo(Screen.Menu)
                        }
                    )
                }
            }
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            gameClient.cleanup()
        }
    }
}

/**
 * Loading screen with circular progress indicator.
 */
@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

