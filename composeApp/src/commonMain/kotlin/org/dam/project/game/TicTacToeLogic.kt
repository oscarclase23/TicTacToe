package org.dam.project.game

import org.dam.project.network.Position

/**
 * Pure game logic for Tic-Tac-Toe.
 * Supports variable board sizes (3x3, 4x4, 5x5) with corresponding win lengths.
 * 
 * This class is stateless regarding UI and networking, making it fully testable.
 * 
 * @param boardSize The size of the board (3, 4, or 5)
 * @param winLength The number of consecutive marks needed to win (equals boardSize)
 */
class TicTacToeGame(
    val boardSize: Int = 3,
    val winLength: Int = boardSize
) {
    private val board: MutableList<MutableList<String>> = MutableList(boardSize) { MutableList(boardSize) { "" } }
    private var currentPlayer: String = "X"
    
    init {
        require(boardSize in 3..5) { "Board size must be 3, 4, or 5" }
        require(winLength == boardSize) { "Win length must equal board size" }
    }
    
    /**
     * Attempts to make a move at the specified position.
     * 
     * @param row The row index (0-based)
     * @param col The column index (0-based)
     * @param player The player making the move ("X" or "O")
     * @return true if the move was valid and placed, false otherwise
     */
    fun makeMove(row: Int, col: Int, player: String): Boolean {
        // Validate bounds
        if (row !in 0 until boardSize || col !in 0 until boardSize) {
            return false
        }
        
        // Validate cell is empty
        if (board[row][col].isNotEmpty()) {
            return false
        }
        
        // Validate player
        if (player != "X" && player != "O") {
            return false
        }
        
        // Place the move
        board[row][col] = player
        moveHistory.addLast(Position(row, col))
        
        // Switch current player
        currentPlayer = if (currentPlayer == "X") "O" else "X"
        
        return true
    }
    
    /**
     * Checks if there is a winner and returns the winning line.
     * 
     * @return List of positions forming the winning line, or null if no winner
     */
    fun checkWinner(): List<Position>? {
        // Check horizontal lines
        for (row in 0 until boardSize) {
            val line = checkLine(
                positions = (0 until boardSize).map { col -> Position(row, col) }
            )
            if (line != null) return line
        }
        
        // Check vertical lines
        for (col in 0 until boardSize) {
            val line = checkLine(
                positions = (0 until boardSize).map { row -> Position(row, col) }
            )
            if (line != null) return line
        }
        
        // Check diagonal (top-left to bottom-right)
        val diagonal1 = checkLine(
            positions = (0 until boardSize).map { i -> Position(i, i) }
        )
        if (diagonal1 != null) return diagonal1
        
        // Check diagonal (top-right to bottom-left)
        val diagonal2 = checkLine(
            positions = (0 until boardSize).map { i -> Position(i, boardSize - 1 - i) }
        )
        if (diagonal2 != null) return diagonal2
        
        return null
    }
    
    /**
     * Helper function to check if a line of positions contains a win.
     */
    private fun checkLine(positions: List<Position>): List<Position>? {
        val values = positions.map { board[it.row][it.col] }
        
        // Check if all positions have the same non-empty value
        if (values.all { it.isNotEmpty() && it == values[0] }) {
            return positions
        }
        
        return null
    }
    
    /**
     * Checks if the board is completely full (draw condition).
     * 
     * @return true if all cells are occupied, false otherwise
     */
    fun isBoardFull(): Boolean {
        return board.all { row -> row.all { cell -> cell.isNotEmpty() } }
    }
    
    /**
     * Resets the board to empty state for a new round.
     */
    private val moveHistory = ArrayDeque<Position>()
    
    /**
     * Undoes the last move.
     * 
     * @return The position of the undone move, or null if no moves to undo
     */
    fun undoLastMove(): Position? {
        if (moveHistory.isEmpty()) return null
        
        val lastMove = moveHistory.removeLast()
        board[lastMove.row][lastMove.col] = ""
        
        // Switch player back
        currentPlayer = if (currentPlayer == "X") "O" else "X"
        
        return lastMove
    }
    
    /**
     * Resets the board to empty state for a new round.
     */
    fun reset() {
        for (row in 0 until boardSize) {
            for (col in 0 until boardSize) {
                board[row][col] = ""
            }
        }
        moveHistory.clear()
        currentPlayer = "X"
    }
    
    // ... existing methods ...
    
    /**
     * Gets the current player who should make the next move.
     * 
     * @return "X" or "O"
     */
    fun getCurrentPlayer(): String = currentPlayer
    
    /**
     * Gets a copy of the current board state.
     * 
     * @return Immutable copy of the board
     */
    fun getBoard(): List<List<String>> {
        return board.map { it.toList() }
    }
    
    /**
     * Gets the value at a specific position.
     * 
     * @param row The row index
     * @param col The column index
     * @return The value at the position ("X", "O", or "")
     */
    fun getCellValue(row: Int, col: Int): String {
        if (row !in 0 until boardSize || col !in 0 until boardSize) {
            return ""
        }
        return board[row][col]
    }

    fun isGameOver(): Boolean {
        return checkWinner() != null || isBoardFull()
    }
    
    /**
     * Creates a deep copy of the game state.
     */
    fun copy(): TicTacToeGame {
        val newGame = TicTacToeGame(boardSize, winLength)
        // Copy board
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                newGame.board[r][c] = this.board[r][c]
            }
        }
        // Copy history
        for (move in this.moveHistory) {
            newGame.moveHistory.addLast(move)
        }
        // Copy player
        newGame.currentPlayer = this.currentPlayer
        return newGame
    }
    /**
     * Helper to manually set the board state (e.g. for persistence restoration).
     */
    fun setBoard(newBoard: List<List<String>>) {
        require(newBoard.size == boardSize)
        for (r in 0 until boardSize) {
            require(newBoard[r].size == boardSize)
            for (c in 0 until boardSize) {
                board[r][c] = newBoard[r][c]
            }
        }
    }

    /**
     * Helper to manually set the current player (e.g. for persistence restoration).
     */
    fun setCurrentPlayer(player: String) {
        require(player == "X" || player == "O")
        currentPlayer = player
    }
}
