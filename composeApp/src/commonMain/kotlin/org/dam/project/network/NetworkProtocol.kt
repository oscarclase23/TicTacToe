package org.dam.project.network

import kotlinx.serialization.Serializable

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

@Serializable
data class JoinQueueRequest(
    val playerName: String,
    val preferredBoardSize: Int = 3,
    val timeLimit: Int = 30,
    val totalRounds: Int = 3,
    val turboMode: Boolean = false   // NEW: explicit turbo flag from client
)

@Serializable
data class GameFound(
    val matchId: String,
    val opponentName: String
)

@Serializable
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD
}

@Serializable
data class Position(val row: Int, val col: Int)

@Serializable
data class GameConfig(
    val boardSize: Int,
    val winLength: Int,
    val totalRounds: Int,
    val difficulty: Difficulty,
    val timeLimit: Int = 30,
    val turboMode: Boolean = false,
    val practiceMode: Boolean = false
)

@Serializable
data class UndoRequest(val matchId: String)

@Serializable
data class UndoResult(val success: Boolean, val message: String)

@Serializable
data class MoveRequest(val position: Position)

@Serializable
data class MoveResult(
    val player: String,
    val position: Position,
    val valid: Boolean,
    val errorMessage: String? = null
)

@Serializable
data class GameState(
    val matchId: String,
    val board: List<List<String>>,
    val boardSize: Int,
    val currentPlayer: String,
    val nextPlayer: String,
    val currentRound: Int,
    val totalRounds: Int = 3,
    val scores: Map<String, Int>,
    val playerXId: String,
    val playerOId: String,
    val timeLimit: Int = 30,
    val practiceMode: Boolean = false,
    val movesLog: List<String> = emptyList()
)

@Serializable
data class RoundEnd(
    val winner: String?,
    val winningLine: List<Position>? = null,
    val isDraw: Boolean = false,
    val reason: String? = null
)

@Serializable
data class MatchEnd(
    val winner: String,
    val score: Map<String, Int>,
    val reason: String? = null
)

/**
 * Player statistics record.
 * - totalMoveTimeSeconds: suma total de segundos de TODOS los movimientos
 * - avgTimePerMove se calcula como totalMoveTimeSeconds / totalMoves
 * - gamesVsAI: partidas jugadas vs cada dificultad (para calcular % victorias)
 *
 * NOTE: winVsAI and gamesVsAI use String keys (not Difficulty enum) to avoid
 * kotlinx.serialization issues with enum map keys.
 */
@Serializable
data class PlayerRecord(
    val playerName: String,
    // Global
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    // PVP
    val pvpWins: Int = 0,
    val pvpLosses: Int = 0,
    val pvpDraws: Int = 0,
    // PVE
    val pveWins: Int = 0,
    val pveLosses: Int = 0,
    val pveDraws: Int = 0,
    // Board size wins — key is boardSize as Int
    val winsByBoardSize: Map<Int, Int> = emptyMap(),
    // Wins vs AI difficulty — key is difficulty name string (e.g. "EASY")
    val winVsAI: Map<String, Int> = emptyMap(),
    // Games played vs AI difficulty — key is difficulty name string
    val gamesVsAI: Map<String, Int> = emptyMap(),
    // Move metrics
    val totalMoves: Int = 0,
    val totalMoveTimeSeconds: Long = 0L,
    // Favorite position
    val favoriteMove: Position? = null,
    val moveFrequencies: Map<String, Int> = emptyMap()
)

@Serializable
data class RecordsData(val records: List<PlayerRecord>)

@Serializable
data class ConnectRequest(
    val playerName: String,
    val clientVersion: String = "1.0.0",
    val previousPlayerId: String? = null
)

@Serializable
data class ConnectResponse(
    val success: Boolean,
    val message: String,
    val playerId: String? = null
)

@Serializable
data class ErrorMessage(
    val code: String,
    val message: String,
    val recoverable: Boolean = true
)

@Serializable
data class NetworkMessage(
    val type: MessageType,
    val payload: String
)