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
 *
 * THE BUG: connect() was launched from rememberCoroutineScope() inside LoginScreen.
 * When connect() changes uiState to Loading, Screen.Login leaves composition,
 * which cancels LoginScreen's scope, killing the connect() coroutine mid-handshake.
 *
 * THE FIX: Use a stable CoroutineScope created with remember{} (NOT rememberCoroutineScope).
 * Pass a plain (String)->Unit lambda to LoginScreen so it never needs its own
 * coroutine scope for connecting.
 */
@Composable
fun App() {
    // Stable scope - lives as long as App composable, NOT cancelled on recomposition.
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val gameClient = remember {
        GameClient(NetworkClient(), org.dam.project.client.JvmClientSettings())
    }

    var username by remember { mutableStateOf("Player") }
    val uiState by gameClient.uiState.collectAsState()

    MedievalTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = uiState) {
                is AppUiState.Loading -> LoadingScreen(state.message)

                is AppUiState.Content -> {
                    when (val screen = state.currentScreen) {
                        is Screen.Login -> LoginScreen(
                            gameClient = gameClient,
                            // Plain lambda - NOT suspend. Launches in stable appScope.
                            // LoginScreen does NOT need its own scope for connecting.
                            onLogin = { name ->
                                username = name
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

                is AppUiState.Error -> ErrorScreen(
                    message = state.message,
                    canRetry = state.canRetry,
                    onRetry = {
                        appScope.launch {
                            gameClient.connect("localhost", 5678, username, allowResume = true)
                        }
                    },
                    onBack = { gameClient.navigateTo(Screen.Menu) }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { gameClient.cleanup() }
    }
}

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