package org.dam.project.game

import org.dam.project.network.Position

/**
 * Pure game logic for Tic-Tac-Toe.
 * Supports variable board sizes (3x3, 4x4, 5x5) with corresponding win lengths.
 */
class TicTacToeGame(
    val boardSize: Int = 3,
    val winLength: Int = boardSize
) {
    private val board: MutableList<MutableList<String>> =
        MutableList(boardSize) { MutableList(boardSize) { "" } }
    private var currentPlayer: String = "X"
    private val moveHistory = ArrayDeque<Position>()

    // Historial legible para UI (símbolo + posición)
    val movesLog = mutableListOf<Pair<String, Position>>() // ("X", Position(1,1))

    init {
        require(boardSize in 3..5) { "Board size must be 3, 4, or 5" }
        require(winLength in 3..boardSize) { "Win length must be between 3 and boardSize" }
    }

    fun makeMove(row: Int, col: Int, player: String): Boolean {
        if (row !in 0 until boardSize || col !in 0 until boardSize) return false
        if (board[row][col].isNotEmpty()) return false
        if (player != "X" && player != "O") return false

        board[row][col] = player
        moveHistory.addLast(Position(row, col))
        movesLog.add(player to Position(row, col))

        currentPlayer = if (currentPlayer == "X") "O" else "X"
        return true
    }

    /**
     * Checks if there is a winner.
     * Uses sliding window of winLength to support any board size / win length combination.
     * Returns the winning line positions, or null if no winner.
     */
    fun checkWinner(): List<Position>? {
        // Horizontals
        for (row in 0 until boardSize) {
            val line = checkLineWindow(
                (0 until boardSize).map { col -> Position(row, col) }
            )
            if (line != null) return line
        }

        // Verticals
        for (col in 0 until boardSize) {
            val line = checkLineWindow(
                (0 until boardSize).map { row -> Position(row, col) }
            )
            if (line != null) return line
        }

        // Diagonals top-left → bottom-right
        for (startRow in 0..boardSize - winLength) {
            for (startCol in 0..boardSize - winLength) {
                val positions = (0 until winLength).map { i ->
                    Position(startRow + i, startCol + i)
                }
                val line = checkExactLine(positions)
                if (line != null) return line
            }
        }

        // Diagonals top-right → bottom-left
        for (startRow in 0..boardSize - winLength) {
            for (startCol in winLength - 1 until boardSize) {
                val positions = (0 until winLength).map { i ->
                    Position(startRow + i, startCol - i)
                }
                val line = checkExactLine(positions)
                if (line != null) return line
            }
        }

        return null
    }

    /** Checks a line of arbitrary length using a sliding window of winLength */
    private fun checkLineWindow(positions: List<Position>): List<Position>? {
        if (positions.size < winLength) return null
        for (start in 0..positions.size - winLength) {
            val window = positions.subList(start, start + winLength)
            val line = checkExactLine(window)
            if (line != null) return line
        }
        return null
    }

    /** Checks if all positions in the list have the same non-empty value */
    private fun checkExactLine(positions: List<Position>): List<Position>? {
        val values = positions.map { board[it.row][it.col] }
        if (values.all { it.isNotEmpty() && it == values[0] }) return positions
        return null
    }

    fun isBoardFull(): Boolean =
        board.all { row -> row.all { cell -> cell.isNotEmpty() } }

    fun isGameOver(): Boolean = checkWinner() != null || isBoardFull()

    fun undoLastMove(): Position? {
        if (moveHistory.isEmpty()) return null
        val lastMove = moveHistory.removeLast()
        board[lastMove.row][lastMove.col] = ""
        if (movesLog.isNotEmpty()) movesLog.removeLast()
        currentPlayer = if (currentPlayer == "X") "O" else "X"
        return lastMove
    }

    fun reset() {
        for (row in 0 until boardSize)
            for (col in 0 until boardSize)
                board[row][col] = ""
        moveHistory.clear()
        movesLog.clear()
        currentPlayer = "X"
    }

    fun getCurrentPlayer(): String = currentPlayer

    fun getBoard(): List<List<String>> = board.map { it.toList() }

    fun getCellValue(row: Int, col: Int): String {
        if (row !in 0 until boardSize || col !in 0 until boardSize) return ""
        return board[row][col]
    }

    fun copy(): TicTacToeGame {
        val newGame = TicTacToeGame(boardSize, winLength)
        for (r in 0 until boardSize)
            for (c in 0 until boardSize)
                newGame.board[r][c] = this.board[r][c]
        for (move in this.moveHistory) newGame.moveHistory.addLast(move)
        for (log in this.movesLog) newGame.movesLog.add(log)
        newGame.currentPlayer = this.currentPlayer
        return newGame
    }

    fun setBoard(newBoard: List<List<String>>) {
        require(newBoard.size == boardSize)
        for (r in 0 until boardSize) {
            require(newBoard[r].size == boardSize)
            for (c in 0 until boardSize) board[r][c] = newBoard[r][c]
        }
    }

    fun setCurrentPlayer(player: String) {
        require(player == "X" || player == "O")
        currentPlayer = player
    }

    /** Returns move log as readable strings, e.g. ["X → (1,1)", "O → (0,2)"] */
    fun getMovesLogStrings(): List<String> =
        movesLog.mapIndexed { idx, (symbol, pos) ->
            "${idx + 1}. $symbol → (${pos.row},${pos.col})"
        }
}