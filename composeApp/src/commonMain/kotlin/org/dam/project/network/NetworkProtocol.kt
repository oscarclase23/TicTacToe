package org.dam.project.network

import kotlinx.serialization.Serializable

/**
 * Enum representing all possible message types in the network protocol.
 */
@Serializable
enum class MessageType {
    CONNECT,
    CREATE_GAME,
    MAKE_MOVE,
    GAME_STATE,
    MOVE_RESULT,
    ROUND_END,
    MATCH_END,
    RECORDS_SYNC,
    ERROR,
    RECONNECT,
    DISCONNECT,
    UNDO_REQUEST,
    UNDO_RESULT,
    JOIN_QUEUE,
    CANCEL_QUEUE,
    GAME_FOUND,
    LEAVE_GAME,
    OPPONENT_DISCONNECTED,
    OPPONENT_RECONNECTED,
    SURRENDER
}

/**
 * Request to join matchmaking queue.
 */
@Serializable
data class JoinQueueRequest(
    val playerName: String,
    val preferredBoardSize: Int = 3,
    val timeLimit: Int = 30,
    val totalRounds: Int = 3 // Preferred number of rounds
)

/**
 * Notification that a game has been found.
 */
@Serializable
data class GameFound(
    val matchId: String,
    val opponentName: String
)

/**
 * Enum representing game difficulty levels for PVE mode.
 */
@Serializable
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

/**
 * Represents a position on the game board.
 * JSON Example: {"row":1,"col":2}
 */
@Serializable
data class Position(
    val row: Int,
    val col: Int
)

/**
 * Game configuration sent when creating a new game.
 * JSON Example: {"boardSize":3,"winLength":3,"totalRounds":5,"difficulty":"HARD","timeLimit":30,"turboMode":false}
 */
@Serializable
data class GameConfig(
    val boardSize: Int,
    val winLength: Int,
    val totalRounds: Int,
    val difficulty: Difficulty,
    val timeLimit: Int = 30, // Time in seconds per move (default 30s, 10s for turbo)
    val turboMode: Boolean = false, // If true, timeLimit is 10 seconds
    val practiceMode: Boolean = false // If true, allows undo functionality
)

/**
 * Request to undo the last move (only in practice mode).
 * JSON Example: {"matchId":"abc"}
 */
@Serializable
data class UndoRequest(
    val matchId: String
)

/**
 * Result of undo operation.
 * JSON Example: {"success":true,"message":"Undone"}
 */
@Serializable
data class UndoResult(
    val success: Boolean,
    val message: String
)

/**
 * Client's move request (intention).
 * JSON Example: {"position":{"row":1,"col":2}}
 */
@Serializable
data class MoveRequest(
    val position: Position
)

/**
 * Server's response to a move request.
 * JSON Example: {"player":"X","position":{"row":1,"col":2},"valid":true}
 */
@Serializable
data class MoveResult(
    val player: String, // "X" or "O"
    val position: Position,
    val valid: Boolean,
    val errorMessage: String? = null
)

/**
 * Complete game state synchronization.
 * JSON Example: {"matchId":"abc123","board":[["X","O",""],["","X",""],["","","O"]],"boardSize":3,"currentPlayer":"X","nextPlayer":"X","currentRound":1,"scores":{"X":0,"O":0}}
 */
@Serializable
data class GameState(
    val matchId: String,
    val board: List<List<String>>, // Empty cells are ""
    val boardSize: Int,
    val currentPlayer: String, // "X" or "O"
    val nextPlayer: String, // "X" or "O" (same as currentPlayer for clarity)
    val currentRound: Int,
    val scores: Map<String, Int>, // "X" -> wins, "O" -> wins
    val playerXId: String, // ID of player controlling X
    val playerOId: String,  // ID of player controlling O (can be "AI")
    val timeLimit: Int = 30 // Negotiated time limit in seconds
)

/**
 * Round completion notification.
 * JSON Example: {"winner":"X","winningLine":[{"row":0,"col":0},{"row":1,"col":1},{"row":2,"col":2}]}
 */
@Serializable
data class RoundEnd(
    val winner: String?, // "X", "O", or null for draw
    val winningLine: List<Position>? = null, // null if draw
    val isDraw: Boolean = false,
    val reason: String? = null // Optional reason (e.g., "Opponent Disconnected")
)

/**
 * Match completion with final scores.
 * JSON Example: {"winner":"Player1","score":{"player1":3,"player2":1}}
 */
@Serializable
data class MatchEnd(
    val winner: String, // Player name or "DRAW"
    val score: Map<String, Int>, // player name -> rounds won
    val reason: String? = null // Optional reason (e.g., "Disconnect", "Timeout")
)

/**
 * Player statistics for records synchronization.
 */
@Serializable
data class PlayerRecord(
    val playerName: String,
    // Global aggregates
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,

    // PVP Stats
    val pvpWins: Int = 0,
    val pvpLosses: Int = 0,
    val pvpDraws: Int = 0,

    // PVE Stats
    val pveWins: Int = 0,
    val pveLosses: Int = 0,
    val pveDraws: Int = 0,

    // Advanced Metrics
    val winsByBoardSize: Map<Int, Int> = emptyMap(), // size -> wins
    val winVsAI: Map<Difficulty, Int> = emptyMap(), // difficulty -> wins
    val totalMoves: Int = 0,
    val totalTimeSeconds: Long = 0L,
    val favoriteMove: Position? = null,
    val moveFrequencies: Map<String, Int> = emptyMap() // "row,col" -> count
)

/**
 * Records data sent to client on connection.
 */
@Serializable
data class RecordsData(
    val records: List<PlayerRecord>
)

/**
 * Connection request from client.
 */
@Serializable
data class ConnectRequest(
    val playerName: String,
    val clientVersion: String = "1.0.0",
    val previousPlayerId: String? = null // For session resumption
)

/**
 * Connection response from server.
 */
@Serializable
data class ConnectResponse(
    val success: Boolean,
    val message: String,
    val playerId: String? = null
)

/**
 * Error message wrapper.
 */
@Serializable
data class ErrorMessage(
    val code: String,
    val message: String,
    val recoverable: Boolean = true
)

/**
 * Generic network message wrapper.
 * All messages are wrapped in this structure with a type and JSON payload.
 */
@Serializable
data class NetworkMessage(
    val type: MessageType,
    val payload: String // JSON string of the specific message type
)
