package org.dam.project.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.client.Screen
import org.dam.project.network.GameState
import org.dam.project.network.Position
import org.dam.project.network.RoundEnd
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource


/**
 * Game screen with board and info panel.
 */
@Composable
fun GameScreen(gameClient: GameClient, matchId: String) {
    val scope = rememberCoroutineScope()
    
    // Observe game state from GameClient
    val gameState by gameClient.currentGameState.collectAsState()
    
    // Observe round end result for showing win/lose/draw messages
    val roundEndResult by gameClient.roundEndResult.collectAsState()
    
    // Observe timer
    val timeRemaining by gameClient.timeRemaining.collectAsState()
    
    // Observe connection states
    val isOpponentDisconnected by gameClient.isOpponentDisconnected.collectAsState()
    val isConnectionLost by gameClient.isConnectionLost.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
        // Left side: Board with status
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Game Status Text
            if (gameState != null) {
                val playerId = gameClient.getPlayerId()
                
                // Determine which symbol the player controls
                val playerSymbol = when (playerId) {
                    gameState!!.playerXId -> "X"
                    gameState!!.playerOId -> "O"
                    else -> null
                }
                
                // Check if it's the player's turn
                val isPlayerTurn = playerSymbol == gameState!!.currentPlayer
                
                val statusText = when {
                    isPlayerTurn -> "🎮 Your Turn"
                    gameState!!.playerXId == "AI" || gameState!!.playerOId == "AI" -> "🤖 AI is thinking..."
                    else -> "⏳ ${gameClient.opponentName ?: "Opponent"}'s Turn"
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (isPlayerTurn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
            
            // Board
            if (gameState != null) {
                val playerId = gameClient.getPlayerId()
                val playerSymbol = when (playerId) {
                    gameState!!.playerXId -> "X"
                    gameState!!.playerOId -> "O"
                    else -> null
                }
                val isPlayerTurn = playerSymbol == gameState!!.currentPlayer
                
                BoardGrid(
                    gameState = gameState!!,
                    isPlayerTurn = isPlayerTurn && !isOpponentDisconnected && !isConnectionLost, // Disable if connection issues
                    roundEndResult = roundEndResult,
                    onCellClick = { row, col ->
                        if (isPlayerTurn && !isOpponentDisconnected && !isConnectionLost) {
                            scope.launch {
                                gameClient.makeMove(row, col)
                            }
                        }
                    }
                )
            } else {
                CircularProgressIndicator()
            }
        }
        
        // Right side: Info panel
        InfoPanel(
            gameState = gameState,
            gameClient = gameClient,
            timeRemaining = timeRemaining,
            onLeave = {
                // Determine if we should Surrender or just Leave
                scope.launch {
                    // if (gameState != null && !gameState!!.isGameOver) { // Logic to check if game is over?
                         // Ideally we ask "Surrender?" 
                         gameClient.surrenderGame() // Explicit surrender on leave
                         // Leave game logic handles navigation

                         gameClient.navigateTo(Screen.Menu)
                    // }
                }
            }
        )
        }
        
        // OVERLAYS
        
        // 1. Connection Lost Overlay
        if (isConnectionLost) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .zIndex(2000f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Conexión perdida...",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Intentando reconectar...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
        
        // 2. Opponent Disconnected Overlay
        if (isOpponentDisconnected && !isConnectionLost) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(1500f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Oponente desconectado",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Esperando a que regrese...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        // 3. Round End Result Overlay
        if (roundEndResult != null && gameState != null) {
            // REMOVED isPVEGame check to allow PVP overlay
            println("[GameScreen] Showing round end overlay: winner=${roundEndResult!!.winner}, isDraw=${roundEndResult!!.isDraw}")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1000f) // Ensure it's on top
            ) {
                RoundEndOverlay(
                    roundEnd = roundEndResult!!,
                    gameState = gameState!!,
                    gameClient = gameClient
                )
            }
        }
    }
}

/**
 * Game board grid with animations.
 */
@Composable
private fun BoardGrid(
    gameState: GameState,
    isPlayerTurn: Boolean,
    roundEndResult: RoundEnd?,
    onCellClick: (Int, Int) -> Unit
) {
    val boardSize = gameState.boardSize
    val cellSize = 100.dp
    val spacing = 4.dp
    
    // Animation for draw (shake)
    val isDraw = roundEndResult?.isDraw == true
    val shakeOffset by animateFloatAsState(
        targetValue = if (isDraw) 1f else 0f,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake"
    )
    
    // Animation for winning line
    val winningLine = roundEndResult?.winningLine
    val lineProgress by animateFloatAsState(
        targetValue = if (winningLine != null) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "winLine"
    )
    
    Box {
        Column(
            modifier = Modifier
                .rotate(shakeOffset * 2f), // Shake animation for draw
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            for (row in 0 until boardSize) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    for (col in 0 until boardSize) {
                        BoardCell(
                            symbol = gameState.board.getOrNull(row)?.getOrNull(col),
                            isClickable = isPlayerTurn,
                            onClick = { onCellClick(row, col) },
                            modifier = Modifier.size(cellSize)
                        )
                    }
                }
            }
        }
        
        // Draw winning line overlay on top of the board
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

