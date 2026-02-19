package org.dam.project.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.client.Screen
import org.dam.project.network.GameState
import org.dam.project.network.Position
import org.dam.project.network.RoundEnd
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource

@Composable
fun GameScreen(gameClient: GameClient, matchId: String) {
    val scope = rememberCoroutineScope()

    val gameState by gameClient.currentGameState.collectAsState()
    val roundEndResult by gameClient.roundEndResult.collectAsState()
    val timeRemaining by gameClient.timeRemaining.collectAsState()
    val isOpponentDisconnected by gameClient.isOpponentDisconnected.collectAsState()
    val isConnectionLost by gameClient.isConnectionLost.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ── LEFT: Board + status ─────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (gameState != null) {
                    val playerId = gameClient.getPlayerId()
                    val playerSymbol = when (playerId) {
                        gameState!!.playerXId -> "X"
                        gameState!!.playerOId -> "O"
                        else -> null
                    }
                    val isPlayerTurn = playerSymbol == gameState!!.currentPlayer
                    val isAIGame = gameState!!.playerXId == "AI" || gameState!!.playerOId == "AI"

                    // Status text
                    val statusText = when {
                        isOpponentDisconnected -> "⚠️ Oponente desconectado"
                        isConnectionLost       -> "🔴 Conexión perdida"
                        isPlayerTurn           -> "🎮 Tu turno"
                        isAIGame               -> "🤖 IA pensando..."
                        else                   -> "⏳ Turno de ${gameClient.opponentName ?: "Oponente"}"
                    }
                    val statusColor = when {
                        isPlayerTurn -> MaterialTheme.colorScheme.primary
                        else         -> MaterialTheme.colorScheme.secondary
                    }

                    // Round indicator
                    Text(
                        text = "Ronda ${gameState!!.currentRound} / ${gameState!!.totalRounds}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Timer bar (always visible when there's a time limit)
                    TimerBar(
                        timeRemaining = timeRemaining,
                        timeLimit = gameState!!.timeLimit,
                        isPlayerTurn = isPlayerTurn
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Score display above board
                    ScoreRow(
                        gameState = gameState!!,
                        gameClient = gameClient,
                        playerSymbol = playerSymbol
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Board
                    BoardGrid(
                        gameState = gameState!!,
                        isPlayerTurn = isPlayerTurn && !isOpponentDisconnected && !isConnectionLost,
                        roundEndResult = roundEndResult,
                        onCellClick = { row, col ->
                            if (isPlayerTurn && !isOpponentDisconnected && !isConnectionLost) {
                                scope.launch { gameClient.makeMove(row, col) }
                            }
                        }
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando partida...", color = MaterialTheme.colorScheme.onBackground)
                }
            }

            // ── RIGHT: Info panel ────────────────────────────────────────
            InfoPanel(
                gameState = gameState,
                gameClient = gameClient,
                onLeave = {
                    scope.launch {
                        gameClient.surrenderGame()
                        gameClient.navigateTo(Screen.Menu)
                    }
                },
                onUndo = {
                    scope.launch { gameClient.requestUndo() }
                }
            )
        }

        // ── OVERLAYS ──────────────────────────────────────────────────────

        if (isConnectionLost) {
            ConnectionOverlay(
                title = "🔴 Conexión perdida",
                subtitle = "Intentando reconectar...",
                color = MaterialTheme.colorScheme.error,
                zIndex = 2000f
            )
        }

        if (isOpponentDisconnected && !isConnectionLost) {
            ConnectionOverlay(
                title = "⚠️ Oponente desconectado",
                subtitle = "Esperando a que regrese...",
                color = MaterialTheme.colorScheme.secondary,
                zIndex = 1500f
            )
        }

        if (roundEndResult != null && gameState != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(1000f)) {
                RoundEndOverlay(
                    roundEnd = roundEndResult!!,
                    gameState = gameState!!,
                    gameClient = gameClient
                )
            }
        }
    }
}

// ── Timer Bar ───────────────────────────────────────────────────────────────

