package org.dam.project.server

import org.dam.project.game.TicTacToeGame
import org.dam.project.network.Difficulty
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameAITest {

    @Test
    fun testAIBlocksOpponentWinMinimax() {
        // Setup a board where opponent (X) is about to win
        // X X .
        // O . .
        // . . .
        val game = TicTacToeGame(3, 3)
        // Player X moves
        game.makeMove(0, 0, "X")
        game.makeMove(1, 0, "O") // AI response (dummy)
        game.makeMove(0, 1, "X")

        // Now it's O's turn (AI). AI must play (0, 2) to block.
        // If integer overflow existed, AI might pick a losing move.
        val bestMove = GameAI.getBestMove(game, Difficulty.HARD, "O")

        assertEquals(0, bestMove.row)
        assertEquals(2, bestMove.col, "AI should block the win at (0,2)")
    }

    @Test
    fun testAITakesWinningMove() {
        // Setup a board where AI (O) can win
        // O O .
        // X X .
        // . . .
        val game = TicTacToeGame(3, 3)
        game.makeMove(1, 0, "X")
        game.makeMove(0, 0, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(2, 0, "X") // Player X makes some move

        // Limit game logic might imply turns.
        // We need to ensure it's O's turn.
        // X(1,0), O(0,0), X(1,1), O(0,1), X(2,0) -> Next is O.
        
        val bestMove = GameAI.getBestMove(game, Difficulty.HARD, "O")
        
        assertEquals(0, bestMove.row)
        assertEquals(2, bestMove.col, "AI should win at (0,2)")
    }

    @Test
    fun testAIPreferCenterOnEmpty() {
        val game = TicTacToeGame(3, 3)
        val bestMove = GameAI.getBestMove(game, Difficulty.HARD, "X")
        
        // Minimax often prefers center (1,1) or corner.
        // On 3x3, center is usually best or equal best.
        // Our AI has center preference heuristic logic:
        // "val orderedMoves = validMoves.sortedByDescending { if (it.row == centerRow && it.col == centerCol) 1 else 0 }"
        
        assertEquals(1, bestMove.row)
        assertEquals(1, bestMove.col, "AI should prefer center (1,1) on empty 3x3")
    }
}
