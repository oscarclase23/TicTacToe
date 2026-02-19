package org.dam.project.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.dam.project.client.AppUiState
import org.dam.project.client.GameClient
import org.dam.project.client.NetworkClient
import org.dam.project.client.Screen
import org.dam.project.ui.screens.ConfigScreen
import org.dam.project.ui.screens.ErrorScreen
import org.dam.project.ui.screens.GameScreen
import org.dam.project.ui.screens.LoginScreen
import org.dam.project.ui.screens.MainMenuScreen
import org.dam.project.ui.screens.RecordsScreen
import org.dam.project.ui.theme.MedievalTheme

/**
 * Root composable for the application.
 * Manages GameClient instance and observes UI state.
 *
 * KEY FIX: We use a stable application-level CoroutineScope (SupervisorJob + Main)
 * instead of rememberCoroutineScope(). The rememberCoroutineScope() scope is tied
 * to the composition and gets cancelled when the composable recomposes or leaves
 * the composition (e.g., when state transitions to Loading). This caused the
 * connect() coroutine to be cancelled mid-handshake, resulting in:
 *   "rememberCoroutineScope left the composition"
 */
@Composable
fun App() {
    // Stable application-level scope that is NOT tied to composition lifecycle.
    // This scope persists across recompositions and state changes.
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    // Create GameClient instance (singleton pattern)
    val gameClient = remember { GameClient(NetworkClient(), org.dam.project.client.JvmClientSettings()) }

    // State for username to allow retries
    var username by remember { mutableStateOf("Player") }

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
                        is Screen.Login -> LoginScreen(
                            gameClient = gameClient,
                            onLogin = { name ->
                                username = name
                                // Use appScope (stable) instead of rememberCoroutineScope()
                                // to avoid cancellation during recomposition
                                appScope.launch {
                                    gameClient.connect("localhost", 5678, name, allowResume = true)
                                }
                            }
                        )
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
                            // Use appScope here too for the same reason
                            appScope.launch {
                                gameClient.connect("localhost", 5678, username, allowResume = true)
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