@Composable
private fun TimerBar(timeRemaining: Int?, timeLimit: Int, isPlayerTurn: Boolean) {
    if (timeRemaining == null) return

    val fraction = (timeRemaining.toFloat() / timeLimit).coerceIn(0f, 1f)
    val barColor = when {
        timeRemaining <= 5  -> Color(0xFFFF0000)
        timeRemaining <= 10 -> Color(0xFFFF9800)
        else                -> Color(0xFF4CAF50)
    }

    val label = if (isPlayerTurn) "⏱️ Tu tiempo: ${timeRemaining}s" else "⏳ Tiempo rival: ${timeRemaining}s"

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = barColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(barColor)
            )
        }
    }
}

// ── Score Row ──────────────────────────────────────────────────────────────

@Composable
private fun ScoreRow(gameState: GameState, gameClient: GameClient, playerSymbol: String?) {
    val opponentSymbol = if (playerSymbol == "X") "O" else "X"
    val isAIGame = gameState.playerXId == "AI" || gameState.playerOId == "AI"
    val opponentLabel = if (isAIGame) "IA" else (gameClient.opponentName ?: "Oponente")
    val playerLabel = gameClient.getPlayerName() ?: "Tú"

    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(playerLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(
                "${gameState.scores[playerSymbol] ?: 0}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text("(${playerSymbol})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
        Text("vs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(opponentLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Text(
                "${gameState.scores[opponentSymbol] ?: 0}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text("(${opponentSymbol})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}

// ── Board Grid ─────────────────────────────────────────────────────────────

@Composable
private fun BoardGrid(
    gameState: GameState,
    isPlayerTurn: Boolean,
    roundEndResult: RoundEnd?,
    onCellClick: (Int, Int) -> Unit
) {
    val boardSize = gameState.boardSize
    val cellSize = when (boardSize) {
        3 -> 110.dp
        4 -> 90.dp
        else -> 72.dp
    }
    val spacing = 4.dp

    val isDraw = roundEndResult?.isDraw == true
    val shakeOffset by animateFloatAsState(
        targetValue = if (isDraw) 1f else 0f,
        animationSpec = repeatable(4, tween(80), RepeatMode.Reverse),
        label = "shake"
    )

    val winningLine = roundEndResult?.winningLine
    val lineProgress by animateFloatAsState(
        targetValue = if (winningLine != null) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "winLine"
    )

    Box {
        Column(
            modifier = Modifier.rotate(shakeOffset * 3f),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (row in 0 until boardSize) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (col in 0 until boardSize) {
                        val symbol = gameState.board.getOrNull(row)?.getOrNull(col)
                        val isWinCell = winningLine?.any { it.row == row && it.col == col } == true
                        BoardCell(
                            symbol = symbol,
                            isClickable = isPlayerTurn,
                            isWinCell = isWinCell,
                            onClick = { onCellClick(row, col) },
                            modifier = Modifier.size(cellSize)
                        )
                    }
                }
            }
        }

        if (winningLine != null && lineProgress > 0f) {
            WinningLineOverlay(
                positions = winningLine,
                boardSize = boardSize,
                cellSize = cellSize,
                spacing = spacing,
                progress = lineProgress,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun WinningLineOverlay(
    positions: List<Position>,
    boardSize: Int,
    cellSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (positions.size >= 2) {
            val cellSizePx = cellSize.toPx()
            val spacingPx = spacing.toPx()
            val offset = cellSizePx / 2
            val start = positions.first()
            val end = positions.last()
            val startX = start.col * (cellSizePx + spacingPx) + offset
            val startY = start.row * (cellSizePx + spacingPx) + offset
            val endX = end.col * (cellSizePx + spacingPx) + offset
            val endY = end.row * (cellSizePx + spacingPx) + offset

            drawLine(
                color = Color(0xFFFFD700),
                start = Offset(startX, startY),
                end = Offset(startX + (endX - startX) * progress, startY + (endY - startY) * progress),
                strokeWidth = 8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 4f), 0f)
            )
        }
    }
}

@Composable
private fun BoardCell(
    symbol: String?,
    isClickable: Boolean,
    isWinCell: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (symbol.isNullOrEmpty()) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f),
        label = "symbolScale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isWinCell) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isWinCell) 3.dp else 2.dp,
                color = if (isWinCell) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = symbol.isNullOrEmpty() && isClickable) {
                println("[BoardCell] Cell clicked! symbol='$symbol'")
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (!symbol.isNullOrEmpty() && (symbol == "X" || symbol == "O")) {
            Image(
                painter = painterResource(GameAssets.getSymbolDrawable(symbol)),
                contentDescription = symbol,
                modifier = Modifier
                    .fillMaxSize(0.80f)
                    .scale(scale),
                contentScale = ContentScale.Fit
            )
        }
    }
}

