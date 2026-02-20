package org.dam.project.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.Difficulty
import org.dam.project.network.PlayerRecord
import org.dam.project.network.Position
import org.dam.project.network.RecordsData
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe manager for player records persistence.
 *
 * FIX 1: winVsAI and gamesVsAI now use Map<String, Int> to avoid
 * kotlinx.serialization issues with enum keys in maps.
 *
 * FIX 2: En empates PVE, winner llega como "DRAW" (no como nombre de jugador).
 * Se añade filtro winner != "DRAW" para evitar crear un registro falso
 * para un jugador llamado "DRAW".
 */
class RecordsManager(private val filePath: String = "records.json") {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val records = mutableMapOf<String, PlayerRecord>()
    private val lock = ReentrantReadWriteLock()

    init {
        load()
    }

    fun getSyncData(): RecordsData = lock.read {
        RecordsData(records = records.values.sortedByDescending { it.wins }.toList())
    }

    /**
     * Updates records after a match with full statistics.
     */
    fun updateRecords(
        winner: String,
        loser: String,
        isDraw: Boolean,
        isPVE: Boolean,
        boardSize: Int,
        difficulty: Difficulty? = null,
        winnerMoves: List<Position> = emptyList(),
        loserMoves: List<Position> = emptyList(),
        durationSeconds: Long = 0
    ) = lock.write {
        val totalMoves = winnerMoves.size + loserMoves.size
        // Approximate time per player: split total duration proportionally by moves
        val winnerTime = if (totalMoves > 0 && winnerMoves.isNotEmpty())
            (durationSeconds * winnerMoves.size / totalMoves) else durationSeconds / 2
        val loserTime = if (totalMoves > 0 && loserMoves.isNotEmpty())
            (durationSeconds * loserMoves.size / totalMoves) else durationSeconds / 2

        if (isDraw) {
            // FIX: Filtrar tanto "AI" como "DRAW" para evitar crear registros falsos.
            // En empates PVE, winner llega como "DRAW" desde handleMatchEnd,
            // y loser puede ser "AI". Sin este filtro se crearía un registro
            // para un jugador llamado "DRAW".
            if (winner != "AI" && winner != "DRAW") {
                updatePlayerStats(winner, false, true, isPVE, difficulty, boardSize, winnerMoves, winnerTime)
            }
            if (loser != "AI" && loser != "DRAW") {
                updatePlayerStats(loser, false, true, isPVE, difficulty, boardSize, loserMoves, loserTime)
            }
        } else {
            if (winner != "AI") updatePlayerStats(winner, true, false, isPVE, difficulty, boardSize, winnerMoves, winnerTime)
            if (loser != "AI") updatePlayerStats(loser, false, false, isPVE, difficulty, boardSize, loserMoves, loserTime)
        }

        save()
        println("[RecordsManager] Updated records for $winner vs $loser (draw=$isDraw, pve=$isPVE)")
    }

