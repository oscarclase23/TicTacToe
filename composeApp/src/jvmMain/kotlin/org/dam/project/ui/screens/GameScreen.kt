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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.client.Screen
import org.dam.project.network.GameState
import org.dam.project.network.Position
import org.dam.project.network.RoundEnd
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource

// ── Main Screen ────────────────────────────────────────────────────────────

@Composable
fun GameScreen(gameClient: GameClient, matchId: String) {
    val scope = rememberCoroutineScope()

    val gameState by gameClient.currentGameState.collectAsState()
    val roundEndResult by gameClient.roundEndResult.collectAsState()
    val timeRemaining by gameClient.timeRemaining.collectAsState()
    val isOpponentDisconnected by gameClient.isOpponentDisconnected.collectAsState()
    val isConnectionLost by gameClient.isConnectionLost.collectAsState()

    // Determine if the current roundEndResult is a match-end overlay
    val isMatchEnd = roundEndResult?.reason?.contains("Fin del Match") == true

    // Delay the overlay card so the winning line has time to animate fully before
    // the result card appears. roundEndResult arrives → line starts drawing (700ms).
    // We wait 900ms so the player clearly sees the line, then show the card.
    var showOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(roundEndResult) {
        if (roundEndResult != null) {
            delay(900)
            showOverlay = true
        } else {
            showOverlay = false
        }
    }

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

                    TimerBar(
                        timeRemaining = timeRemaining,
                        timeLimit = gameState!!.timeLimit,
                        isPlayerTurn = isPlayerTurn
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ScoreRow(
                        gameState = gameState!!,
                        gameClient = gameClient,
                        playerSymbol = playerSymbol
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    BoardGrid(
                        gameState = gameState!!,
                        // Disable board interaction while any overlay is showing
                        isPlayerTurn = isPlayerTurn &&
                                !isOpponentDisconnected &&
                                !isConnectionLost &&
                                roundEndResult == null,
                        roundEndResult = roundEndResult,
                        onCellClick = { row, col ->
                            if (isPlayerTurn && !isOpponentDisconnected && !isConnectionLost && roundEndResult == null) {
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

        // ── OVERLAYS (in z-order) ─────────────────────────────────────────

        if (isConnectionLost) {
            ConnectionOverlay(
                title = "🔴 Conexión perdida",
                subtitle = "Intentando reconectar...",
                color = MaterialTheme.colorScheme.error,
                zIndex = 3000f
            )
        }

        if (isOpponentDisconnected && !isConnectionLost) {
            ConnectionOverlay(
                title = "⚠️ Oponente desconectado",
                subtitle = "Esperando a que regrese...",
                color = MaterialTheme.colorScheme.secondary,
                zIndex = 2500f
            )
        }

        // Round-end overlay (not match-end)
        if (showOverlay && roundEndResult != null && gameState != null && !isMatchEnd) {
            Box(modifier = Modifier.fillMaxSize().zIndex(1000f)) {
                RoundEndOverlay(
                    roundEnd = roundEndResult!!,
                    gameState = gameState!!,
                    gameClient = gameClient,
                    isMatchEnd = false
                )
            }
        }

        // Match-end overlay (higher z than round-end)
        if (showOverlay && roundEndResult != null && gameState != null && isMatchEnd) {
            Box(modifier = Modifier.fillMaxSize().zIndex(2000f)) {
                MatchEndOverlay(
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
    if (timeRemaining == null) {
        // Show empty placeholder to avoid layout jump
        Spacer(modifier = Modifier.height(36.dp))
        return
    }

    val fraction = (timeRemaining.toFloat() / timeLimit).coerceIn(0f, 1f)
    val barColor = when {
        timeRemaining <= 5  -> Color(0xFFFF0000)
        timeRemaining <= 10 -> Color(0xFFFF9800)
        else                -> Color(0xFF4CAF50)
    }

    // Animate bar color transition
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(800),
        label = "timerFraction"
    )

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
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(10.dp)
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
            Text(
                playerLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                "${gameState.scores[playerSymbol] ?: 0}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "(${playerSymbol})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Text(
            "vs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                opponentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                "${gameState.scores[opponentSymbol] ?: 0}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "(${opponentSymbol})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
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
        3    -> 110.dp
        4    -> 90.dp
        else -> 72.dp
    }
    val spacing = 4.dp

    val isDraw = roundEndResult?.isDraw == true

    // Shake animation on draw
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(isDraw) {
        if (isDraw) {
            repeat(5) {
                shakeAnim.animateTo(6f, animationSpec = tween(60))
                shakeAnim.animateTo(-6f, animationSpec = tween(60))
            }
            shakeAnim.animateTo(0f, animationSpec = tween(60))
        }
    }

    val winningLine = roundEndResult?.winningLine
    val lineProgress by animateFloatAsState(
        targetValue = if (winningLine != null) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "winLine"
    )

    Box {
        Column(
            modifier = Modifier.rotate(shakeAnim.value),
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
    cellSize: androidx.compose.ui.unit.Dp,
    spacing: androidx.compose.ui.unit.Dp,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (positions.size >= 2) {
            val cellSizePx = cellSize.toPx()
            val spacingPx  = spacing.toPx()
            val offset     = cellSizePx / 2
            val start      = positions.first()
            val end        = positions.last()
            val startX = start.col * (cellSizePx + spacingPx) + offset
            val startY = start.row * (cellSizePx + spacingPx) + offset
            val endX   = end.col * (cellSizePx + spacingPx) + offset
            val endY   = end.row * (cellSizePx + spacingPx) + offset

            drawLine(
                color = Color(0xFFFFD700),
                start = Offset(startX, startY),
                end   = Offset(
                    startX + (endX - startX) * progress,
                    startY + (endY - startY) * progress
                ),
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
                color = if (isWinCell) Color(0xFFFFD700)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
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
                modifier = Modifier.fillMaxSize(0.80f).scale(scale),
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
                    Text(
                        "Mejor de ${state.totalRounds}",
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
                MoveHistoryPanel(movesLog = gameState?.movesLog ?: emptyList())
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (gameState?.practiceMode == true) {
                    OutlinedButton(
                        onClick = onUndo,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) { Text("↩️ Deshacer movimiento") }
                }

                Button(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("🚪 Abandonar partida") }
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
            LaunchedEffect(movesLog.size) {
                if (movesLog.isNotEmpty()) listState.animateScrollToItem(movesLog.size - 1)
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

/**
 * Shown after each individual round.
 * Displays win/loss/draw result for the round + current match score +
 * an auto-countdown showing when the next round starts.
 */
@Composable
private fun RoundEndOverlay(
    roundEnd: RoundEnd,
    gameState: GameState,
    gameClient: GameClient,
    isMatchEnd: Boolean
) {
    val playerId = gameClient.getPlayerId()
    val playerSymbol = when (playerId) {
        gameState.playerXId -> "X"
        gameState.playerOId -> "O"
        else -> null
    }

    val result = when {
        roundEnd.isDraw             -> "draw"
        roundEnd.winner == playerSymbol -> "win"
        else                        -> "lose"
    }

    val (imageRes, headline, bgColor) = when (result) {
        "win"  -> Triple(GameAssets.winIcon,  "¡Ganaste la ronda!",  Color(0xFF4CAF50))
        "lose" -> Triple(GameAssets.loseIcon, "Perdiste la ronda",   MaterialTheme.colorScheme.error)
        else   -> Triple(GameAssets.drawIcon, "Empate",              Color(0xFFFF9800))
    }

    // Countdown: server waits 3s before next round, but overlay appears ~0.9s late,
    // so we start from 2 to stay roughly in sync with the actual server transition.
    var countdown by remember { mutableStateOf(2) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    // Entry animation
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(350),
        label = "roundOverlayAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "roundOverlayScale"
    )
    var initialScale by remember { mutableStateOf(0.7f) }
    LaunchedEffect(Unit) { initialScale = 1f }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f * alpha)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(360.dp)
                .scale(scale),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(20.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Result icon
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bgColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = headline,
                        modifier = Modifier.size(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Headline
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineMedium,
                    color = bgColor,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Round info
                Text(
                    text = "Ronda ${gameState.currentRound} de ${gameState.totalRounds}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Current match score
                MatchScoreDisplay(gameState = gameState, playerSymbol = playerSymbol, gameClient = gameClient)

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Next round countdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Text(
                        text = if (countdown > 0) "Siguiente ronda en ${countdown}s..."
                        else "Cargando siguiente ronda...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── Match End Overlay ──────────────────────────────────────────────────────

/**
 * Shown at the end of the entire match.
 * Displays final win/loss/draw + final score + "returning to menu" countdown.
 */
@Composable
private fun MatchEndOverlay(
    roundEnd: RoundEnd,
    gameState: GameState,
    gameClient: GameClient
) {
    val playerId = gameClient.getPlayerId()
    val playerSymbol = when (playerId) {
        gameState.playerXId -> "X"
        gameState.playerOId -> "O"
        else -> null
    }

    val result = when {
        roundEnd.isDraw                 -> "draw"
        roundEnd.winner == playerSymbol -> "win"
        else                            -> "lose"
    }

    val (imageRes, headline, subHeadline, bgColor) = when (result) {
        "win"  -> Quad(GameAssets.winIcon,  "🏆 ¡VICTORIA!",   "¡Has ganado el match!",        Color(0xFFFFD700))
        "lose" -> Quad(GameAssets.loseIcon, "💀 DERROTA",       "Has perdido el match",          MaterialTheme.colorScheme.error)
        else   -> Quad(GameAssets.drawIcon, "🤝 EMPATE",        "El match ha terminado en empate", Color(0xFFFF9800))
    }

    // Overlay appears ~900ms after MATCH_END (line animation delay).
    // Client navigates away at 5000ms total → overlay visible for ~4100ms.
    // Countdown starts at 3: 3..2..1..0, then a moment of "0" before the screen changes.
    var countdown by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    // Entry animation: slide up + fade in
    val offsetY by animateFloatAsState(
        targetValue = 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 200f),
        label = "matchEndSlide"
    )
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "matchEndAlpha"
    )

    // Pulsating glow on win
    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseAnim.animateFloat(
        initialValue = 0.3f,
        targetValue = if (result == "win") 0.8f else 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f * alpha)),
        contentAlignment = Alignment.Center
    ) {
        // Background glow for winner
        if (result == "win") {
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .background(
                        Color(0xFFFFD700).copy(alpha = pulseAlpha * 0.08f),
                        RoundedCornerShape(50)
                    )
            )
        }

        Card(
            modifier = Modifier.width(420.dp).alpha(alpha),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(28.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                modifier = Modifier.padding(36.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Big result icon with colored ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(50))
                        .border(3.dp, bgColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .background(bgColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = headline,
                        modifier = Modifier.size(68.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                // Main headline
                Text(
                    text = headline,
                    style = MaterialTheme.typography.displaySmall,
                    color = bgColor,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subHeadline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )

                // FINAL SCORE (prominent)
                Text(
                    text = "Resultado Final",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                MatchScoreDisplay(
                    gameState = gameState,
                    playerSymbol = playerSymbol,
                    gameClient = gameClient,
                    large = true
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Return to menu countdown
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (countdown > 0) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = bgColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Volviendo al menú en ${countdown}s...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    } else {
                        Text(
                            text = "Cargando menú...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}

// ── Match Score Display (shared) ───────────────────────────────────────────

@Composable
private fun MatchScoreDisplay(
    gameState: GameState,
    playerSymbol: String?,
    gameClient: GameClient,
    large: Boolean = false
) {
    val opponentSymbol = if (playerSymbol == "X") "O" else "X"
    val isAIGame = gameState.playerXId == "AI" || gameState.playerOId == "AI"
    val playerLabel = gameClient.getPlayerName() ?: "Tú"
    val opponentLabel = if (isAIGame) "IA" else (gameClient.opponentName ?: "Oponente")

    val myScore = gameState.scores[playerSymbol] ?: 0
    val opScore = gameState.scores[opponentSymbol] ?: 0

    val scoreStyle = if (large) MaterialTheme.typography.displayMedium
    else MaterialTheme.typography.displaySmall

    val nameStyle = if (large) MaterialTheme.typography.bodyLarge
    else MaterialTheme.typography.bodyMedium

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                playerLabel,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                "$myScore",
                style = scoreStyle,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "($playerSymbol)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }

        Text(
            "–",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                opponentLabel,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                "$opScore",
                style = scoreStyle,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "($opponentSymbol)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

// ── Helper ─────────────────────────────────────────────────────────────────

/** Helper data class for 4-value destructuring in Kotlin */
private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)