// ── Info Panel ─────────────────────────────────────────────────────────────

@Composable
private fun InfoPanel(
    gameState: GameState?,
    gameClient: GameClient,
    onLeave: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        modifier = Modifier.width(280.dp).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "📋 Info de Partida",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()

                gameState?.let { state ->
                    // Difficulty / Mode info
                    val isAI = state.playerXId == "AI" || state.playerOId == "AI"
                    Text(
                        if (isAI) "🤖 Modo PVE" else "⚔️ Modo PVP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Tablero ${state.boardSize}×${state.boardSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        "⏱️ ${state.timeLimit}s por turno",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (state.practiceMode) {
                        Text(
                            "📝 Modo práctica",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                HorizontalDivider()

                // Move history
                MoveHistoryPanel(movesLog = gameState?.movesLog ?: emptyList())
            }

            // Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gameState?.practiceMode == true) {
                    OutlinedButton(
                        onClick = onUndo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("↩️ Deshacer movimiento")
                    }
                }

                Button(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🚪 Abandonar partida")
                }
            }
        }
    }
}

// ── Move History Panel ─────────────────────────────────────────────────────

@Composable
private fun MoveHistoryPanel(movesLog: List<String>) {
    Column {
        Text(
            "Historial de movimientos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (movesLog.isEmpty()) {
            Text(
                "Sin movimientos aún",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        } else {
            val listState = rememberLazyListState()

            // Auto-scroll to last item
            LaunchedEffect(movesLog.size) {
                if (movesLog.isNotEmpty()) {
                    listState.animateScrollToItem(movesLog.size - 1)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(movesLog) { _, move ->
                    val isX = move.contains("X →")
                    Text(
                        text = move,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isX) Color(0xFF64B5F6) else Color(0xFFEF9A9A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isX) Color(0xFF64B5F6).copy(alpha = 0.08f)
                                else Color(0xFFEF9A9A).copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ── Connection Overlays ────────────────────────────────────────────────────

@Composable
private fun ConnectionOverlay(title: String, subtitle: String, color: Color, zIndex: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .zIndex(zIndex),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = color, modifier = Modifier.size(56.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

// ── Round End Overlay ──────────────────────────────────────────────────────

@Composable
private fun RoundEndOverlay(roundEnd: RoundEnd, gameState: GameState, gameClient: GameClient) {
    val playerId = gameClient.getPlayerId()
    val playerSymbol = when (playerId) {
        gameState.playerXId -> "X"
        gameState.playerOId -> "O"
        else -> null
    }

    val result = when {
        roundEnd.isDraw -> "draw"
        roundEnd.winner == playerSymbol -> "win"
        else -> "lose"
    }

    val (imageRes, message, bgColor) = when (result) {
        "win"  -> Triple(GameAssets.winIcon,  "🏆 ¡Ganaste!",  Color(0xFF4CAF50))
        "lose" -> Triple(GameAssets.loseIcon, "💀 Perdiste",   MaterialTheme.colorScheme.error)
        else   -> Triple(GameAssets.drawIcon, "🤝 Empate",     Color(0xFFFF9800))
    }

    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "overlayAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f * alpha))
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.width(380.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bgColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = message,
                        modifier = Modifier.size(56.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.headlineMedium,
                    color = bgColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (roundEnd.reason != null) {
                    Text(
                        text = roundEnd.reason,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = "Ronda ${gameState.currentRound} de ${gameState.totalRounds}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                // Scores
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${gameState.scores["X"] ?: 0}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("–", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(
                        "${gameState.scores["O"] ?: 0}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "La siguiente ronda comenzará pronto...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}