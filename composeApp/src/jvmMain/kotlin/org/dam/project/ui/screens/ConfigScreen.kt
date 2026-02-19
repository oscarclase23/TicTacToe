package org.dam.project.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.network.Difficulty
import org.dam.project.client.AppUiState

@Composable
fun ConfigScreen(gameClient: GameClient) {
    var boardSize by remember { mutableStateOf(3) }
    var totalRounds by remember { mutableStateOf(3) }
    var difficulty by remember { mutableStateOf(Difficulty.EASY) }
    var timeLimit by remember { mutableStateOf(30) }
    var turboMode by remember { mutableStateOf(false) }
    var practiceMode by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val uiState by gameClient.uiState.collectAsState()
        val isPvp = (uiState as? AppUiState.Content)?.currentScreen.let {
            if (it is org.dam.project.client.Screen.Config) it.isPvp else false
        }

        Text(
            text = if (isPvp) "Configuración PVP" else "Configuración PVE",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Board Size Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tamaño del Tablero", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardSizeButton("3x3", 3, boardSize) { boardSize = it }
                    BoardSizeButton("4x4", 4, boardSize) { boardSize = it }
                    BoardSizeButton("5x5", 5, boardSize) { boardSize = it }
                }
            }
        }

        // Number of Rounds
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Número de Rondas (Mejor de...)", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundsButton("3", 3, totalRounds) { totalRounds = it }
                    RoundsButton("5", 5, totalRounds) { totalRounds = it }
                    RoundsButton("7", 7, totalRounds) { totalRounds = it }
                }
            }
        }

        // Difficulty (PVE only)
        if (!isPvp) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dificultad de la IA", style = MaterialTheme.typography.titleMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DifficultyButton("Fácil", Difficulty.EASY, difficulty) { difficulty = it }
                        DifficultyButton("Medio", Difficulty.MEDIUM, difficulty) { difficulty = it }
                        DifficultyButton("Difícil", Difficulty.HARD, difficulty) { difficulty = it }
                    }
                }
            }
        }

        // Time Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Control de Tiempo", style = MaterialTheme.typography.titleMedium)

                // Turbo Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modo Turbo", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Partidas rápidas (10 segundos fijos)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(checked = turboMode, onCheckedChange = { turboMode = it })
                }

                if (!isPvp) {
                    // Practice Mode (PVE only)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Modo Práctica", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Permite deshacer movimientos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(checked = practiceMode, onCheckedChange = { practiceMode = it })
                    }
                }

                HorizontalDivider()

                // Time Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tiempo por turno: ${if (turboMode) "10s (Turbo)" else "${timeLimit}s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (turboMode)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = timeLimit.toFloat(),
                        onValueChange = { timeLimit = it.toInt() },
                        valueRange = 15f..60f,
                        steps = 8,
                        enabled = !turboMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = { gameClient.navigateTo(org.dam.project.client.Screen.Menu) },
                modifier = Modifier.weight(1f)
            ) { Text("Cancelar") }

            Button(
                onClick = {
                    scope.launch {
                        val finalTimeLimit = if (turboMode) 10 else timeLimit
                        if (isPvp) {
                            // FIX: Pass turboMode explicitly to joinQueue
                            gameClient.joinQueue(
                                preferredBoardSize = boardSize,
                                timeLimit = finalTimeLimit,
                                totalRounds = totalRounds,
                                turboMode = turboMode
                            )
                        } else {
                            gameClient.createPVEGame(
                                boardSize = boardSize,
                                winLength = boardSize,
                                totalRounds = totalRounds,
                                difficulty = difficulty,
                                timeLimit = finalTimeLimit,
                                turboMode = turboMode,
                                practiceMode = practiceMode
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text(if (isPvp) "Buscar Oponente" else "Iniciar Partida") }
        }
    }
}

@Composable
private fun RowScope.BoardSizeButton(text: String, size: Int, selected: Int, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(size) },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == size) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) { Text(text) }
}

@Composable
private fun RowScope.RoundsButton(text: String, rounds: Int, selected: Int, onClick: (Int) -> Unit) {
    Button(
        onClick = { onClick(rounds) },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == rounds) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) { Text(text) }
}

@Composable
private fun RowScope.DifficultyButton(text: String, diff: Difficulty, selected: Difficulty, onClick: (Difficulty) -> Unit) {
    Button(
        onClick = { onClick(diff) },
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == diff) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) { Text(text) }
}