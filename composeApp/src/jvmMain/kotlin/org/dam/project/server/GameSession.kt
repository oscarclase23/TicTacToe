package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.*
import kotlinx.coroutines.*

class GameSession(
    val matchId: String,
    val config: GameConfig,
    val playerX: String,
    val playerO: String,
    val isAIGame: Boolean = false
) {
    var playerXConnected: Boolean = true
    var playerOConnected: Boolean = true

    val game = TicTacToeGame(config.boardSize, config.boardSize)
    var currentRound = 1
    val scores = mutableMapOf(playerX to 0, playerO to 0)

    // Statistics
    val matchMoves = mutableListOf<Pair<String, Position>>()
    val matchStartTime = System.currentTimeMillis()

    // Timer
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private var turnId: Long = 0

    fun startTurnTimer(onTimeout: suspend () -> Unit) {
        stopTimer()
        val currentTurnId = turnId
        timerJob = scope.launch {
            try {
                delay(config.timeLimit * 1000L)
                if (isActive && turnId == currentTurnId) {
                    onTimeout()
                }
            } catch (_: CancellationException) {}
        }
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun makeMove(playerId: String, position: Position): MoveResult {
        synchronized(this) {
            val playerSymbol = when (playerId) {
                playerX -> "X"
                playerO -> "O"
                else -> return MoveResult("", position, false, "Invalid player ID")
            }

            if (game.getCurrentPlayer() != playerSymbol) {
                return MoveResult(playerSymbol, position, false, "Not your turn")
            }

            val success = game.makeMove(position.row, position.col, playerSymbol)
            if (success) {
                turnId++
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

    fun getGameState(): GameState {
        val currentPlayer = game.getCurrentPlayer()
        return GameState(
            matchId = matchId,
            board = game.getBoard(),
            boardSize = config.boardSize,
            currentPlayer = currentPlayer,
            nextPlayer = if (currentPlayer == "X") "O" else "X",
            currentRound = currentRound,
            totalRounds = config.totalRounds,
            scores = scores.mapKeys { if (it.key == playerX) "X" else "O" },
            playerXId = playerX,
            playerOId = playerO,
            timeLimit = config.timeLimit,
            practiceMode = config.practiceMode,
            movesLog = game.getMovesLogStrings()
        )
    }

    fun checkRoundEnd(): RoundEnd? {
        val winningLine = game.checkWinner()
        if (winningLine != null) {
            val winner = game.getBoard()[winningLine[0].row][winningLine[0].col]
            val winnerId = if (winner == "X") playerX else playerO
            scores[winnerId] = (scores[winnerId] ?: 0) + 1
            return RoundEnd(winner = winner, winningLine = winningLine, isDraw = false)
        }
        if (game.isBoardFull()) {
            return RoundEnd(winner = null, winningLine = null, isDraw = true)
        }
        return null
    }

    fun checkMatchEnd(): MatchEnd? {
        if (currentRound > config.totalRounds) {
            val xScore = scores[playerX] ?: 0
            val oScore = scores[playerO] ?: 0
            val winner = when {
                xScore > oScore -> playerX
                oScore > xScore -> playerO
                else -> "DRAW"
            }
            return MatchEnd(winner = winner, score = mapOf(playerX to xScore, playerO to oScore))
        }
        return null
    }

    fun nextRound() {
        currentRound++
        turnId++
        game.reset()
    }

    fun handleUndo(playerId: String): UndoResult {
        if (!config.practiceMode) return UndoResult(false, "Undo only allowed in Practice Mode")

        val playerMoves = matchMoves.filter { it.first == playerId }
        if (playerMoves.isEmpty()) return UndoResult(false, "No moves to undo")

        var foundPlayerMove = false
        var undoneCount = 0

        while (matchMoves.isNotEmpty() && !foundPlayerMove) {
            val lastMove = matchMoves.removeLast()
            game.undoLastMove()
            undoneCount++
            if (lastMove.first == playerId) foundPlayerMove = true
        }

        return if (foundPlayerMove) {
            stopTimer()
            UndoResult(true, "Undid $undoneCount moves")
        } else {
            UndoResult(false, "Failed to undo")
        }
    }

    fun getPlayerId(symbol: String): String = when (symbol) {
        "X" -> playerX
        "O" -> playerO
        else -> ""
    }

    fun setPlayerConnected(playerId: String, connected: Boolean) {
        if (playerId == playerX) playerXConnected = connected
        if (playerId == playerO) playerOConnected = connected
    }

    fun isPlayerConnected(playerId: String): Boolean =
        if (playerId == playerX) playerXConnected
        else if (playerId == playerO) playerOConnected
        else false
}