    private fun updatePlayerStats(
        playerName: String,
        isWinner: Boolean,
        isDraw: Boolean,
        isPVE: Boolean,
        opponentDifficulty: Difficulty?,
        boardSize: Int,
        moves: List<Position>,
        playerTimeSeconds: Long
    ) {
        if (playerName == "AI") return

        val current = records.getOrPut(playerName) { PlayerRecord(playerName = playerName) }

        // Global stats
        val newWins = current.wins + if (isWinner) 1 else 0
        val newLosses = current.losses + if (!isWinner && !isDraw) 1 else 0
        val newDraws = current.draws + if (isDraw) 1 else 0

        // FIX: currentStreak resets to 0 on loss or draw, increments only on win
        val newCurrentStreak = if (isWinner) current.currentStreak + 1 else 0
        val newBestStreak = maxOf(current.bestStreak, newCurrentStreak)

        // PVP/PVE stats
        val newPvpWins   = current.pvpWins   + if (!isPVE && isWinner) 1 else 0
        val newPvpLosses = current.pvpLosses + if (!isPVE && !isWinner && !isDraw) 1 else 0
        val newPvpDraws  = current.pvpDraws  + if (!isPVE && isDraw) 1 else 0
        val newPveWins   = current.pveWins   + if (isPVE && isWinner) 1 else 0
        val newPveLosses = current.pveLosses + if (isPVE && !isWinner && !isDraw) 1 else 0
        val newPveDraws  = current.pveDraws  + if (isPVE && isDraw) 1 else 0

        // Time tracking: accumulate actual player time and move count
        val newTotalMoves = current.totalMoves + moves.size
        val newTotalMoveTime = current.totalMoveTimeSeconds + playerTimeSeconds

        // Wins by board size
        val newWinsByBoardSize = current.winsByBoardSize.toMutableMap()
        if (isWinner) newWinsByBoardSize[boardSize] = (newWinsByBoardSize[boardSize] ?: 0) + 1

        // Wins vs AI difficulty — String keys to avoid enum serialization issues
        val newWinVsAI = current.winVsAI.toMutableMap()
        if (isWinner && isPVE && opponentDifficulty != null) {
            val key = opponentDifficulty.name
            newWinVsAI[key] = (newWinVsAI[key] ?: 0) + 1
        }

        // Games played vs AI difficulty
        val newGamesVsAI = current.gamesVsAI.toMutableMap()
        if (isPVE && opponentDifficulty != null) {
            val key = opponentDifficulty.name
            newGamesVsAI[key] = (newGamesVsAI[key] ?: 0) + 1
        }

        // Move frequencies and favorite move
        val newMoveFreqs = current.moveFrequencies.toMutableMap()
        moves.forEach { pos ->
            val key = "${pos.row},${pos.col}"
            newMoveFreqs[key] = (newMoveFreqs[key] ?: 0) + 1
        }
        val newFavoriteMove = if (newMoveFreqs.isNotEmpty()) {
            val maxEntry = newMoveFreqs.maxByOrNull { it.value }!!
            val parts = maxEntry.key.split(",")
            Position(parts[0].toInt(), parts[1].toInt())
        } else current.favoriteMove

        records[playerName] = current.copy(
            wins = newWins,
            losses = newLosses,
            draws = newDraws,
            currentStreak = newCurrentStreak,
            bestStreak = newBestStreak,
            pvpWins = newPvpWins,
            pvpLosses = newPvpLosses,
            pvpDraws = newPvpDraws,
            pveWins = newPveWins,
            pveLosses = newPveLosses,
            pveDraws = newPveDraws,
            winsByBoardSize = newWinsByBoardSize,
            winVsAI = newWinVsAI,
            gamesVsAI = newGamesVsAI,
            totalMoves = newTotalMoves,
            totalMoveTimeSeconds = newTotalMoveTime,
            moveFrequencies = newMoveFreqs,
            favoriteMove = newFavoriteMove
        )
    }

    private fun save() {
        try {
            val data = RecordsData(records = records.values.toList())
            File(filePath).writeText(json.encodeToString(data))
            println("[RecordsManager] Saved ${records.size} records to $filePath")
        } catch (e: Exception) {
            println("[RecordsManager] Error saving: ${e.message}")
        }
    }

    private fun load() {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val data = json.decodeFromString<RecordsData>(file.readText())
                records.clear()
                data.records.forEach { records[it.playerName] = it }
                println("[RecordsManager] Loaded ${records.size} records from $filePath")
            } else {
                println("[RecordsManager] No records file, starting fresh")
                save()
            }
        } catch (e: Exception) {
            println("[RecordsManager] Error loading: ${e.message}")
        }
    }

    fun getPlayerRecord(playerName: String): PlayerRecord? = lock.read { records[playerName] }

    fun getTopPlayers(limit: Int = 10): List<PlayerRecord> = lock.read {
        records.values.sortedByDescending { it.wins }.take(limit)
    }
}