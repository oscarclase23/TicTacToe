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
 * Automatically loads and saves records to a JSON file.
 */
class RecordsManager(private val filePath: String = "records.json") {
    
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val records = mutableMapOf<String, PlayerRecord>()
    private val lock = ReentrantReadWriteLock()
    
    init {
        load()
    }
    
    /**
     * Gets current records data for client synchronization.
     */
    fun getSyncData(): RecordsData = lock.read {
        RecordsData(records = records.values.toList())
    }
    
    /**
     * Updates records after a match completion with detailed statistics.
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
        // Update Winner (or Player 1 in draw)
        updatePlayerStats(
            playerName = winner,
            isWinner = !isDraw,
            isDraw = isDraw,
            isPVE = isPVE,
            opponentDifficulty = difficulty,
            boardSize = boardSize,
            moves = winnerMoves,
            duration = durationSeconds
        )
        
        // Update Loser (or Player 2 in draw)
        // Note: For AI, we don't track stats, so check if loser is "AI" (though typically we filter before calling)
        if (loser != "AI") {
            updatePlayerStats(
                playerName = loser,
                isWinner = false,
                isDraw = isDraw,
                isPVE = isPVE,
                opponentDifficulty = difficulty, // Same difficulty applies
                boardSize = boardSize,
                moves = loserMoves,
                duration = durationSeconds
            )
        }
        
        save()
        println("[RecordsManager] Updated detailed records for $winner vs $loser")
    }
    
    private fun updatePlayerStats(
        playerName: String,
        isWinner: Boolean,
        isDraw: Boolean,
        isPVE: Boolean,
        opponentDifficulty: Difficulty?,
        boardSize: Int,
        moves: List<Position>,
        duration: Long
    ) {
        if (playerName == "AI") return
        
        val current = records.getOrPut(playerName) {
            PlayerRecord(playerName = playerName)
        }
        
        // Update Global Stats
        val newWins = current.wins + (if (isWinner) 1 else 0)
        val newLosses = current.losses + (if (!isWinner && !isDraw) 1 else 0)
        val newDraws = current.draws + (if (isDraw) 1 else 0)
        val newCurrentStreak = if (isWinner) current.currentStreak + 1 else 0
        val newBestStreak = maxOf(current.bestStreak, newCurrentStreak)
        
        // Update PVP/PVE Stats
        val newPvpWins = current.pvpWins + (if (!isPVE && isWinner) 1 else 0)
        val newPvpLosses = current.pvpLosses + (if (!isPVE && !isWinner && !isDraw) 1 else 0)
        val newPvpDraws = current.pvpDraws + (if (!isPVE && isDraw) 1 else 0)
        
        val newPveWins = current.pveWins + (if (isPVE && isWinner) 1 else 0)
        val newPveLosses = current.pveLosses + (if (isPVE && !isWinner && !isDraw) 1 else 0)
        val newPveDraws = current.pveDraws + (if (isPVE && isDraw) 1 else 0)
        
        // Update Advanced Metrics
        val newTotalMoves = current.totalMoves + moves.size
        val newTotalTime = current.totalTimeSeconds + duration
        
        // Wins by Board Size
        val newWinsByBoardSize = current.winsByBoardSize.toMutableMap()
        if (isWinner) {
            newWinsByBoardSize[boardSize] = (newWinsByBoardSize[boardSize] ?: 0) + 1
        }
        
        // Wins vs AI Difficulty
        val newWinVsAI = current.winVsAI.toMutableMap()
        if (isWinner && isPVE && opponentDifficulty != null) {
            newWinVsAI[opponentDifficulty] = (newWinVsAI[opponentDifficulty] ?: 0) + 1
        }
        
        // Move Frequencies & Favorite Move
        val newMoveFrequencies = current.moveFrequencies.toMutableMap()
        moves.forEach { pos ->
            val key = "${pos.row},${pos.col}"
            newMoveFrequencies[key] = (newMoveFrequencies[key] ?: 0) + 1
        }
        
        // Calculate new favorite move
        var newFavoriteMove = current.favoriteMove
        if (newMoveFrequencies.isNotEmpty()) {
            val maxEntry = newMoveFrequencies.maxByOrNull { it.value }
            if (maxEntry != null) {
                val parts = maxEntry.key.split(",")
                if (parts.size == 2) {
                    newFavoriteMove = Position(parts[0].toInt(), parts[1].toInt())
                }
            }
        }
        
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
            totalMoves = newTotalMoves,
            totalTimeSeconds = newTotalTime,
            moveFrequencies = newMoveFrequencies,
            favoriteMove = newFavoriteMove
        )
    }
    
    /**
     * Saves current records to JSON file.
     */
    private fun save() {
        try {
            val recordsData = RecordsData(records = records.values.toList())
            val jsonString = json.encodeToString(recordsData)
            File(filePath).writeText(jsonString)
            println("[RecordsManager] Saved ${records.size} records to $filePath")
        } catch (e: Exception) {
            println("[RecordsManager] Error saving records: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Loads records from JSON file.
     */
    private fun load() {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val jsonString = file.readText()
                val recordsData = json.decodeFromString<RecordsData>(jsonString)
                records.clear()
                recordsData.records.forEach { record ->
                    records[record.playerName] = record
                }
                println("[RecordsManager] Loaded ${records.size} records from $filePath")
            } else {
                println("[RecordsManager] No existing records file, starting fresh")
                save()
            }
        } catch (e: Exception) {
            println("[RecordsManager] Error loading records: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Gets a specific player's record.
     */
    fun getPlayerRecord(playerName: String): PlayerRecord? = lock.read {
        records[playerName]
    }
    
    /**
     * Gets all player records sorted by best score.
     */
    fun getTopPlayers(limit: Int = 10): List<PlayerRecord> = lock.read {
        records.values
            .sortedByDescending { it.bestStreak }
            .take(limit)
    }
}
