package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.Difficulty
import org.dam.project.network.Position
import kotlin.random.Random

/**
 * AI engine for PVE mode.
 * Stateless - takes game state and returns best move.
 */
object GameAI {
    
    /**
     * Gets the best move for the AI based on difficulty level.
     * 
     * @param game Current game state
     * @param difficulty AI difficulty level
     * @param aiPlayer AI's symbol ("X" or "O")
     * @return Best position to play
     */
    fun getBestMove(game: TicTacToeGame, difficulty: Difficulty, aiPlayer: String): Position {
        return try {
            when (difficulty) {
                Difficulty.EASY -> getRandomMove(game)
                Difficulty.MEDIUM -> getMinimaxMove(game.copy(), aiPlayer, depth = 2)
                Difficulty.HARD -> getMinimaxMove(game.copy(), aiPlayer, depth = getMaxDepth(game.boardSize))
            }
        } catch (e: Exception) {
            println("[GameAI] ERROR: getBestMove failed: ${e.message}, falling back to random")
            e.printStackTrace()
            // Failsafe: always return a valid random move
            getRandomMove(game)
        }
    }
    
    /**
     * Gets maximum search depth based on board size to prevent timeouts.
     */
    private fun getMaxDepth(boardSize: Int): Int {
        return when (boardSize) {
            3 -> Int.MAX_VALUE // No limit for 3x3 (unbeatable)
            4 -> 4
            5 -> 4
            else -> 3
        }
    }
    
    /**
     * EASY: Returns a random valid move.
     */
    private fun getRandomMove(game: TicTacToeGame): Position {
        val validMoves = getValidMoves(game)
        return validMoves.random()
    }
    
    /**
     * MEDIUM/HARD: Returns best move using minimax with alpha-beta pruning.
     */
    private fun getMinimaxMove(game: TicTacToeGame, aiPlayer: String, depth: Int): Position {
        val opponent = if (aiPlayer == "X") "O" else "X"
        val validMoves = getValidMoves(game)
        
        var bestScore = Int.MIN_VALUE
        var bestMove = validMoves.first()
        
        for (move in validMoves) {
            // Try this move
            game.makeMove(move.row, move.col, aiPlayer)
            
            // Evaluate with minimax
            val score = minimax(game, depth - 1, false, aiPlayer, opponent, Int.MIN_VALUE, Int.MAX_VALUE)
            
            // Undo move
            undoMove(game, move)
            
            // Update best move
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }
        
        return bestMove
    }
    
    /**
     * Minimax algorithm with alpha-beta pruning.
     * 
     * @param game Current game state
     * @param depth Remaining search depth
     * @param isMaximizing Whether this is maximizing player's turn
     * @param aiPlayer AI's symbol
     * @param opponent Opponent's symbol
     * @param alpha Alpha value for pruning
     * @param beta Beta value for pruning
     * @return Evaluation score
     */
    private fun minimax(
        game: TicTacToeGame,
        depth: Int,
        isMaximizing: Boolean,
        aiPlayer: String,
        opponent: String,
        alpha: Int,
        beta: Int
    ): Int {
        // Check terminal states
        val winner = game.checkWinner()
        if (winner != null) {
            val winnerSymbol = game.getBoard()[winner[0].row][winner[0].col]
            return if (winnerSymbol == aiPlayer) 100 else -100
        }
        
        if (game.isBoardFull()) {
            return 0 // Draw
        }
        
        if (depth == 0) {
            return evaluate(game, aiPlayer, opponent)
        }
        
        val validMoves = getValidMoves(game)
        var currentAlpha = alpha
        var currentBeta = beta
        
        if (isMaximizing) {
            var maxScore = Int.MIN_VALUE
            
            for (move in validMoves) {
                game.makeMove(move.row, move.col, aiPlayer)
                val score = minimax(game, depth - 1, false, aiPlayer, opponent, currentAlpha, currentBeta)
                undoMove(game, move)
                
                maxScore = maxOf(maxScore, score)
                currentAlpha = maxOf(currentAlpha, score)
                
                if (currentBeta <= currentAlpha) {
                    break // Beta cutoff
                }
            }
            
            return maxScore
        } else {
            var minScore = Int.MAX_VALUE
            
            for (move in validMoves) {
                game.makeMove(move.row, move.col, opponent)
                val score = minimax(game, depth - 1, true, aiPlayer, opponent, currentAlpha, currentBeta)
                undoMove(game, move)
                
                minScore = minOf(minScore, score)
                currentBeta = minOf(currentBeta, score)
                
                if (currentBeta <= currentAlpha) {
                    break // Alpha cutoff
                }
            }
            
            return minScore
        }
    }
    
    /**
     * Evaluates board position heuristically.
     */
    private fun evaluate(game: TicTacToeGame, aiPlayer: String, opponent: String): Int {
        // Simple heuristic: count potential winning lines
        var score = 0
        val board = game.getBoard()
        val size = game.boardSize
        
        // Check all possible lines
        // Rows
        for (row in 0 until size) {
            score += evaluateLine(board, (0 until size).map { Position(row, it) }, aiPlayer, opponent)
        }
        
        // Columns
        for (col in 0 until size) {
            score += evaluateLine(board, (0 until size).map { Position(it, col) }, aiPlayer, opponent)
        }
        
        // Diagonals
        score += evaluateLine(board, (0 until size).map { Position(it, it) }, aiPlayer, opponent)
        score += evaluateLine(board, (0 until size).map { Position(it, size - 1 - it) }, aiPlayer, opponent)
        
        return score
    }
    
    /**
     * Evaluates a single line (row, column, or diagonal).
     */
    private fun evaluateLine(board: List<List<String>>, positions: List<Position>, aiPlayer: String, opponent: String): Int {
        var aiCount = 0
        var opponentCount = 0
        
        for (pos in positions) {
            when (board[pos.row][pos.col]) {
                aiPlayer -> aiCount++
                opponent -> opponentCount++
            }
        }
        
        // If both players have marks in this line, it's blocked
        if (aiCount > 0 && opponentCount > 0) {
            return 0
        }
        
        // Score based on how many marks we have
        return when {
            aiCount > 0 -> aiCount * aiCount
            opponentCount > 0 -> -opponentCount * opponentCount
            else -> 0
        }
    }
    
    /**
     * Gets all valid moves for current board state.
     */
    private fun getValidMoves(game: TicTacToeGame): List<Position> {
        val moves = mutableListOf<Position>()
        val board = game.getBoard()
        
        for (row in board.indices) {
            for (col in board[row].indices) {
                if (board[row][col].isEmpty()) {
                    moves.add(Position(row, col))
                }
            }
        }
        
        return moves
    }
    
    /**
     * Undoes a move.
     */
    private fun undoMove(game: TicTacToeGame, position: Position) {
        game.undoLastMove()
    }
}
