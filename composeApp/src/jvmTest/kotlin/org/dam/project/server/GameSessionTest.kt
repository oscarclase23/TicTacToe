package org.dam.project.server

import org.dam.project.network.GameConfig
import org.dam.project.network.Difficulty
import org.dam.project.network.Position
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameSessionTest {

    @Test
    fun testUndoInPracticeModePVE() {
        val config = GameConfig(
            boardSize = 3,
            winLength = 3,
            totalRounds = 3,
            difficulty = Difficulty.EASY,
            practiceMode = true
        )
        val session = GameSession("match1", config, "Player1", "AI", true)
        
        // Player moves
        session.makeMove("Player1", Position(0, 0))
        // Simulate AI move (GameServer usually does this via processMove logic, but session just records what it's told)
        session.makeMove("AI", Position(0, 1))

        assertEquals(2, session.matchMoves.size)
        assertEquals("X", session.game.getCellValue(0, 0))
        assertEquals("O", session.game.getCellValue(0, 1))

        // Request undo
        val result = session.handleUndo("Player1")
        
        assertTrue(result.success, "Undo should succeed in practice mode")
        // Should undo 2 moves (Player + AI) because it loops until it finds the player's move to undo
        
        // Logic check: handleUndo removes moves from END until it finds one by "playerId".
        // matchMoves: [(Player1, (0,0)), (AI, (0,1))]
        // 1. Pop (AI, (0,1)). Undo board. matchMoves=[(Player1, (0,0))]. Last move was not Player1. Continue.
        // 2. Pop (Player1, (0,0)). Undo board. matchMoves=[]. Last move WAS Player1. Stop.
        // Undone count = 2.
        
        assertEquals(0, session.matchMoves.size)
        assertEquals("", session.game.getCellValue(0, 0))
        assertEquals("", session.game.getCellValue(0, 1))
    }

    @Test
    fun testUndoFailsInNonPracticeMode() {
        val config = GameConfig(3, 3, 3, Difficulty.EASY, practiceMode = false)
        val session = GameSession("match2", config, "Player1", "AI", true)
        
        session.makeMove("Player1", Position(0, 0))
        
        val result = session.handleUndo("Player1")
        assertFalse(result.success, "Undo should fail in non-practice mode")
    }

    @Test
    fun testUndoMultipleMovesPractice() {
        // Player1, AI, Player1, AI
        val config = GameConfig(3, 3, 3, Difficulty.EASY, practiceMode = true)
        val session = GameSession("match3", config, "Player1", "AI", true)

        session.makeMove("Player1", Position(0, 0)) // X
        session.makeMove("AI", Position(0, 1))      // O
        session.makeMove("Player1", Position(1, 0)) // X
        session.makeMove("AI", Position(1, 1))      // O
        
        assertEquals(4, session.matchMoves.size)
        
        // Undo last Player1 move (and subsequent AI move)
        val result = session.handleUndo("Player1")
        assertTrue(result.success)
        
        // Should have removed AI(1,1) and Player1(1,0)
        assertEquals(2, session.matchMoves.size, "Should remain 2 moves")
        assertEquals("", session.game.getCellValue(1, 0))
        assertEquals("", session.game.getCellValue(1, 1))
        
        // Board state at (0,0) and (0,1) should remain
        assertEquals("X", session.game.getCellValue(0, 0))
        assertEquals("O", session.game.getCellValue(0, 1))
    }
}
