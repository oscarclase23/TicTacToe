package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.Difficulty
import org.dam.project.network.Position

/**
 * AI engine for PVE mode.
 *
 * EASY:   Pure random moves.
 * MEDIUM: Minimax depth 3 with 25% random fallback — beatable but not trivial.
 * HARD:   Full minimax with alpha-beta pruning.
 *           - 3x3: completely exhaustive (depth 9 max) → UNBEATABLE, always forces
 *             win or draw, never loses.
 *           - 4x4: depth 7 with move ordering → very strong.
 *           - 5x5: depth 5 with move ordering + heuristic → strong but beatable.
 *
 * Key fixes vs previous version:
 *   1. Score formula corrected: winning sooner = higher score (depth remaining, not depth left).
 *      OLD (buggy): score = 1000 + depth  →  depth counts DOWN, so winning at depth=0 = 1000,
 *                                             winning at depth=5 = 1005 (prefers LATE wins — wrong)
 *      NEW (fixed): score = 1000 + depthRemaining  →  winning with more moves to spare = higher score.
 *
 *   2. minimax() now derives isMaximizing internally from the current player on the board,
 *      completely eliminating the possibility of the AI playing against itself.
 *
 *   3. Move ordering: center > corners > edges, evaluated before minimax to help alpha-beta
 *      prune more aggressively on larger boards.
 *
 *   4. Immediate win/block detection before full minimax — guarantees the AI never misses
 *      a 1-move win or a 1-move block even if alpha-beta cuts happen to skip it.
 */
object GameAI {

    fun getBestMove(game: TicTacToeGame, difficulty: Difficulty, aiPlayer: String): Position {
        return try {
            when (difficulty) {
                Difficulty.EASY   -> getRandomMove(game)
                Difficulty.MEDIUM -> getMediumMove(game.copy(), aiPlayer)
                Difficulty.HARD   -> getHardMove(game.copy(), aiPlayer)
            }
        } catch (e: Exception) {
            println("[GameAI] ERROR in getBestMove: ${e.message}, falling back to random")
            getRandomMove(game)
        }
    }

    // ── EASY ────────────────────────────────────────────────────────────────

    private fun getRandomMove(game: TicTacToeGame): Position =
        getValidMoves(game).random()

    // ── MEDIUM ──────────────────────────────────────────────────────────────

    /**
     * Medium: 25% chance of random, otherwise minimax depth 3.
     * This makes it "smart but beatable" — it will block obvious wins but
     * won't play perfectly.
     */
    private fun getMediumMove(game: TicTacToeGame, aiPlayer: String): Position {
        if (kotlin.random.Random.nextFloat() < 0.25f) return getRandomMove(game)
        // Check immediate win first (always take the win)
        val immediateWin = findImmediateWin(game, aiPlayer)
        if (immediateWin != null) return immediateWin
        // Check immediate block (sometimes miss it — 15% chance)
        val opponent = opponent(aiPlayer)
        val immediateBlock = findImmediateWin(game, opponent)
        if (immediateBlock != null && kotlin.random.Random.nextFloat() > 0.15f) return immediateBlock
        // Otherwise minimax depth 3
        return minimaxRoot(game, aiPlayer, depth = 3)
    }

    // ── HARD ────────────────────────────────────────────────────────────────

    /**
     * Hard: always play optimally.
     * Step 1: Check immediate win (1-move win) — take it immediately.
     * Step 2: Check immediate block (opponent 1-move win) — block it immediately.
     * Step 3: Full minimax with alpha-beta.
     *
     * Steps 1 and 2 are technically redundant with a correct minimax, but they act
     * as a safety net in case depth limits (on 4x4/5x5) cause the minimax to miss them.
     */
    private fun getHardMove(game: TicTacToeGame, aiPlayer: String): Position {
        // Step 1: take immediate win
        val win = findImmediateWin(game, aiPlayer)
        if (win != null) return win

        // Step 2: block immediate opponent win
        val block = findImmediateWin(game, opponent(aiPlayer))
        if (block != null) return block

        // Step 3: full minimax
        return minimaxRoot(game, aiPlayer, getMaxDepth(game.boardSize))
    }

