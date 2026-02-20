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
import org.dam.project.ui.theme.GameTheme

/**
 * Root composable for the application.
 *
 * BUG RAÍZ: El App.kt original usaba rememberCoroutineScope() para lanzar connect().
 * Cuando connect() cambia el estado a Loading, Screen.Login sale de la composición,
 * lo cual cancela el scope de rememberCoroutineScope() -> ForgottenCoroutineScopeException.
 *
 * SOLUCIÓN: appScope usa remember { CoroutineScope(...) } en lugar de rememberCoroutineScope().
 * Este scope NO está ligado al ciclo de vida de ningún Composable y nunca se cancela
 * por recomposición. Se cancela manualmente en DisposableEffect.onDispose.
 */
@Composable
fun App() {
    // *** CLAVE: remember{CoroutineScope} es DISTINTO de rememberCoroutineScope() ***
    // rememberCoroutineScope() -> se cancela cuando el composable sale de la composición
    // remember{CoroutineScope} -> vive mientras App() esté en la composición (toda la app)
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    val gameClient = remember {
        GameClient(NetworkClient(), org.dam.project.client.JvmClientSettings())
    }

    var username by remember { mutableStateOf("Player") }
    val uiState by gameClient.uiState.collectAsState()

    GameTheme {
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
                            // onLogin es (String)->Unit normal, NO suspend.
                            // Lanza connect() en appScope que sobrevive la recomposición.
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
        onDispose {
            gameClient.cleanup()
            appScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
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
