package org.dam.project.server

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.game.TicTacToeGame
import org.dam.project.network.*
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.dam.project.util.LoggerConfig

/**
 * Main game server that manages client connections and game sessions.
 */
class GameServer(val config: ServerConfig) {
    
    constructor(configFile: String) : this(loadConfig(configFile))
    
    constructor(port: Int) : this(ServerConfig("localhost", port, 10))
    
    val records = RecordsManager("records.json")
    
    private val clients = ConcurrentHashMap<String, ClientHandler>()
    private val activeGames = ConcurrentHashMap<String, GameSession>()
    // private val waitingQueue -- Removed in favor of waitingQueues map

    private val json = Json { ignoreUnknownKeys = true }
    private val persistenceManager = GamesPersistenceManager()
    
    // Helper to resolve player name from ID
    private fun getPlayerName(playerId: String): String {
        return if (playerId == "AI") "AI" else clients[playerId]?.playerName ?: "Unknown"
    }

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Starts the server and begins accepting clients.
     */
    fun start() {
        try {
            // Load active games from disk
            val loadedSessions = persistenceManager.load()
            var restoredCount = 0
            loadedSessions.forEach { data ->
                try {
                    // Reconstruct GameSession
                    val session = GameSession(data.matchId, data.config, data.playerX, data.playerO, data.isAIGame)
                    session.scores.putAll(data.scores)
                    // Note: matchStartTime is effectively reset to "now" on restart, which is acceptable for MVP
                    
                    // Restore board state
                    session.game.setBoard(data.boardState)
                    session.game.setCurrentPlayer(data.currentPlayer)
                    
                    activeGames[data.matchId] = session
                    restoredCount++
                    
                    // Restart timers if applicable
                    startSessionTimer(session)
                } catch (e: Exception) {
                    println("[GameServer] Failed to restore session ${data.matchId}: ${e.message}")
                }
            }
            if (restoredCount > 0) {
                println("[GameServer] Restored $restoredCount active games from persistence")
            }

            // Bind to specific host address if possible, otherwise fallback to port
            try {
                serverSocket = ServerSocket(config.port, 50, java.net.InetAddress.getByName(config.host))
            } catch (e: Exception) {
                println("[GameServer] Warning: Failed to bind to ${config.host}, binding to all interfaces on port ${config.port}")
                serverSocket = ServerSocket(config.port)
            }
            println("[GameServer] Server started on ${config.host}:${config.port}")
            println("[GameServer] Max clients: ${config.maxClients}")
            
            // Accept clients in loop
            scope.launch {
                println("[GameServer] Accept loop started")
                while (isActive) {
                    try {
                        // Blocking accept
                        val socket = serverSocket?.accept() ?: break
                        
                        println("[GameServer] New connection request from ${socket.inetAddress}")
                        
                        // Check client limit
                        if (clients.size >= config.maxClients) {
                            println("[GameServer] Max clients reached, rejecting connection")
                            socket.close()
                            continue
                        }
                        
                        // CRITICAL: Handle client in a NEW coroutine so we don't block the accept loop
                        scope.launch {
                            try {
                                val handler = ClientHandler(socket, this@GameServer)
                                handler.handle()
                            } catch (e: Exception) {
                                println("[GameServer] Error in client handler: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            println("[GameServer] Error accepting client: ${e.message}")
                        }
                    }
                }
            }
            
            println("[GameServer] Server is running. Press Ctrl+C to stop.")
            
        } catch (e: Exception) {
            println("[GameServer] Failed to start server: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Stops the server gracefully.
     */
    fun stop() {
        println("[GameServer] Stopping server...")
        scope.cancel()
        serverSocket?.close()
        clients.values.forEach { it.close() }
        println("[GameServer] Server stopped")
    }
    
    /**
     * Registers a new client.
     * 
     * @param handler Client handler
     * @param playerName Player's chosen name
     * @return Assigned player ID
     */
    fun registerClient(handler: ClientHandler, playerName: String, previousPlayerId: String? = null): String {
        // Session Resumption Logic
        if (previousPlayerId != null) {
            // Check if this player is in any active game
            val existingGame = activeGames.values.find { it.playerX == previousPlayerId || it.playerO == previousPlayerId }
            
            if (existingGame != null) {
                println("[GameServer] Resuming session for player $previousPlayerId (${existingGame.matchId})")
                
                // Re-assign handler
                clients[previousPlayerId] = handler
                handler.setMatchId(existingGame.matchId)
                handler.playerId = previousPlayerId
                handler.playerName = playerName 
                
                // Update session connection status
                existingGame.setPlayerConnected(previousPlayerId, true)
                
                // Send latest game state immediately
                scope.launch {
                    delay(500)
                    val gameState = existingGame.getGameState()
                    handler.sendMessage(MessageType.GAME_STATE, json.encodeToString(gameState))
                    
                    // Notify opponent that I'm back
                    val opponentId = if (existingGame.playerX == previousPlayerId) existingGame.playerO else existingGame.playerX
                    if (opponentId != "AI") {
                         clients[opponentId]?.sendMessage(MessageType.OPPONENT_RECONNECTED, "")
                    }
                }
                
                return previousPlayerId
            }
        }

        // Default behavior: New player
        val playerId = UUID.randomUUID().toString()
        clients[playerId] = handler
        println("[GameServer] Registered client: $playerId ($playerName)")
        return playerId
    }
    
    /**
     * Unregisters a client.
     */
    fun unregisterClient(playerId: String) {
        clients.remove(playerId)
        
        // Check if player was in an active game
        // New Logic: Mark as disconnected, DO NOT Forfeit immediately
        handlePlayerDisconnect(playerId)
        
        println("[GameServer] Unregistered client: $playerId")
    }
    
    /**
     * Handles a player disconnection.
     * Marks them as disconnected but keeps the game alive.
     */
    fun handlePlayerDisconnect(playerId: String) {
        val activeSession = activeGames.values.find { 
            it.playerX == playerId || it.playerO == playerId 
        }
        
        if (activeSession != null) {
            println("[GameServer] Player $playerId disconnected from game ${activeSession.matchId}")
            
            // Mark as disconnected
            activeSession.setPlayerConnected(playerId, false)
            
            // If it's a PVE game, we might want to pause or just leave it. 
            // For now, let's treat PVE disconnect as "pause" implicitly (timer continues? maybe stop timer)
            if (activeSession.isAIGame) {
                activeSession.stopTimer()
                // Game persists in activeGames, so player can reconnect
            } else {
                // PVP: Notify opponent
                val opponentId = if (activeSession.playerX == playerId) activeSession.playerO else activeSession.playerX
                val opponentClient = clients[opponentId]
                
                if (opponentClient != null) {
                    println("[GameServer] Notifying opponent $opponentId of disconnection")
                    opponentClient.sendMessage(MessageType.OPPONENT_DISCONNECTED, "")
                }
                
                // Stop timer to be fair? Or let it run out?
                // Requirement says "Tablero bloqueado", which implies pause.
                activeSession.stopTimer()
            }
            persistenceManager.save(activeGames)
        }
    }
    
    /**
     * Processes a surrender request.
     * This is the old "Forfeit" logic, explicitly triggered.
     */
    fun processSurrender(matchId: String, playerId: String) {
        val activeSession = activeGames[matchId] ?: return
        
        println("[GameServer] Player $playerId SURRENDERED game $matchId")
        val opponentId = if (activeSession.playerX == playerId) activeSession.playerO else activeSession.playerX
        
        // Determine winner (Opponent wins)
        val opponentName = getPlayerName(opponentId)
        val forfeitScore = mapOf(
            getPlayerName(playerId) to 0,
            opponentName to 1 
        )
        
        val matchEnd = MatchEnd(
            winner = opponentName,
            score = forfeitScore,
            reason = "Opponent Surrendered"
        )
        
        // Notify both (if connected)
        broadcastToGame(matchId, MessageType.MATCH_END, json.encodeToString(matchEnd))
        
        // Update records
        records.updateRecords(
            winner = opponentName,
            loser = getPlayerName(playerId),
            isDraw = false,
            isPVE = activeSession.isAIGame,
            boardSize = activeSession.config.boardSize,
            difficulty = activeSession.config.difficulty
        )
        
        // Cleanup session
        activeSession.stopTimer()
        activeGames.remove(activeSession.matchId)
        persistenceManager.save(activeGames)
    }
    
    /**
     * Creates a PVE game (player vs AI).
     * 
     * @param playerId Player's ID
     * @param config Game configuration
     * @return Match ID
     */

    fun createPVEGame(playerId: String, config: GameConfig): String {
        // ... implementation ...
        val matchId = UUID.randomUUID().toString()
        
        // Randomize starting player: 50% chance AI goes first
        val aiGoesFirst = kotlin.random.Random.nextBoolean()
        
        val session = if (aiGoesFirst) {
            println("[GameServer] AI will go first (AI=X, Player=O)")
            // AI is X (goes first), Player is O
            GameSession(
                matchId = matchId,
                config = config,
                playerX = "AI",
                playerO = playerId,
                isAIGame = true
            )
        } else {
            println("[GameServer] Player will go first (Player=X, AI=O)")
            // Player is X (goes first), AI is O
            GameSession(
                matchId = matchId,
                config = config,
                playerX = playerId,
                playerO = "AI",
                isAIGame = true
            )
        }
        
        activeGames[matchId] = session
        persistenceManager.save(activeGames) // Save new game
        println("[GameServer] Created PVE game: $matchId (difficulty: ${config.difficulty}, practice=${config.practiceMode})")
        
        // Start timer for the first player
        startSessionTimer(session)
        
        return matchId
    }

    /**
     * Processes an undo request.
     */
    suspend fun processUndo(matchId: String, playerId: String) {
        val session = activeGames[matchId] ?: return
        
        val result = session.handleUndo(playerId)
        
        // Send result to requesting player
        // Note: Generic UNDO_RESULT message not implemented in client handler yet, 
        // but client UI updates based on GAME_STATE mostly.
        
        if (result.success) {
            println("[GameServer] Undo successful for $playerId")
            
            // Broadcast new state
            val gameState = session.getGameState()
            broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(gameState))
            
            persistenceManager.save(activeGames) // Save state (undo)
            
            // Restart timer for the current player
            startSessionTimer(session)
        } else {
            println("[GameServer] Undo failed: ${result.message}")
            clients[playerId]?.sendMessage(MessageType.ERROR, json.encodeToString(ErrorMessage("UNDO_FAILED", result.message)))
        }
    }

    
    /**
     * Processes a move in a game session.
     * 
     * @param matchId Match ID
     * @param playerId Player making the move
     * @param position Position to play
     */
    suspend fun processMove(matchId: String, playerId: String, position: Position) {
        println("[GameServer] Processing move: matchId=$matchId, playerId=$playerId, position=(${position.row},${position.col})")
        val session = activeGames[matchId] ?: run {
            println("[GameServer] Game not found: $matchId")
            return
        }
        
        // Validate and make move
        val moveResult = session.makeMove(playerId, position)
        println("[GameServer] Move result: valid=${moveResult.valid}, player=${moveResult.player}")
        
        // Send move result
        broadcastToGame(matchId, MessageType.MOVE_RESULT, json.encodeToString(moveResult))
        
        if (!moveResult.valid) {
            println("[GameServer] Move was invalid: ${moveResult.errorMessage}")
            return
        }
        
        // CRITICAL: Stop the timer for the current turn immediately!
        // This prevents the timer from firing while we process round end or wait for animations.
        session.stopTimer()
        
        // Broadcast updated game state
        val gameState = session.getGameState()
        println("[GameServer] Broadcasting game state after player move")
        broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(gameState))
        
        persistenceManager.save(activeGames) // Save state (move made)
        
        // Check for round end
        val roundEnd = session.checkRoundEnd()
        if (roundEnd != null) {
            println("[GameServer] Round ended: winner=${roundEnd.winner}")
            broadcastToGame(matchId, MessageType.ROUND_END, json.encodeToString(roundEnd))
            
            // Check for match end
            val matchEnd = session.checkMatchEnd()
            if (matchEnd != null) {
                println("[GameServer] Match ended: winner=${matchEnd.winner}")
                broadcastToGame(matchId, MessageType.MATCH_END, json.encodeToString(matchEnd))
                
                // Update records
                val winnerId = if (matchEnd.winner == "DRAW") session.playerX else matchEnd.winner
                val loserId = if (matchEnd.winner == "DRAW") session.playerO else (if (winnerId == session.playerX) session.playerO else session.playerX)
                val isDraw = matchEnd.winner == "DRAW"
                
                // Get real names
                val winnerName = getPlayerName(winnerId)
                val loserName = getPlayerName(loserId)
                
                // Calculate duration
                val duration = (System.currentTimeMillis() - session.matchStartTime) / 1000
                
                // Filter moves
                val winnerMoves = session.matchMoves.filter { it.first == winnerId }.map { it.second }
                val loserMoves = session.matchMoves.filter { it.first == loserId }.map { it.second }
                
                records.updateRecords(
                    winner = winnerName,
                    loser = loserName,
                    isDraw = isDraw,
                    isPVE = session.isAIGame,
                    boardSize = session.config.boardSize,
                    difficulty = session.config.difficulty,
                    winnerMoves = winnerMoves,
                    loserMoves = loserMoves,
                    durationSeconds = duration
                )
                
                // Remove game
                activeGames.remove(matchId)
                persistenceManager.save(activeGames) // Save change (removed)
            } else {
                // Start next round
                delay(2000) // Wait for 2 seconds so players can see the round result
                session.nextRound()
                val newGameState = session.getGameState()
                broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(newGameState))
                
                // Check if it's AI turn in the new round
                checkAndTriggerAIMove(session)
                
                // Start timer for the new round
                startSessionTimer(session)
            }
        } else {
            // Check if it's AI turn in the current round
            checkAndTriggerAIMove(session)
            
            // Restart timer for next player
            startSessionTimer(session)
        }
    }

    /**
     * Checks if it's AI's turn and triggers the move if necessary.
     */
    private suspend fun checkAndTriggerAIMove(session: GameSession) {
        if (session.isAIGame && !session.game.isGameOver()) {
            // Check if it's AI's turn by comparing current player with AI's symbol
            val aiSymbol = if (session.playerX == "AI") "X" else "O"
            val isAITurn = session.game.getCurrentPlayer() == aiSymbol
            
            if (isAITurn) {
                // AI's turn - execute in background with error handling
                println("[GameServer] AI's turn, calculating move...")
                try {
                    delay(500) // Small delay for better UX
                    makeAIMove(session)
                } catch (e: Exception) {
                    println("[GameServer] ERROR: AI move failed: ${e.message}")
                    e.printStackTrace()
                    // Try to recover with a random move
                    try {
                        val randomMove = getRandomMove(session.game)
                        if (randomMove != null) {
                            println("[GameServer] Attempting recovery with random move: $randomMove")
                            processMove(session.matchId, "AI", randomMove)
                        }
                    } catch (e2: Exception) {
                        println("[GameServer] CRITICAL: Recovery failed: ${e2.message}")
                    }
                }
            } else {
                println("[GameServer] Waiting for next player move. Current player: ${session.game.getCurrentPlayer()}")
            }
        } else {
            println("[GameServer] Waiting for next player move. Current player: ${session.game.getCurrentPlayer()}")
        }
    }
    
    /**
     * Gets a random valid move as fallback.
     */
    private fun getRandomMove(game: TicTacToeGame): Position? {
        val emptyPositions = mutableListOf<Position>()
        for (row in 0 until game.boardSize) {
            for (col in 0 until game.boardSize) {
                if (game.getCellValue(row, col).isEmpty()) {
                    emptyPositions.add(Position(row, col))
                }
            }
        }
        return emptyPositions.randomOrNull()
    }
    
    /**
     * Makes an AI move.
     */
    private suspend fun makeAIMove(session: GameSession) {
        // Determine AI's symbol based on which player it is
        val aiSymbol = if (session.playerX == "AI") "X" else "O"
        val aiMove = GameAI.getBestMove(session.game, session.config.difficulty, aiSymbol)
        processMove(session.matchId, "AI", aiMove)
    }
    
    /**
     * Broadcasts a message to all players in a game.
     */
    fun broadcastToGame(matchId: String, type: MessageType, payload: String) {
        val session = activeGames[matchId] ?: return
        
        listOf(session.playerX, session.playerO).forEach { playerId ->
            if (playerId != "AI") {
                clients[playerId]?.sendMessage(type, payload)
            }
        }
    }
    
    /**
     * Gets a game session by ID.
     */
    fun getSession(matchId: String): GameSession? = activeGames[matchId]
    /**
     * Handles a timeout for a specific match.
     */
    private fun handleTimeout(matchId: String) {
        scope.launch {
            val session = activeGames[matchId] ?: return@launch
            
            println("[GameServer] Timeout for match $matchId")
            
            // Current player loses the round
            val currentPlayer = session.game.getCurrentPlayer()
            val loserId = session.getPlayerId(currentPlayer)
            
            // Determine winner (the opponent)
            val winnerSymbol = if (currentPlayer == "X") "O" else "X"
            val winnerId = session.getPlayerId(winnerSymbol)
            
            // Construct artificial RoundEnd
            val roundEnd = RoundEnd(
                winner = winnerSymbol,
                winningLine = null, // No winning line for timeout
                isDraw = false
            )
            
            // Update scores
            session.scores[winnerId] = (session.scores[winnerId] ?: 0) + 1
            
            println("[GameServer] Timeout! Winner is $winnerSymbol ($winnerId)")
            broadcastToGame(matchId, MessageType.ROUND_END, json.encodeToString(roundEnd))
            
            // Check for match end (copied logic from processMove)
            val matchEnd = session.checkMatchEnd()
            if (matchEnd != null) {
                println("[GameServer] Match ended (by timeout): winner=${matchEnd.winner}")
                broadcastToGame(matchId, MessageType.MATCH_END, json.encodeToString(matchEnd))
                
                // Update records
                val winnerId = if (matchEnd.winner == "DRAW") session.playerX else matchEnd.winner
                val loserId = if (matchEnd.winner == "DRAW") session.playerO else (if (winnerId == session.playerX) session.playerO else session.playerX)
                val isDraw = matchEnd.winner == "DRAW"
                
                // Get real names
                val winnerName = getPlayerName(winnerId)
                val loserName = getPlayerName(loserId)
                
                // Calculate duration
                val duration = (System.currentTimeMillis() - session.matchStartTime) / 1000
                
                // Filter moves
                val winnerMoves = session.matchMoves.filter { it.first == winnerId }.map { it.second }
                val loserMoves = session.matchMoves.filter { it.first == loserId }.map { it.second }
                
                records.updateRecords(
                    winner = winnerName,
                    loser = loserName,
                    isDraw = isDraw,
                    isPVE = session.isAIGame,
                    boardSize = session.config.boardSize,
                    difficulty = session.config.difficulty,
                    winnerMoves = winnerMoves,
                    loserMoves = loserMoves,
                    durationSeconds = duration
                )

                activeGames.remove(matchId)
                persistenceManager.save(activeGames) // Save change
            } else {
                // Start next round
                delay(2000) // Wait for 2 seconds so players can see the round result
                session.nextRound()
                val newGameState = session.getGameState()
                broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(newGameState))
                
                // Check if it's AI turn in the new round
                checkAndTriggerAIMove(session)
                
                // Start timer for the new round
                startSessionTimer(session)
            }
        }
    }

    /**
     * Starts the timer for the current player in a session.
     */
    private fun startSessionTimer(session: GameSession) {
        // Don't start timer if it's AI's turn (AI is fast enough and we don't want to timeout the PC)
        if (session.isAIGame) {
            val currentPlayer = session.game.getCurrentPlayer()
            // Map symbol to ID
            val currentPlayerId = session.getPlayerId(currentPlayer)
            
            // If current player is AI ("AI" or matches AI ID), don't start timer
            if (currentPlayerId == "AI") {
                println("[GameServer] Skipping timer for AI turn")
                return
            }
        }
        
        session.startTurnTimer {
            handleTimeout(session.matchId)
        }
    }

    // Matchmaking Queues: Map<BoardSize, Queue<QueueEntry>>
    private val waitingQueues = ConcurrentHashMap<Int, java.util.concurrent.ConcurrentLinkedQueue<QueueEntry>>()
    
    private data class QueueEntry(
        val playerId: String,
        val timeLimit: Int,
        val totalRounds: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Adds a player to the matchmaking queue.
     */
    fun queuePlayer(playerId: String, boardSize: Int, timeLimit: Int, totalRounds: Int) {
        // Remove from any existing queue first
        cancelQueue(playerId)
        
        val queue = waitingQueues.getOrPut(boardSize) { java.util.concurrent.ConcurrentLinkedQueue() }
        queue.add(QueueEntry(playerId, timeLimit, totalRounds))
        
        println("[GameServer] Player $playerId joined queue for ${boardSize}x$boardSize (time: ${timeLimit}s, rounds: $totalRounds). Queue size: ${queue.size}")
        
        checkQueue(boardSize)
    }
    
    /**
     * Removes a player from the matchmaking queue.
     */
    fun cancelQueue(playerId: String) {
        var removed = false
        waitingQueues.values.forEach { queue ->
            if (queue.removeIf { it.playerId == playerId }) {
                removed = true
            }
        }
        if (removed) {
            println("[GameServer] Player $playerId removed from queue.")
        }
    }
    
    /**
     * Checks if a match can be formed for a specific board size.
     */
    private fun checkQueue(boardSize: Int) {
        val queue = waitingQueues[boardSize] ?: return
        
        synchronized(queue) {
            if (queue.size >= 2) {
                val entry1 = queue.poll()
                val entry2 = queue.poll()
                
                if (entry1 != null && entry2 != null) {
                    createPVPGame(entry1, entry2, boardSize)
                }
            }
        }
    }
    
    /**
     * Creates a PVP game between two players.
     */
    private fun createPVPGame(entry1: QueueEntry, entry2: QueueEntry, boardSize: Int) {
        val player1 = entry1.playerId
        val player2 = entry2.playerId
        val matchId = UUID.randomUUID().toString()
        println("[GameServer] Creating PVP match: $matchId between $player1 and $player2")
        
        // Negotiate time limit: use average of both settings
        val finalTimeLimit = (entry1.timeLimit + entry2.timeLimit) / 2
        
        // Negotiate total rounds: use average of both settings
        val finalTotalRounds = (entry1.totalRounds + entry2.totalRounds) / 2
        
        println("[GameServer] Negotiated game config: Time=${finalTimeLimit}s, Rounds=$finalTotalRounds (P1: T=${entry1.timeLimit} R=${entry1.totalRounds}, P2: T=${entry2.timeLimit} R=${entry2.totalRounds})")
        
        val config = GameConfig(
            boardSize = boardSize,
            winLength = boardSize, // Standard rule
            totalRounds = finalTotalRounds,
            difficulty = Difficulty.MEDIUM, // Irrelevant
            timeLimit = finalTimeLimit,
            practiceMode = false
        )
        
        // Randomize starting player
        val p1Starts = kotlin.random.Random.nextBoolean()
        val playerX = if (p1Starts) player1 else player2
        val playerO = if (p1Starts) player2 else player1
        
        val session = GameSession(
            matchId = matchId,
            config = config,
            playerX = playerX,
            playerO = playerO,
            isAIGame = false
        )
        
        activeGames[matchId] = session
        persistenceManager.save(activeGames) // Save new game
        
        // Notify players
        val p1Name = getPlayerName(player1)
        val p2Name = getPlayerName(player2)
        
        // Update handlers with match ID
        clients[player1]?.setMatchId(matchId)
        clients[player2]?.setMatchId(matchId)
        
        // Notify P1 found P2
        clients[player1]?.sendMessage(MessageType.GAME_FOUND, json.encodeToString(GameFound(matchId, p2Name)))
        // Notify P2 found P1
        clients[player2]?.sendMessage(MessageType.GAME_FOUND, json.encodeToString(GameFound(matchId, p1Name)))
        
        // Wait a brief moment then send initial state
        scope.launch {
            delay(1000)
            val gameState = session.getGameState()
            broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(gameState))
            startSessionTimer(session)
        }
    }
}

/**
 * Main entry point for the server.
 */
fun main() {
    // Setup file logging
    org.dam.project.util.LoggerConfig.setupLogging("server.log")
    
    val server = GameServer("server.properties")
    
    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })
    
    server.start()
    
    // Keep main thread alive
    Thread.currentThread().join()
}