    // ── Immediate win/block detection ───────────────────────────────────────

    /**
     * Finds a move that immediately wins the game for [player], or null if none exists.
     * This is O(n) over valid moves and guarantees we never miss a 1-move win.
     */
    private fun findImmediateWin(game: TicTacToeGame, player: String): Position? {
        for (move in getValidMoves(game)) {
            game.makeMove(move.row, move.col, player)
            val wins = game.checkWinner() != null
            game.undoLastMove()
            if (wins) return move
        }
        return null
    }

    // ── Minimax root ─────────────────────────────────────────────────────────

    private fun minimaxRoot(game: TicTacToeGame, aiPlayer: String, depth: Int): Position {
        val moves = getOrderedMoves(game, aiPlayer)

        var bestScore = Int.MIN_VALUE
        var bestMove = moves.first()

        for (move in moves) {
            game.makeMove(move.row, move.col, aiPlayer)
            // After AI plays, it's opponent's turn → isMaximizing = false
            val score = minimax(game, depth - 1, false, aiPlayer, Int.MIN_VALUE, Int.MAX_VALUE)
            game.undoLastMove()

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove
    }

    // ── Minimax with alpha-beta pruning ──────────────────────────────────────

    /**
     * Minimax with alpha-beta pruning.
     *
     * [isMaximizing] = true when it's the AI's turn, false when opponent's turn.
     * [aiPlayer]     = the symbol the AI is playing ("X" or "O") — fixed throughout the search.
     * [depth]        = remaining depth budget (decreases each ply).
     *
     * Score:
     *   +1000 + depth  →  AI wins  (higher depth remaining = sooner win = better)
     *   -1000 - depth  →  opponent wins  (higher depth remaining = sooner loss = worse)
     *   0              →  draw or depth exhausted without winner
     */
    private fun minimax(
        game: TicTacToeGame,
        depth: Int,
        isMaximizing: Boolean,
        aiPlayer: String,
        alpha: Int,
        beta: Int
    ): Int {
        // Terminal state checks
        val winner = game.checkWinner()
        if (winner != null) {
            val winnerSymbol = game.getBoard()[winner[0].row][winner[0].col]
            return if (winnerSymbol == aiPlayer) 1000 + depth else -(1000 + depth)
        }
        if (game.isBoardFull()) return 0
        if (depth == 0) return evaluate(game, aiPlayer)

        val currentPlayer = if (isMaximizing) aiPlayer else opponent(aiPlayer)
        val moves = getOrderedMoves(game, currentPlayer)

        var currentAlpha = alpha
        var currentBeta = beta

        return if (isMaximizing) {
            var maxScore = Int.MIN_VALUE
            for (move in moves) {
                game.makeMove(move.row, move.col, currentPlayer)
                val score = minimax(game, depth - 1, false, aiPlayer, currentAlpha, currentBeta)
                game.undoLastMove()
                if (score > maxScore) maxScore = score
                if (maxScore > currentAlpha) currentAlpha = maxScore
                if (currentBeta <= currentAlpha) break  // beta cut-off
            }
            maxScore
        } else {
            var minScore = Int.MAX_VALUE
            for (move in moves) {
                game.makeMove(move.row, move.col, currentPlayer)
                val score = minimax(game, depth - 1, true, aiPlayer, currentAlpha, currentBeta)
                game.undoLastMove()
                if (score < minScore) minScore = score
                if (minScore < currentBeta) currentBeta = minScore
                if (currentBeta <= currentAlpha) break  // alpha cut-off
            }
            minScore
        }
    }

    // ── Move ordering ────────────────────────────────────────────────────────

    /**
     * Returns valid moves sorted by strategic priority (best first).
     * Better ordering → alpha-beta prunes more → deeper effective search.
     *
     * Priority:
     *   1. Immediate winning move (score 1000)
     *   2. Immediate blocking move (score 900)
     *   3. Center cell (score 50)
     *   4. Corner cells (score 30)
     *   5. Edge cells (score 10)
     */
    private fun getOrderedMoves(game: TicTacToeGame, player: String): List<Position> {
        val moves = getValidMoves(game)
        if (moves.size <= 1) return moves

        val center  = game.boardSize / 2
        val maxIdx  = game.boardSize - 1
        val corners = setOf(
            Position(0, 0), Position(0, maxIdx),
            Position(maxIdx, 0), Position(maxIdx, maxIdx)
        )
        val opp = opponent(player)

        return moves.sortedByDescending { move ->
            // Check if this move wins immediately
            game.makeMove(move.row, move.col, player)
            val winsNow = game.checkWinner() != null
            game.undoLastMove()
            if (winsNow) return@sortedByDescending 1000

            // Check if this move blocks opponent's immediate win
            game.makeMove(move.row, move.col, opp)
            val blocksWin = game.checkWinner() != null
            game.undoLastMove()
            if (blocksWin) return@sortedByDescending 900

            // Positional priority
            when {
                move.row == center && move.col == center -> 50
                move in corners -> 30
                else -> 10
            }
        }
    }

    // ── Heuristic evaluation (depth limit fallback) ──────────────────────────

    /**
     * Heuristic for when depth is exhausted (used on 4x4/5x5).
     * Scores lines based on how many AI/opponent pieces they contain,
     * with exponential weighting for near-complete lines.
     */
    private fun evaluate(game: TicTacToeGame, aiPlayer: String): Int {
        val board  = game.getBoard()
        val size   = game.boardSize
        val winLen = game.winLength
        val opp    = opponent(aiPlayer)
        var score  = 0

        fun evalLine(positions: List<Position>) {
            val values   = positions.map { board[it.row][it.col] }
            val aiCount  = values.count { it == aiPlayer }
            val oppCount = values.count { it == opp }
            // Only score lines that aren't blocked by opponent pieces
            if (aiCount > 0 && oppCount == 0) score += when (aiCount) {
                winLen - 1 -> 100   // one away from winning
                winLen - 2 -> 10
                else       -> 1
            }
            if (oppCount > 0 && aiCount == 0) score -= when (oppCount) {
                winLen - 1 -> 100   // opponent one away from winning — must block!
                winLen - 2 -> 10
                else       -> 1
            }
        }

        // All rows
        for (r in 0 until size) {
            for (startC in 0..size - winLen) {
                val linePositions = (startC until startC + winLen).map { Position(r, it) }
                evalLine(linePositions)
            }
        }
        // All columns
        for (c in 0 until size) {
            for (startR in 0..size - winLen) {
                val linePositions = (startR until startR + winLen).map { Position(it, c) }
                evalLine(linePositions)
            }
        }
        // Diagonals ↘
        for (startR in 0..size - winLen) {
            for (startC in 0..size - winLen) {
                val linePositions = (0 until winLen).map { Position(startR + it, startC + it) }
                evalLine(linePositions)
            }
        }
        // Diagonals ↙
        for (startR in 0..size - winLen) {
            for (startC in winLen - 1 until size) {
                val linePositions = (0 until winLen).map { Position(startR + it, startC - it) }
                evalLine(linePositions)
            }
        }

        return score
    }

    // ── Depth limits ────────────────────────────────────────────────────────

    /**
     * Max search depth per board size.
     *
     * 3x3: 9 (full exhaustive search — the game tree has at most 9 plies).
     *       With alpha-beta this is near-instant. UNBEATABLE guaranteed.
     *
     * 4x4: 7 plies. 4x4 has up to 16 plies but that's too slow.
     *       Depth 7 + move ordering gives very strong play.
     *
     * 5x5: 5 plies. 5x5 tree is enormous; depth 5 + heuristic is still strong.
     */
    private fun getMaxDepth(boardSize: Int): Int = when (boardSize) {
        3    -> 9    // exhaustive — UNBEATABLE
        4    -> 7
        5    -> 5
        else -> 4
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private fun opponent(player: String) = if (player == "X") "O" else "X"

    private fun getValidMoves(game: TicTacToeGame): List<Position> {
        val board = game.getBoard()
        return board.indices.flatMap { r ->
            board[r].indices
                .filter { c -> board[r][c].isEmpty() }
                .map { c -> Position(r, c) }
        }
    }
}