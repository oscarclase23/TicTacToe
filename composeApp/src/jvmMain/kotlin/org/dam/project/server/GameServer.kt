package org.dam.project.server

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.game.TicTacToeGame
import org.dam.project.network.*
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.dam.project.util.LoggerConfig

class GameServer(val config: ServerConfig) {

    constructor(configFile: String) : this(loadConfig(configFile))
    constructor(port: Int) : this(ServerConfig("localhost", port, 10))

    val records = RecordsManager("records.json")

    private val clients = ConcurrentHashMap<String, ClientHandler>()
    private val activeGames = ConcurrentHashMap<String, GameSession>()
    private val json = Json { ignoreUnknownKeys = true }
    private val persistenceManager = GamesPersistenceManager()

    private fun getPlayerName(playerId: String): String =
        if (playerId == "AI") "AI" else clients[playerId]?.playerName ?: "Unknown"

    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        // Restore persisted games
        val loaded = persistenceManager.load()
        var restored = 0
        loaded.forEach { data ->
            try {
                val session = GameSession(data.matchId, data.config, data.playerX, data.playerO, data.isAIGame)
                session.scores.putAll(data.scores)
                session.game.setBoard(data.boardState)
                session.game.setCurrentPlayer(data.currentPlayer)
                activeGames[data.matchId] = session
                restored++
                startSessionTimer(session)
            } catch (e: Exception) {
                println("[GameServer] Failed to restore session ${data.matchId}: ${e.message}")
            }
        }
        if (restored > 0) println("[GameServer] Restored $restored active games")

