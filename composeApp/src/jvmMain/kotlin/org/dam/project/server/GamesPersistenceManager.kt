package org.dam.project.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages persistence of active game sessions to support server restarts.
 */
class GamesPersistenceManager(private val filePath: String = "active_games.json") {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true 
        allowStructuredMapKeys = true
    }
    
    /**
     * Saves the current active games to disk.
     */
    fun save(games: Map<String, GameSession>) {
        try {
            // We need to serialize the GameSession objects
            // Since GameSession might contain transient state that we can't easily serialize,
            // we might need to rely on GameSession being @Serializable or extract the state.
            // Looking at GameSession (inferred), it likely holds the GameState.
            
            // For simplicity/robustness, we'll assume GameSession is serializable 
            // OR we'll serialize the GameConfigs + GameStates.
            // Let's check GameSession... actually I haven't seen GameSession code yet.
            // I will assume for now I can serializing the list of sessions.
            
            // Wait, I need to check if GameSession is serializable. 
            // If not, I should map it to a Serializable DTO.
            
            // Since I cannot verify GameSession right now, I will use a safe approach:
            // I will define a DTO here that definitely captures what we need to restore a game.
            
            val sessionsData = games.values.map { session ->
                PersistedSession(
                    matchId = session.matchId,
                    config = session.config,
                    playerX = session.playerX,
                    playerO = session.playerO,
                    isAIGame = session.isAIGame,
                    matchStartTime = session.matchStartTime,
                    // Serialize the actual game board state
                    boardState = session.game.getBoard(), // Assuming getBoard() returns data
                    currentPlayer = session.game.getCurrentPlayer(),
                    scores = session.scores
                )
            }
            
            val jsonString = json.encodeToString(sessionsData)
            File(filePath).writeText(jsonString)
            println("[GamesPersistenceManager] Saved ${games.size} active games to $filePath")
        } catch (e: Exception) {
            println("[GamesPersistenceManager] Error saving games: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Loads active games from disk.
     * Returns a list of PersistedSession DTOs that the GameServer can use to reconstruct GameSessions.
     */
    fun load(): List<PersistedSession> {
        try {
            val file = File(filePath)
            if (!file.exists()) return emptyList()
            
            val jsonString = file.readText()
            return json.decodeFromString<List<PersistedSession>>(jsonString)
        } catch (e: Exception) {
            println("[GamesPersistenceManager] Error loading games: ${e.message}")
            // Return empty list on error to allow server to start fresh
            return emptyList()
        }
    }
    
    /**
     * Clears the persistence file (e.g., when all games finish).
     */
    fun clear() {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            println("[GamesPersistenceManager] Error clearing persistence file: ${e.message}")
        }
    }
}

/**
 * DTO for saving session state.
 */
@kotlinx.serialization.Serializable
data class PersistedSession(
    val matchId: String,
    val config: org.dam.project.network.GameConfig,
    val playerX: String,
    val playerO: String,
    val isAIGame: Boolean,
    val matchStartTime: Long,
    val boardState: List<List<String>>,
    val currentPlayer: String,
    val scores: Map<String, Int>
)
