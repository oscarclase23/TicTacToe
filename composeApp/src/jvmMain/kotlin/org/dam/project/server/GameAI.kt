package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.Difficulty
import org.dam.project.network.Position

/**
 * AI engine for PVE mode.
 * EASY:   Random moves
 * MEDIUM: Minimax depth 4 with alpha-beta pruning
 * HARD:   Full minimax (unbeatable on 3x3, deep search on 4x4/5x5)
 */
object GameAI {

    fun getBestMove(game: TicTacToeGame, difficulty: Difficulty, aiPlayer: String): Position {
        return try {
            when (difficulty) {
                Difficulty.EASY   -> getRandomMove(game)
                Difficulty.MEDIUM -> getMediumMove(game.copy(), aiPlayer)
                Difficulty.HARD   -> getMinimaxMove(game.copy(), aiPlayer, getMaxDepth(game.boardSize))
            }
        } catch (e: Exception) {
            println("[GameAI] ERROR in getBestMove: ${e.message}, falling back to random")
            getRandomMove(game)
        }
    }

    /** MEDIUM: Uses minimax depth 4 but sometimes makes a random move (20% chance) for imperfection */
    private fun getMediumMove(game: TicTacToeGame, aiPlayer: String): Position {
        // 20% chance of random move to make medium beatable
        if (Math.random() < 0.20) return getRandomMove(game)
        return getMinimaxMove(game, aiPlayer, depth = 4)
    }

    private fun getMaxDepth(boardSize: Int): Int = when (boardSize) {
        3 -> Int.MAX_VALUE  // Unbeatable on 3x3
        4 -> 5
        5 -> 4
        else -> 3
    }

    private fun getRandomMove(game: TicTacToeGame): Position =
        getValidMoves(game).random()

    private fun getMinimaxMove(game: TicTacToeGame, aiPlayer: String, depth: Int): Position {
        val opponent = if (aiPlayer == "X") "O" else "X"
        val validMoves = getValidMoves(game)

        // Center preference: try center first on 3x3
        val centerRow = game.boardSize / 2
        val centerCol = game.boardSize / 2
        val orderedMoves = validMoves.sortedByDescending {
            if (it.row == centerRow && it.col == centerCol) 1 else 0
        }

        var bestScore = Int.MIN_VALUE
        var bestMove = orderedMoves.first()

        for (move in orderedMoves) {
            game.makeMove(move.row, move.col, aiPlayer)
            val score = minimax(game, depth - 1, false, aiPlayer, opponent, Int.MIN_VALUE, Int.MAX_VALUE)
            game.undoLastMove()

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove
    }

    private fun minimax(
        game: TicTacToeGame,
        depth: Int,
        isMaximizing: Boolean,
        aiPlayer: String,
        opponent: String,
        alpha: Int,
        beta: Int
    ): Int {
        val winner = game.checkWinner()
        if (winner != null) {
            val winnerSymbol = game.getBoard()[winner[0].row][winner[0].col]
            // Score adjusted by depth: winning sooner is better
            return if (winnerSymbol == aiPlayer) 1000 + depth else -(1000 + depth)
        }
        if (game.isBoardFull()) return 0
        if (depth == 0) return evaluate(game, aiPlayer, opponent)

        val validMoves = getValidMoves(game)
        var currentAlpha = alpha
        var currentBeta = beta

        return if (isMaximizing) {
            var maxScore = Int.MIN_VALUE
            for (move in validMoves) {
                game.makeMove(move.row, move.col, aiPlayer)
                val score = minimax(game, depth - 1, false, aiPlayer, opponent, currentAlpha, currentBeta)
                game.undoLastMove()
                maxScore = maxOf(maxScore, score)
                currentAlpha = maxOf(currentAlpha, score)
                if (currentBeta <= currentAlpha) break
            }
            maxScore
        } else {
            var minScore = Int.MAX_VALUE
            for (move in validMoves) {
                game.makeMove(move.row, move.col, opponent)
                val score = minimax(game, depth - 1, true, aiPlayer, opponent, currentAlpha, currentBeta)
                game.undoLastMove()
                minScore = minOf(minScore, score)
                currentBeta = minOf(currentBeta, score)
                if (currentBeta <= currentAlpha) break
            }
            minScore
        }
    }

    /**
     * Heuristic evaluation:
     * - Counts potential winning lines for AI (+) and opponent (-)
     * - Weights lines with more pieces higher
     */
    private fun evaluate(game: TicTacToeGame, aiPlayer: String, opponent: String): Int {
        val board = game.getBoard()
        val size = game.boardSize
        val winLen = game.winLength
        var score = 0

        fun evalLine(positions: List<Position>) {
            val values = positions.map { board[it.row][it.col] }
            val aiCount = values.count { it == aiPlayer }
            val oppCount = values.count { it == opponent }
            if (aiCount > 0 && oppCount == 0) score += aiCount * aiCount * 10
            if (oppCount > 0 && aiCount == 0) score -= oppCount * oppCount * 10
        }

        // All possible windows of winLength in rows
        for (r in 0 until size) {
            for (startC in 0..size - winLen) {
                evalLine((startC until startC + winLen).map { Position(r, it) })
            }
        }
        // Columns
        for (c in 0 until size) {
            for (startR in 0..size - winLen) {
                evalLine((startR until startR + winLen).map { Position(it, c) })
            }
        }
        // Diagonals ↘
        for (startR in 0..size - winLen) {
            for (startC in 0..size - winLen) {
                evalLine((0 until winLen).map { Position(startR + it, startC + it) })
            }
        }
        // Diagonals ↙
        for (startR in 0..size - winLen) {
            for (startC in winLen - 1 until size) {
                evalLine((0 until winLen).map { Position(startR + it, startC - it) })
            }
        }

        return score
    }

    private fun getValidMoves(game: TicTacToeGame): List<Position> {
        val board = game.getBoard()
        return board.indices.flatMap { r ->
            board[r].indices.filter { c -> board[r][c].isEmpty() }.map { c -> Position(r, c) }
        }
    }
}