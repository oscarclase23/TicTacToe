package org.dam.project.server

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

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

    fun setMatchId(matchId: String) { this.currentMatchId = matchId }

    suspend fun handle() = withContext(Dispatchers.IO) {
        try {
            println("[ClientHandler] Cliente conectado desde ${socket.inetAddress}")
            println("[ClientHandler] Enviando registros iniciales...")
            val recordsData = server.records.getSyncData()
            sendMessage(MessageType.RECORDS_SYNC, json.encodeToString(recordsData))
            println("[ClientHandler] Registros iniciales enviados.")

            while (isActive && !socket.isClosed) {
                val line = input.readLine() ?: break
                try {
                    val message = json.decodeFromString<NetworkMessage>(line)
                    processMessage(message)
                } catch (e: Exception) {
                    println("[ClientHandler] Error procesando mensaje: ${e.message}")
                    sendError("ERROR_FORMATO", "Formato de mensaje inválido")
                }
            }
        } catch (e: Exception) {
            println("[ClientHandler] Error de conexión: ${e.message}")
        } finally {
            close()
        }
    }

    private suspend fun processMessage(message: NetworkMessage) {
        println("[ClientHandler] Recibido ${message.type} de $playerId")
        println("[ClientHandler] Payload: ${message.payload}")

        when (message.type) {
            MessageType.CONNECT -> handleConnect(message.payload)
            MessageType.CREATE_GAME -> handleCreateGame(message.payload)
            MessageType.MAKE_MOVE -> handleMakeMove(message.payload)
            MessageType.UNDO_REQUEST -> handleUndoRequest(message.payload)
            MessageType.JOIN_QUEUE -> handleJoinQueue(message.payload)
            MessageType.CANCEL_QUEUE -> handleCancelQueue()
            MessageType.LEAVE_GAME -> handleLeaveGame()
            MessageType.SURRENDER -> handleSurrender()
            MessageType.DISCONNECT -> close()
            else -> sendError("MENSAJE_DESCONOCIDO", "Tipo de mensaje desconocido: ${message.type}")
        }
    }

    private suspend fun handleConnect(payload: String) {
        try {
            val request = json.decodeFromString<ConnectRequest>(payload)
            playerId = server.registerClient(this, request.playerName, request.previousPlayerId)
            playerName = request.playerName

            sendMessage(MessageType.CONNECT, json.encodeToString(ConnectResponse(
                success = true,
                message = "Conectado correctamente",
                playerId = playerId
            )))
            println("[ClientHandler] Jugador conectado: $playerId (${request.playerName})")
        } catch (e: Exception) {
            println("[ClientHandler] Error en conexión: ${e.message}")
            sendError("ERROR_CONEXION", "Fallo al conectar: ${e.message}")
        }
    }

    private suspend fun handleCreateGame(payload: String) {
        try {
            val config = json.decodeFromString<GameConfig>(payload)
            val pid = playerId ?: run { sendError("SIN_CONEXION", "Debes conectarte primero"); return }

            val matchId = server.createPVEGame(pid, config)
            currentMatchId = matchId
            println("[ClientHandler] Partida PVE creada: $matchId para jugador $pid")

            val session = server.getSession(matchId)
            if (session != null) {
                // FIX: usar broadcastToGame en lugar de sendMessage directo,
                // igual que en PVP, para consistencia en el flujo de estados
                server.broadcastToGame(matchId, MessageType.GAME_STATE, json.encodeToString(session.getGameState()))

                if (session.playerX == "AI" && session.game.getCurrentPlayer() == "X") {
                    println("[ClientHandler] La IA empieza primero, lanzando movimiento inicial")
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(500)
                        val currentSession = server.getSession(matchId)
                        if (currentSession != null && !currentSession.isFinished) {
                            val aiMove = GameAI.getBestMove(currentSession.game, currentSession.config.difficulty, "X")
                            server.processMove(matchId, "AI", aiMove)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("[ClientHandler] Error al crear partida: ${e.message}")
            sendError("ERROR_CREAR_PARTIDA", "No se pudo crear la partida: ${e.message}")
        }
    }

    private suspend fun handleUndoRequest(payload: String) {
        try {
            val request = json.decodeFromString<UndoRequest>(payload)
            val pid = playerId ?: return
            server.processUndo(request.matchId, pid)
        } catch (e: Exception) {
            println("[ClientHandler] Error al procesar deshacer: ${e.message}")
        }
    }

    private suspend fun handleJoinQueue(payload: String) {
        try {
            val request = json.decodeFromString<JoinQueueRequest>(payload)
            val pid = playerId ?: run { sendError("SIN_CONEXION", "Debes conectarte primero"); return }
            // FIX: use the explicit turboMode field from the request, not a heuristic
            server.queuePlayer(pid, request.preferredBoardSize, request.timeLimit, request.totalRounds, request.turboMode)
        } catch (e: Exception) {
            println("[ClientHandler] Error al unirse a la cola: ${e.message}")
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

    private suspend fun handleSurrender() {
        val pid = playerId ?: run {
            println("[ClientHandler] RENDIRSE recibido pero sin ID de jugador")
            return
        }
        val matchId = currentMatchId ?: run {
            println("[ClientHandler] RENDIRSE recibido pero sin partida activa para $pid")
            return
        }
        println("[ClientHandler] Jugador $pid se rindió en la partida $matchId")
        server.processSurrender(matchId, pid)
    }

    private suspend fun handleMakeMove(payload: String) {
        try {
            val moveRequest = json.decodeFromString<MoveRequest>(payload)
            val matchId = currentMatchId ?: run { sendError("SIN_PARTIDA", "No hay partida activa"); return }
            val pid = playerId ?: run { sendError("SIN_CONEXION", "No conectado"); return }
            server.processMove(matchId, pid, moveRequest.position)
        } catch (e: Exception) {
            println("[ClientHandler] Error al procesar movimiento: ${e.message}")
            sendError("ERROR_MOVIMIENTO", "No se pudo procesar el movimiento: ${e.message}")
        }
    }

    fun sendMessage(type: MessageType, payload: String) {
        try {
            output.println(json.encodeToString(NetworkMessage(type, payload)))
        } catch (e: Exception) {
            println("[ClientHandler] Error enviando mensaje: ${e.message}")
        }
    }

    private fun sendError(code: String, message: String) {
        sendMessage(MessageType.ERROR, json.encodeToString(ErrorMessage(code, message, recoverable = true)))
    }

    fun close() {
        try {
            playerId?.let {
                server.cancelQueue(it)
                server.unregisterClient(it)
            }
            socket.close()
            println("[ClientHandler] Cliente desconectado: $playerId")
        } catch (e: Exception) {
            println("[ClientHandler] Error al cerrar conexión: ${e.message}")
        }
    }
}