        try {
            serverSocket = try {
                ServerSocket(config.port, 50, java.net.InetAddress.getByName(config.host))
            } catch (_: Exception) {
                ServerSocket(config.port)
            }
            println("[GameServer] Server started on ${config.host}:${config.port}")
            println("[GameServer] Max clients: ${config.maxClients}")

            scope.launch {
                println("[GameServer] Accept loop started")
                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        println("[GameServer] New connection request from ${socket.inetAddress}")
                        if (clients.size >= config.maxClients) {
                            println("[GameServer] Max clients reached, rejecting")
                            socket.close()
                            continue
                        }
                        scope.launch {
                            try { ClientHandler(socket, this@GameServer).handle() }
                            catch (e: Exception) { println("[GameServer] Error in handler: ${e.message}") }
                        }
                    } catch (e: Exception) {
                        if (isActive) println("[GameServer] Accept error: ${e.message}")
                    }
                }
            }

            println("[GameServer] Server is running. Press Ctrl+C to stop.")
        } catch (e: Exception) {
            println("[GameServer] Failed to start: ${e.message}")
            e.printStackTrace()
        }
    }

    fun stop() {
        println("[GameServer] Stopping...")
        scope.cancel()
        serverSocket?.close()
        clients.values.forEach { it.close() }
        println("[GameServer] Stopped")
    }

    fun registerClient(handler: ClientHandler, playerName: String, previousPlayerId: String? = null): String {
        if (previousPlayerId != null) {
            val existingGame = activeGames.values.find {
                it.playerX == previousPlayerId || it.playerO == previousPlayerId
            }
            if (existingGame != null) {
                println("[GameServer] Resuming session for $previousPlayerId (${existingGame.matchId})")
                clients[previousPlayerId] = handler
                handler.setMatchId(existingGame.matchId)
                handler.playerId = previousPlayerId
                handler.playerName = playerName
                existingGame.setPlayerConnected(previousPlayerId, true)
                scope.launch {
                    delay(300)
                    handler.sendMessage(MessageType.GAME_STATE, json.encodeToString(existingGame.getGameState()))
                    val opponentId = if (existingGame.playerX == previousPlayerId) existingGame.playerO else existingGame.playerX
                    if (opponentId != "AI") clients[opponentId]?.sendMessage(MessageType.OPPONENT_RECONNECTED, "")
                }
                return previousPlayerId
            }
        }
        val playerId = UUID.randomUUID().toString()
        clients[playerId] = handler
        println("[GameServer] Registered client: $playerId ($playerName)")
        return playerId
    }

    fun unregisterClient(playerId: String) {
        clients.remove(playerId)
        handlePlayerDisconnect(playerId)
        println("[GameServer] Unregistered client: $playerId")
    }

    fun handlePlayerDisconnect(playerId: String) {
        val session = activeGames.values.find { it.playerX == playerId || it.playerO == playerId } ?: return
        println("[GameServer] Player $playerId disconnected from game ${session.matchId}")
        session.setPlayerConnected(playerId, false)
        if (session.isAIGame) {
            session.stopTimer()
        } else {
            val opponentId = if (session.playerX == playerId) session.playerO else session.playerX
            clients[opponentId]?.sendMessage(MessageType.OPPONENT_DISCONNECTED, "")
            session.stopTimer()
        }
        persistenceManager.save(activeGames)
    }

    fun processSurrender(matchId: String, playerId: String) {
        val session = activeGames[matchId] ?: return
        println("[GameServer] Player $playerId SURRENDERED game $matchId")
        val opponentId = if (session.playerX == playerId) session.playerO else session.playerX
        val opponentName = getPlayerName(opponentId)
        val matchEnd = MatchEnd(
            winner = opponentName,
            score = mapOf(getPlayerName(playerId) to 0, opponentName to 1),
            reason = "Oponente se rindió"
        )
        broadcastToGame(matchId, MessageType.MATCH_END, json.encodeToString(matchEnd))
        records.updateRecords(
            winner = opponentName, loser = getPlayerName(playerId),
            isDraw = false, isPVE = session.isAIGame,
            boardSize = session.config.boardSize, difficulty = session.config.difficulty
        )
        // Sync records to ALL connected clients
        broadcastAll(MessageType.RECORDS_SYNC, json.encodeToString(records.getSyncData()))
        
        session.stopTimer()
        activeGames.remove(matchId)
        persistenceManager.save(activeGames)
    }

    fun createPVEGame(playerId: String, config: GameConfig): String {
        val matchId = UUID.randomUUID().toString()
        val aiGoesFirst = kotlin.random.Random.nextBoolean()
        val session = if (aiGoesFirst) {
            println("[GameServer] AI will go first (AI=X, Player=O)")
            GameSession(matchId, config, "AI", playerId, true)
        } else {
            println("[GameServer] Player will go first (Player=X, AI=O)")
            GameSession(matchId, config, playerId, "AI", true)
        }
        activeGames[matchId] = session
        persistenceManager.save(activeGames)
        println("[GameServer] Created PVE game: $matchId (difficulty: ${config.difficulty}, practice=${config.practiceMode})")
        startSessionTimer(session)
        return matchId
    }

    suspend fun processUndo(matchId: String, playerId: String) {
        val session = activeGames[matchId] ?: return
        val result = session.handleUndo(playerId)
        if (result.success) {
            println("[GameServer] Undo successful for $playerId")
            broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(session.getGameState()))
            persistenceManager.save(activeGames)
            startSessionTimer(session)
        } else {
            println("[GameServer] Undo failed: ${result.message}")
            clients[playerId]?.sendMessage(
                MessageType.ERROR,
                json.encodeToString(ErrorMessage("UNDO_FAILED", result.message))
            )
        }
    }

    suspend fun processMove(matchId: String, playerId: String, position: Position) {
        println("[GameServer] Processing move: matchId=$matchId, playerId=$playerId, position=(${position.row},${position.col})")
        val session = activeGames[matchId] ?: run {
            println("[GameServer] Game not found: $matchId")
            return
        }

        val moveResult = session.makeMove(playerId, position)
        println("[GameServer] Move result: valid=${moveResult.valid}, player=${moveResult.player}")

        broadcastToGame(matchId, MessageType.MOVE_RESULT, json.encodeToString(moveResult))

        if (!moveResult.valid) {
            println("[GameServer] Move was invalid: ${moveResult.errorMessage}")
            return
        }

        session.stopTimer()

        val gameState = session.getGameState()
        println("[GameServer] Broadcasting game state after player move")
        broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(gameState))
        persistenceManager.save(activeGames)

        val roundEnd = session.checkRoundEnd()
        if (roundEnd != null) {
            println("[GameServer] Round ended: winner=${roundEnd.winner}")
            broadcastToGame(matchId, MessageType.ROUND_END, json.encodeToString(roundEnd))

            val matchEnd = session.checkMatchEnd()
            if (matchEnd != null) {
                handleMatchEnd(session, matchEnd)
            } else {
                delay(2000)
                session.nextRound()
                broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(session.getGameState()))
                checkAndTriggerAIMove(session)
                startSessionTimer(session)
            }
        } else {
            checkAndTriggerAIMove(session)
            startSessionTimer(session)
        }
    }

    private suspend fun handleMatchEnd(session: GameSession, matchEnd: MatchEnd) {
        println("[GameServer] Match ended: winner=${matchEnd.winner}")
        broadcastToGame(session.matchId, MessageType.MATCH_END, json.encodeToString(matchEnd))

        val isDraw = matchEnd.winner == "DRAW"
        val winnerId = if (isDraw) session.playerX else matchEnd.winner
        val loserId = if (isDraw) session.playerO else
            if (winnerId == session.playerX) session.playerO else session.playerX

        val winnerName = getPlayerName(winnerId)
        val loserName = getPlayerName(loserId)
        val duration = (System.currentTimeMillis() - session.matchStartTime) / 1000
        val winnerMoves = session.matchMoves.filter { it.first == winnerId }.map { it.second }
        val loserMoves = session.matchMoves.filter { it.first == loserId }.map { it.second }

        records.updateRecords(
            winner = winnerName, loser = loserName, isDraw = isDraw,
            isPVE = session.isAIGame, boardSize = session.config.boardSize,
            difficulty = session.config.difficulty,
            winnerMoves = winnerMoves, loserMoves = loserMoves,
            durationSeconds = duration
        )

        // Sync records to ALL connected clients
        broadcastAll(MessageType.RECORDS_SYNC, json.encodeToString(records.getSyncData()))

        activeGames.remove(session.matchId)
        persistenceManager.save(activeGames)
    }

    private suspend fun checkAndTriggerAIMove(session: GameSession) {
        if (!session.isAIGame || session.game.isGameOver()) return
        val aiSymbol = if (session.playerX == "AI") "X" else "O"
        if (session.game.getCurrentPlayer() != aiSymbol) {
            println("[GameServer] Waiting for next player move. Current player: ${session.game.getCurrentPlayer()}")
            return
        }
        println("[GameServer] AI's turn, calculating move...")
        try {
            delay(400)
            val aiMove = GameAI.getBestMove(session.game, session.config.difficulty, aiSymbol)
            processMove(session.matchId, "AI", aiMove)
        } catch (e: Exception) {
            println("[GameServer] ERROR: AI move failed: ${e.message}")
            val fallback = getRandomMove(session.game)
            if (fallback != null) processMove(session.matchId, "AI", fallback)
        }
    }

    private fun getRandomMove(game: TicTacToeGame): Position? {
        val empty = mutableListOf<Position>()
        for (r in 0 until game.boardSize)
            for (c in 0 until game.boardSize)
                if (game.getCellValue(r, c).isEmpty()) empty.add(Position(r, c))
        return empty.randomOrNull()
    }

    private fun handleTimeout(matchId: String) {
        scope.launch {
            val session = activeGames[matchId] ?: return@launch
            println("[GameServer] Timeout for match $matchId")

            val currentPlayer = session.game.getCurrentPlayer()
            val winnerSymbol = if (currentPlayer == "X") "O" else "X"
            val winnerId = session.getPlayerId(winnerSymbol)

            session.scores[winnerId] = (session.scores[winnerId] ?: 0) + 1
            println("[GameServer] Timeout! Winner is $winnerSymbol ($winnerId)")

            val roundEnd = RoundEnd(winner = winnerSymbol, isDraw = false, reason = "Tiempo agotado")
            broadcastToGame(matchId, MessageType.ROUND_END, json.encodeToString(roundEnd))

            val matchEnd = session.checkMatchEnd()
            if (matchEnd != null) {
                handleMatchEnd(session, matchEnd)
            } else {
                delay(2000)
                session.nextRound()
                broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(session.getGameState()))
                checkAndTriggerAIMove(session)
                startSessionTimer(session)
            }
        }
    }

    private fun startSessionTimer(session: GameSession) {
        if (session.isAIGame) {
            val currentPlayerId = session.getPlayerId(session.game.getCurrentPlayer())
            if (currentPlayerId == "AI") {
                println("[GameServer] Skipping timer for AI turn")
                return
            }
        }
        session.startTurnTimer { handleTimeout(session.matchId) }
    }

    fun broadcastToGame(matchId: String, type: MessageType, payload: String) {
        val session = activeGames[matchId] ?: return
        listOf(session.playerX, session.playerO).forEach { pid ->
            if (pid != "AI") clients[pid]?.sendMessage(type, payload)
        }
    }

    fun broadcastAll(type: MessageType, payload: String) {
        clients.values.forEach { it.sendMessage(type, payload) }
    }

    fun getSession(matchId: String): GameSession? = activeGames[matchId]

    // ── Matchmaking ──────────────────────────────────────────────────────────

    private val waitingQueues = ConcurrentHashMap<Int, java.util.concurrent.ConcurrentLinkedQueue<QueueEntry>>()

    private data class QueueEntry(
        val playerId: String, val timeLimit: Int, val totalRounds: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun queuePlayer(playerId: String, boardSize: Int, timeLimit: Int, totalRounds: Int) {
        cancelQueue(playerId)
        val queue = waitingQueues.getOrPut(boardSize) { java.util.concurrent.ConcurrentLinkedQueue() }
        queue.add(QueueEntry(playerId, timeLimit, totalRounds))
        println("[GameServer] Player $playerId queued for ${boardSize}x$boardSize. Queue size: ${queue.size}")
        checkQueue(boardSize)
    }

    fun cancelQueue(playerId: String) {
        waitingQueues.values.forEach { it.removeIf { e -> e.playerId == playerId } }
    }

    private fun checkQueue(boardSize: Int) {
        val queue = waitingQueues[boardSize] ?: return
        synchronized(queue) {
            if (queue.size >= 2) {
                val e1 = queue.poll()
                val e2 = queue.poll()
                if (e1 != null && e2 != null) createPVPGame(e1, e2, boardSize)
            }
        }
    }

    private fun createPVPGame(e1: QueueEntry, e2: QueueEntry, boardSize: Int) {
        val matchId = UUID.randomUUID().toString()
        println("[GameServer] Creating PVP match: $matchId between ${e1.playerId} and ${e2.playerId}")

        val finalTimeLimit = (e1.timeLimit + e2.timeLimit) / 2
        val finalRounds = (e1.totalRounds + e2.totalRounds) / 2

        val config = GameConfig(
            boardSize = boardSize, winLength = boardSize,
            totalRounds = finalRounds, difficulty = Difficulty.MEDIUM,
            timeLimit = finalTimeLimit
        )

        val p1Starts = kotlin.random.Random.nextBoolean()
        val playerX = if (p1Starts) e1.playerId else e2.playerId
        val playerO = if (p1Starts) e2.playerId else e1.playerId

        val session = GameSession(matchId, config, playerX, playerO, false)
        activeGames[matchId] = session
        persistenceManager.save(activeGames)

        clients[e1.playerId]?.setMatchId(matchId)
        clients[e2.playerId]?.setMatchId(matchId)

        val p1Name = getPlayerName(e1.playerId)
        val p2Name = getPlayerName(e2.playerId)
        clients[e1.playerId]?.sendMessage(MessageType.GAME_FOUND, json.encodeToString(GameFound(matchId, p2Name)))
        clients[e2.playerId]?.sendMessage(MessageType.GAME_FOUND, json.encodeToString(GameFound(matchId, p1Name)))

        scope.launch {
            delay(1000)
            broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(session.getGameState()))
            startSessionTimer(session)
        }
    }
}

fun main() {
    org.dam.project.util.LoggerConfig.setupLogging("server.log")
    val server = GameServer("server.properties")
    Runtime.getRuntime().addShutdownHook(Thread { server.stop() })
    server.start()
    Thread.currentThread().join()
}