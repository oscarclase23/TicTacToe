package org.dam.project.game

import org.dam.project.network.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TicTacToeLogicTest {
    
    @Test
    fun testInitialBoardIsEmpty() {
        val game = TicTacToeGame(3)
        val board = game.getBoard()
        
        assertEquals(3, board.size)
        assertTrue(board.all { row -> row.all { it == "" } })
    }
    
    @Test
    fun testValidMoveOnEmptyCell() {
        val game = TicTacToeGame(3)
        val result = game.makeMove(0, 0, "X")
        
        assertTrue(result)
        assertEquals("X", game.getCellValue(0, 0))
    }
    
    @Test
    fun testInvalidMoveOnOccupiedCell() {
        val game = TicTacToeGame(3)
        game.makeMove(0, 0, "X")
        val result = game.makeMove(0, 0, "O")
        
        assertFalse(result)
        assertEquals("X", game.getCellValue(0, 0))
    }
    
    @Test
    fun testInvalidMoveOutOfBounds() {
        val game = TicTacToeGame(3)
        
        assertFalse(game.makeMove(-1, 0, "X"))
        assertFalse(game.makeMove(0, -1, "X"))
        assertFalse(game.makeMove(3, 0, "X"))
        assertFalse(game.makeMove(0, 3, "X"))
    }
    
    @Test
    fun testPlayerAlternation() {
        val game = TicTacToeGame(3)
        
        assertEquals("X", game.getCurrentPlayer())
        game.makeMove(0, 0, "X")
        assertEquals("O", game.getCurrentPlayer())
        game.makeMove(0, 1, "O")
        assertEquals("X", game.getCurrentPlayer())
    }
    
    @Test
    fun testHorizontalWinDetection() {
        val game = TicTacToeGame(3)
        
        // Create horizontal win in row 0
        game.makeMove(0, 0, "X")
        game.makeMove(1, 0, "O")
        game.makeMove(0, 1, "X")
        game.makeMove(1, 1, "O")
        game.makeMove(0, 2, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(3, winningLine.size)
        assertTrue(winningLine.contains(Position(0, 0)))
        assertTrue(winningLine.contains(Position(0, 1)))
        assertTrue(winningLine.contains(Position(0, 2)))
    }
    
    @Test
    fun testVerticalWinDetection() {
        val game = TicTacToeGame(3)
        
        // Create vertical win in column 0
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(1, 0, "X")
        game.makeMove(1, 1, "O")
        game.makeMove(2, 0, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(3, winningLine.size)
        assertTrue(winningLine.contains(Position(0, 0)))
        assertTrue(winningLine.contains(Position(1, 0)))
        assertTrue(winningLine.contains(Position(2, 0)))
    }
    
    @Test
    fun testDiagonalWinDetectionTopLeftToBottomRight() {
        val game = TicTacToeGame(3)
        
        // Create diagonal win
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(0, 2, "O")
        game.makeMove(2, 2, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(3, winningLine.size)
        assertTrue(winningLine.contains(Position(0, 0)))
        assertTrue(winningLine.contains(Position(1, 1)))
        assertTrue(winningLine.contains(Position(2, 2)))
    }
    
    @Test
    fun testDiagonalWinDetectionTopRightToBottomLeft() {
        val game = TicTacToeGame(3)
        
        // Create diagonal win
        game.makeMove(0, 2, "X")
        game.makeMove(0, 0, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(2, 0, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(3, winningLine.size)
        assertTrue(winningLine.contains(Position(0, 2)))
        assertTrue(winningLine.contains(Position(1, 1)))
        assertTrue(winningLine.contains(Position(2, 0)))
    }
    
    @Test
    fun testNoWinnerYet() {
        val game = TicTacToeGame(3)
        
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        
        assertNull(game.checkWinner())
    }
    
    @Test
    fun testBoardFullDetection() {
        val game = TicTacToeGame(3)
        
        assertFalse(game.isBoardFull())
        
        // Fill the board (creating a draw)
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(0, 2, "X")
        game.makeMove(1, 0, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(1, 2, "O")
        game.makeMove(2, 0, "O")
        game.makeMove(2, 1, "X")
        game.makeMove(2, 2, "O")
        
        assertTrue(game.isBoardFull())
    }
    
    @Test
    fun testResetBoard() {
        val game = TicTacToeGame(3)
        
        game.makeMove(0, 0, "X")
        game.makeMove(1, 1, "O")
        game.makeMove(2, 2, "X")
        
        game.reset()
        
        val board = game.getBoard()
        assertTrue(board.all { row -> row.all { it == "" } })
        assertEquals("X", game.getCurrentPlayer())
    }
    
    @Test
    fun testIsGameOverWithWinner() {
        val game = TicTacToeGame(3)
        
        assertFalse(game.isGameOver())
        
        // Create a win
        game.makeMove(0, 0, "X")
        game.makeMove(1, 0, "O")
        game.makeMove(0, 1, "X")
        game.makeMove(1, 1, "O")
        game.makeMove(0, 2, "X")
        
        assertTrue(game.isGameOver())
    }
    
    @Test
    fun testIsGameOverWithDraw() {
        val game = TicTacToeGame(3)
        
        // Fill board without winner
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(0, 2, "X")
        game.makeMove(1, 0, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(1, 2, "O")
        game.makeMove(2, 0, "O")
        game.makeMove(2, 1, "X")
        game.makeMove(2, 2, "O")
        
        assertTrue(game.isGameOver())
        assertNull(game.checkWinner())
    }
    
    @Test
    fun test4x4BoardSize() {
        val game = TicTacToeGame(4)
        
        assertEquals(4, game.boardSize)
        assertEquals(4, game.getBoard().size)
        
        // Test horizontal win on 4x4
        game.makeMove(0, 0, "X")
        game.makeMove(1, 0, "O")
        game.makeMove(0, 1, "X")
        game.makeMove(1, 1, "O")
        game.makeMove(0, 2, "X")
        game.makeMove(1, 2, "O")
        game.makeMove(0, 3, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(4, winningLine.size)
    }
    
    @Test
    fun test5x5BoardSize() {
        val game = TicTacToeGame(5)
        
        assertEquals(5, game.boardSize)
        assertEquals(5, game.getBoard().size)
        
        // Test diagonal win on 5x5
        game.makeMove(0, 0, "X")
        game.makeMove(0, 1, "O")
        game.makeMove(1, 1, "X")
        game.makeMove(0, 2, "O")
        game.makeMove(2, 2, "X")
        game.makeMove(0, 3, "O")
        game.makeMove(3, 3, "X")
        game.makeMove(0, 4, "O")
        game.makeMove(4, 4, "X")
        
        val winningLine = game.checkWinner()
        assertNotNull(winningLine)
        assertEquals(5, winningLine.size)
    }
}
