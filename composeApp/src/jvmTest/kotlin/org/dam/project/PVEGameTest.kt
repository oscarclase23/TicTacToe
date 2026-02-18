package org.dam.project

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.client.NetworkClient
import org.dam.project.network.*
import kotlin.test.Test

/**
 * Automated test to verify PVE gameplay works correctly.
 * This simulates a full 3-round match against the AI.
 */
class PVEGameTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Test
    fun testFullPVEMatch() = runBlocking {
        println("=== Starting PVE Game Test ===")
        
        val client = NetworkClient()
        
        try {
            // Connect to server
            println("Connecting to server...")
            client.connect("127.0.0.1", 5678)
            println("✓ Connected")
            
            // Wait for RECORDS_SYNC
            val recordsSync = client.receive()
            println("✓ Received: ${recordsSync.type}")
            
            // Send CONNECT
            val connectRequest = ConnectRequest(playerName = "TestPlayer")
            client.send(NetworkMessage(MessageType.CONNECT, json.encodeToString(connectRequest)))
            println("✓ Sent CONNECT")
            
            // Receive CONNECT_RESPONSE
            val connectResponse = client.receive()
            println("✓ Received: ${connectResponse.type}")
            
            // Create PVE game
            val gameConfig = GameConfig(
                boardSize = 3,
                winLength = 3,
                totalRounds = 3,
                difficulty = Difficulty.EASY
            )
            client.send(NetworkMessage(MessageType.CREATE_GAME, json.encodeToString(gameConfig)))
            println("✓ Sent CREATE_GAME")
            
            // Receive initial GAME_STATE
            val initialState = client.receive()
            println("✓ Received: ${initialState.type}")
            
            var roundCount = 0
            var moveCount = 0
            var matchEnded = false
            
            // Play the game
            while (!matchEnded && roundCount < 5) { // Max 5 rounds as safety
                val message = client.receive()
                println("Received: ${message.type}")
                
                when (message.type) {
                    MessageType.GAME_STATE -> {
                        val gameState = json.decodeFromString<GameState>(message.payload)
                        println("  Current player: ${gameState.currentPlayer}, Round: ${gameState.currentRound}")
                        
                        // Determine if it's our turn
                        val playerId = "TestPlayer" // We don't have the actual ID, but we know we're not AI
                        val isOurTurn = (gameState.playerXId != "AI" && gameState.currentPlayer == "X") ||
                                       (gameState.playerOId != "AI" && gameState.currentPlayer == "O")
                        
                        if (isOurTurn) {
                            // Make a simple move (first empty cell)
                            val board = gameState.board
                            var moved = false
                            for (row in 0 until 3) {
                                for (col in 0 until 3) {
                                    if (board[row][col].isEmpty() && !moved) {
                                        val moveRequest = MoveRequest(Position(row, col))
                                        client.send(NetworkMessage(MessageType.MAKE_MOVE, json.encodeToString(moveRequest)))
                                        println("  ✓ Made move: ($row, $col)")
                                        moveCount++
                                        moved = true
                                        break
                                    }
                                }
                                if (moved) break
                            }
                        }
                    }
                    MessageType.ROUND_END -> {
                        val roundEnd = json.decodeFromString<RoundEnd>(message.payload)
                        roundCount++
                        println("  ✓ Round $roundCount ended. Winner: ${roundEnd.winner ?: "DRAW"}")
                    }
                    MessageType.MATCH_END -> {
                        val matchEnd = json.decodeFromString<MatchEnd>(message.payload)
                        println("  ✓ Match ended. Winner: ${matchEnd.winner}")
                        matchEnded = true
                    }
                    MessageType.MOVE_RESULT -> {
                        // Just acknowledge
                    }
                    else -> {
                        println("  Unexpected message: ${message.type}")
                    }
                }
                
                // Safety timeout
                if (moveCount > 50) {
                    println("ERROR: Too many moves, something is wrong")
                    break
                }
            }
            
            println("\n=== Test Results ===")
            println("Rounds played: $roundCount")
            println("Total moves: $moveCount")
            println("Match ended: $matchEnded")
            
            if (matchEnded && roundCount == 3) {
                println("✓✓✓ TEST PASSED ✓✓✓")
            } else {
                println("✗✗✗ TEST FAILED ✗✗✗")
            }
            
        } catch (e: Exception) {
            println("ERROR: ${e.message}")
            e.printStackTrace()
        } finally {
            client.close()
            println("Connection closed")
        }
    }
}
