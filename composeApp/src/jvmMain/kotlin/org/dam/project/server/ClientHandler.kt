package org.dam.project.server

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

/**
 * Handles communication with a single client.
 */
class ClientHandler(
    private val socket: Socket,
    private val server: GameServer
) {
    private val input = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val output = PrintWriter(socket.getOutputStream(), true)
    private val json = Json

    var playerId: String? = null
    var playerName: String? = null
    var currentMatchId: String? = null

    fun setMatchId(matchId: String) {
        this.currentMatchId = matchId
    }

    suspend fun handle() = withContext(Dispatchers.IO) {
        try {
            println("[ClientHandler] Client connected from ${socket.inetAddress}")

            // Send records immediately (Server speaks first)
            println("[ClientHandler] Sending initial records...")
            val recordsData = server.records.getSyncData()
            sendMessage(MessageType.RECORDS_SYNC, json.encodeToString(recordsData))
            println("[ClientHandler] Initial records sent.")

            // Read messages in loop
            while (isActive && !socket.isClosed) {
                val line = input.readLine() ?: break

                try {
                    val message = json.decodeFromString<NetworkMessage>(line)
                    processMessage(message)
                } catch (e: Exception) {
                    println("[ClientHandler] Error processing message: ${e.message}")
                    sendError("PARSE_ERROR", "Invalid message format")
                }
            }
        } catch (e: Exception) {
            println("[ClientHandler] Connection error: ${e.message}")
        } finally {
            close()
        }
    }

    private suspend fun processMessage(message: NetworkMessage) {
        println("[ClientHandler] Received ${message.type} from $playerId")
        println("[ClientHandler] Message payload: ${message.payload}")

        when (message.type) {
            MessageType.CONNECT -> handleConnect(message.payload)
            MessageType.CREATE_GAME -> handleCreateGame(message.payload)
            MessageType.MAKE_MOVE -> handleMakeMove(message.payload)
            MessageType.UNDO_REQUEST -> handleUndoRequest(message.payload)
            MessageType.JOIN_QUEUE -> handleJoinQueue(message.payload)
            MessageType.CANCEL_QUEUE -> handleCancelQueue()
            MessageType.LEAVE_GAME -> handleLeaveGame()
            MessageType.SURRENDER -> handleSurrender()   // FIX: was falling to else branch
            MessageType.DISCONNECT -> close()
            else -> sendError("UNKNOWN_MESSAGE", "Unknown message type: ${message.type}")
        }
    }

    private suspend fun handleConnect(payload: String) {
        try {
            val request = json.decodeFromString<ConnectRequest>(payload)
            playerId = server.registerClient(this, request.playerName, request.previousPlayerId)
            playerName = request.playerName

            val response = ConnectResponse(
                success = true,
                message = "Connected successfully",
                playerId = playerId
            )
            sendMessage(MessageType.CONNECT, json.encodeToString(response))

            println("[ClientHandler] Player connected: $playerId (${request.playerName})")
        } catch (e: Exception) {
            println("[ClientHandler] Error handling connect: ${e.message}")
            sendError("CONNECT_ERROR", "Failed to connect: ${e.message}")
        }
    }

    private suspend fun handleCreateGame(payload: String) {
        try {
            val config = json.decodeFromString<GameConfig>(payload)
            val pid = playerId ?: run {
                sendError("NOT_CONNECTED", "Must connect first")
                return
            }

            val matchId = server.createPVEGame(pid, config)
            currentMatchId = matchId

            println("[ClientHandler] Created PVE game: $matchId for player $pid")

            val session = server.getSession(matchId)
            if (session != null) {
                val gameState = session.getGameState()
                sendMessage(MessageType.GAME_STATE, json.encodeToString(gameState))

                // If AI goes first (AI is X), trigger AI move immediately
                if (session.playerX == "AI" && session.game.getCurrentPlayer() == "X") {
                    println("[ClientHandler] AI goes first, triggering initial AI move")
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500)
                        val aiSymbol = "X"
                        val aiMove = GameAI.getBestMove(session.game, session.config.difficulty, aiSymbol)
                        server.processMove(matchId, "AI", aiMove)
                    }
                }
            }
        } catch (e: Exception) {
            println("[ClientHandler] Error creating game: ${e.message}")
            sendError("CREATE_GAME_ERROR", "Failed to create game: ${e.message}")
        }
    }

    private suspend fun handleUndoRequest(payload: String) {
        try {
            val request = json.decodeFromString<UndoRequest>(payload)
            val pid = playerId ?: return
            server.processUndo(request.matchId, pid)
        } catch (e: Exception) {
            println("[ClientHandler] Error handling undo: ${e.message}")
        }
    }

    private suspend fun handleJoinQueue(payload: String) {
        try {
            val request = json.decodeFromString<JoinQueueRequest>(payload)
            val pid = playerId ?: run {
                sendError("NOT_CONNECTED", "Not connected")
                return
            }
            server.queuePlayer(pid, request.preferredBoardSize, request.timeLimit, request.totalRounds)
        } catch (e: Exception) {
            println("[ClientHandler] Error joining queue: ${e.message}")
        }
    }

    private suspend fun handleCancelQueue() {
        val pid = playerId ?: return
        server.cancelQueue(pid)
    }

    private suspend fun handleLeaveGame() {
        val pid = playerId ?: return
        val matchId = currentMatchId ?: return
        server.processSurrender(matchId, pid)
    }

    /**
     * Handles SURRENDER message - player explicitly surrenders the current game.
     * FIX: Previously this fell through to the else branch and returned an
     * "Unknown message type: SURRENDER" error, which left the game session
     * in limbo and caused the client to get confused.
     */
    private suspend fun handleSurrender() {
        val pid = playerId ?: run {
            println("[ClientHandler] SURRENDER received but no player ID")
            return
        }
        val matchId = currentMatchId ?: run {
            println("[ClientHandler] SURRENDER received but no active match for player $pid")
            return
        }
        println("[ClientHandler] Player $pid surrendered match $matchId")
        server.processSurrender(matchId, pid)
    }

    private suspend fun handleMakeMove(payload: String) {
        try {
            val moveRequest = json.decodeFromString<MoveRequest>(payload)
            val matchId = currentMatchId ?: run {
                sendError("NO_ACTIVE_GAME", "No active game")
                return
            }

            val pid = playerId ?: run {
                sendError("NOT_CONNECTED", "Not connected")
                return
            }

            server.processMove(matchId, pid, moveRequest.position)

        } catch (e: Exception) {
            println("[ClientHandler] Error handling move: ${e.message}")
            sendError("MOVE_ERROR", "Failed to process move: ${e.message}")
        }
    }

    fun sendMessage(type: MessageType, payload: String) {
        try {
            val message = NetworkMessage(type, payload)
            val jsonString = json.encodeToString(message)
            output.println(jsonString)
        } catch (e: Exception) {
            println("[ClientHandler] Error sending message: ${e.message}")
        }
    }

    private fun sendError(code: String, message: String) {
        val errorMessage = ErrorMessage(code, message, recoverable = true)
        sendMessage(MessageType.ERROR, json.encodeToString(errorMessage))
    }

    fun close() {
        try {
            playerId?.let {
                server.cancelQueue(it)
                server.unregisterClient(it)
            }
            socket.close()
            println("[ClientHandler] Client disconnected: $playerId")
        } catch (e: Exception) {
            println("[ClientHandler] Error closing connection: ${e.message}")
        }
    }
}