/**
 * Overlay that draws the winning line animation.
 */
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
            
            val currentX = startX + (endX - startX) * progress
            val currentY = startY + (endY - startY) * progress
            
            drawLine(
                color = Color(0xFFFFD700), // Gold color
                start = Offset(startX, startY),
                end = Offset(currentX, currentY),
                strokeWidth = 8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
            )
        }
    }
}

/**
 * Individual board cell with animation.
 */
@Composable
private fun BoardCell(
    symbol: String?,
    isClickable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Animate scale when symbol appears
    val scale by animateFloatAsState(
        targetValue = if (symbol != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 200f
        )
    )
    
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.primary)
            .clickable(enabled = symbol.isNullOrEmpty() && isClickable) { 
                println("[BoardCell] Cell clicked! symbol='$symbol'")
                onClick() 
            },
        contentAlignment = Alignment.Center
    ) {
        if (symbol != null && symbol.isNotEmpty() && (symbol == "X" || symbol == "O")) {
            Image(
                painter = painterResource(GameAssets.getSymbolDrawable(symbol)),
                contentDescription = symbol,
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .align(Alignment.Center)
                    .scale(scale),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/**
 * Info panel showing game status and controls.
 */
@Composable
private fun InfoPanel(
    gameState: GameState?,
    gameClient: GameClient,
    timeRemaining: Int?,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Game Info",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Divider()
            
            if (gameState != null) {
                // Determine which symbol the player controls
                val playerId = gameClient.getPlayerId()
                val playerSymbol = when (playerId) {
                    gameState.playerXId -> "X"
                    gameState.playerOId -> "O"
                    else -> null
                }
                
                // Check if it's the player's turn
                val isPlayerTurn = playerSymbol == gameState.currentPlayer
                
                // Turn indicator
                val turnText = when {
                    isPlayerTurn -> "🎮 Your Turn"
                    gameState.playerXId == "AI" || gameState.playerOId == "AI" -> "🤖 AI is thinking..."
                    else -> "⏳ ${gameClient.opponentName ?: "Opponent"}'s Turn"
                }
                
                Text(
                    text = turnText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isPlayerTurn) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondary
                )
                
                // Timer display
                if (timeRemaining != null) {
                    val timerColor = when {
                        timeRemaining <= 5 -> Color(0xFFFF0000) // Intense Red
                        timeRemaining <= 10 -> Color(0xFFFF9800) // Orange
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    val timerLabel = if (isPlayerTurn) "⏱️ Tu tiempo" else "⏳ Rival"
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timerLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${timeRemaining}s",
                            style = MaterialTheme.typography.headlineMedium,
                            color = timerColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Score display
                Text(
                    text = "Round ${gameState.currentRound}",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("You", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${gameState.scores[playerSymbol] ?: 0}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        val opponentSymbol = if (playerSymbol == "X") "O" else "X"
                        val isAI = gameState.playerXId == "AI" || gameState.playerOId == "AI"
                        val opponentLabel = if (isAI) "AI" else (gameClient.opponentName ?: "Opponent")
                        
                        Text(opponentLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${gameState.scores[opponentSymbol] ?: 0}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Undo button (Practice Mode only)
            val scope = rememberCoroutineScope()
            
            if (gameClient.isPracticeMode()) {
                 Button(
                    onClick = { 
                        scope.launch {
                            gameClient.requestUndo()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(GameAssets.restartIcon), // Use restart/back icon
                            contentDescription = "Undo",
                            modifier = Modifier.size(24.dp), 
                            contentScale = ContentScale.Fit
                        )
                        Text("Undo Move")
                    }
                }
            }

            // Leave button
            Button(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(GameAssets.homeIcon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text("Leave Game")
                }
            }
        }
    }
}

/**
 * Overlay that shows the round end result (win/lose/draw) with image and message.
 * Only shown for PVE games.
 */
@Composable
private fun RoundEndOverlay(
    roundEnd: RoundEnd,
    gameState: GameState,
    gameClient: GameClient
) {
    // Determine if player won, lost, or drew
    val playerId = gameClient.getPlayerId()
    val playerSymbol = when (playerId) {
        gameState.playerXId -> "X"
        gameState.playerOId -> "O"
        else -> null
    }
    
    // Determine result from player's perspective
    val result = when {
        roundEnd.isDraw -> "draw"
        roundEnd.winner == playerSymbol -> "win"
        else -> "lose"
    }
    
    // Get image and message based on result
    val (imageResource, message) = when (result) {
        "win" -> GameAssets.winIcon to "¡Has ganado!"
        "lose" -> GameAssets.loseIcon to "Has perdido"
        "draw" -> GameAssets.drawIcon to "Empate"
        else -> GameAssets.drawIcon to "Empate"
    }
    
    // Animate appearance
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 500)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f * alpha))
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
            Card(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight(), // Use wrap content instead of strict padding
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Image centered
                    Image(
                        painter = painterResource(imageResource),
                        contentDescription = message,
                        modifier = Modifier.size(100.dp),
                        contentScale = ContentScale.Fit
                    )
                    
                    // Message centered
                    Text(
                        text = message,
                        style = MaterialTheme.typography.headlineLarge,
                        color = when (result) {
                            "win" -> MaterialTheme.colorScheme.primary
                            "lose" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    // Show Reason if available (e.g. "Opponent Disconnected")
                    if (roundEnd.reason != null) {
                        Text(
                            text = roundEnd.reason,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    
                    // Round info centered
                    Text(
                        text = "Ronda ${gameState.currentRound}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
    }
}
