package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.*
import kotlinx.coroutines.*

/**
 * Represents an active game session between two players or player vs AI.
 */
class GameSession(
    val matchId: String,
    val config: GameConfig,
    val playerX: String,
    val playerO: String,
    val isAIGame: Boolean = false
) {
    // Connection Status
    var playerXConnected: Boolean = true
    var playerOConnected: Boolean = true
    
    val game = TicTacToeGame(config.boardSize, config.boardSize)
    var currentRound = 1
    val scores = mutableMapOf(playerX to 0, playerO to 0)
    
    // Statistics tracking
    val matchMoves = mutableListOf<Pair<String, Position>>()
    val matchStartTime = System.currentTimeMillis()
    
    // Timer management
    private var timerJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    private var turnId: Long = 0
    
    /**
     * Starts the turn timer.
     */
    fun startTurnTimer(onTimeout: suspend () -> Unit) {
        stopTimer()
        val currentTurnId = turnId
        timerJob = scope.launch {
            try {
                // Wait for the time limit
                delay(config.timeLimit * 1000L)
                // If we reach here, time is up
                if (isActive) {
                    // CRITICAL: Double check if we are still in the same turn
                    if (turnId == currentTurnId) {
                        onTimeout()
                    }
                }
            } catch (e: CancellationException) {
                // Timer cancelled, do nothing
            }
        }
    }
    
    /**
     * Stops the current turn timer.
     */
    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    
    /**
     * Attempts to make a move for a player.
     * 
     * @param playerId The player making the move
     * @param position The position to place the mark
     * @return MoveResult indicating success or failure
     */
    fun makeMove(playerId: String, position: Position): MoveResult {
        // Synchronize access to game state to prevent race conditions with Timer/Undo
        synchronized(this) {
            // Determine which player symbol this player has
            val playerSymbol = when (playerId) {
                playerX -> "X"
                playerO -> "O"
                else -> return MoveResult(
                    player = "",
                    position = position,
                    valid = false,
                    errorMessage = "Invalid player ID"
                )
            }
            
            // Check if it's this player's turn
            if (game.getCurrentPlayer() != playerSymbol) {
                return MoveResult(
                    player = playerSymbol,
                    position = position,
                    valid = false,
                    errorMessage = "Not your turn"
                )
            }
            
            // Attempt the move
            val success = game.makeMove(position.row, position.col, playerSymbol)
    
            if (success) {
                turnId++ // Invalidate previous timer
                matchMoves.add(playerId to position)
            }
            
            return MoveResult(
                player = playerSymbol,
                position = position,
                valid = success,
                errorMessage = if (!success) "Invalid move" else null
            )
        }
    }
    
    /**
     * Gets the current game state.
     */
    fun getGameState(): GameState {
        val currentPlayer = game.getCurrentPlayer()
        val nextPlayer = if (currentPlayer == "X") "O" else "X"
        return GameState(
            matchId = matchId,
            board = game.getBoard(),
            boardSize = config.boardSize,
            currentPlayer = currentPlayer,
            nextPlayer = nextPlayer,
            currentRound = currentRound,
            scores = scores.mapKeys { if (it.key == playerX) "X" else "O" },
            playerXId = playerX,
            playerOId = playerO,
            timeLimit = config.timeLimit // Include the session's time limit
        )
    }
    
    /**
     * Checks if the current round has ended.
     * 
     * @return RoundEnd if round is over, null otherwise
     */
    fun checkRoundEnd(): RoundEnd? {
        val winningLine = game.checkWinner()
        
        if (winningLine != null) {
            // Someone won
            val winner = game.getBoard()[winningLine[0].row][winningLine[0].col]
            val winnerId = if (winner == "X") playerX else playerO
            scores[winnerId] = (scores[winnerId] ?: 0) + 1
            
            return RoundEnd(
                winner = winner,
                winningLine = winningLine,
                isDraw = false
            )
        } else if (game.isBoardFull()) {
            // Draw
            return RoundEnd(
                winner = null,
                winningLine = null,
                isDraw = true
            )
        }
        
        return null
    }
    
    /**
     * Checks if the entire match has ended.
     * 
     * @return MatchEnd if match is over, null otherwise
     */
    fun checkMatchEnd(): MatchEnd? {
        // Check if we've completed all rounds
        if (currentRound > config.totalRounds) {
            val xScore = scores[playerX] ?: 0
            val oScore = scores[playerO] ?: 0
            
            val winner = when {
                xScore > oScore -> playerX
                oScore > xScore -> playerO
                else -> "DRAW"
            }
            
            return MatchEnd(
                winner = winner,
                score = mapOf(
                    playerX to xScore,
                    playerO to oScore
                )
            )
        }
        
        return null
    }
    
    /**
     * Starts the next round.
     */
    fun nextRound() {
        currentRound++
        turnId++ // Invalidate previous timer
        game.reset()
    }
    
    /**
     * Handles an undo request from a player.
     * 
     * @param playerId The player requesting undo
     * @return UndoResult indicating success/failure
     */
    fun handleUndo(playerId: String): UndoResult {
        // 1. Check if practice mode is enabled
        if (!config.practiceMode) {
            return UndoResult(false, "Undo only allowed in Practice Mode")
        }
        
        // 2. Check if player has made any moves
        val playerMoves = matchMoves.filter { it.first == playerId }
        if (playerMoves.isEmpty()) {
            return UndoResult(false, "No moves to undo")
        }
        
        // 3. Logic: Undo until we revert the last move by this player
        // In PVE, this usually means undoing AI move + Player move
        // In PVP local (if supported), just one move? Or usually undo is "take back move"
        
        var undoneCount = 0
        var foundPlayerMove = false
        
        while (matchMoves.isNotEmpty() && !foundPlayerMove) {
            val lastMove = matchMoves.removeLast()
            game.undoLastMove()
            undoneCount++
            
            if (lastMove.first == playerId) {
                foundPlayerMove = true
            }
        }
        
        if (foundPlayerMove) {
            // Cancel any active timer and restart it for the current player (which should be the one who just undid)
            stopTimer()
            return UndoResult(true, "Undid $undoneCount moves")
        }
        
        return UndoResult(false, "Failed to undo")
    }
    
    /**
     * Gets the player ID for a given symbol.
     */
    fun getPlayerId(symbol: String): String {
        return when (symbol) {
            "X" -> playerX
            "O" -> playerO
            else -> ""
        }
    }

    /**
     * Sets the connection status for a player.
     */
    fun setPlayerConnected(playerId: String, connected: Boolean) {
        if (playerId == playerX) playerXConnected = connected
        if (playerId == playerO) playerOConnected = connected
    }

    /**
     * Checks if a player is connected.
     */
    fun isPlayerConnected(playerId: String): Boolean {
        return if (playerId == playerX) playerXConnected 
               else if (playerId == playerO) playerOConnected
               else false
    }
}
