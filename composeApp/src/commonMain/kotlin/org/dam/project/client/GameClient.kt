package org.dam.project.client

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.*
import kotlin.math.pow

class GameClient(
    private val network: NetworkClient,
    private val settings: ClientSettings? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Content(Screen.Login))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _currentGameState = MutableStateFlow<GameState?>(null)
    val currentGameState: StateFlow<GameState?> = _currentGameState.asStateFlow()

    private val _roundEndResult = MutableStateFlow<RoundEnd?>(null)
    val roundEndResult: StateFlow<RoundEnd?> = _roundEndResult.asStateFlow()

    private val _matchEndResult = MutableStateFlow<Pair<MatchEnd, GameState>?>(null)
    val matchEndResult: StateFlow<Pair<MatchEnd, GameState>?> = _matchEndResult.asStateFlow()

    private val _timeRemaining = MutableStateFlow<Int?>(null)
    val timeRemaining: StateFlow<Int?> = _timeRemaining.asStateFlow()

    private val _isOpponentDisconnected = MutableStateFlow(false)
    val isOpponentDisconnected: StateFlow<Boolean> = _isOpponentDisconnected.asStateFlow()

    private val _isConnectionLost = MutableStateFlow(false)
    val isConnectionLost: StateFlow<Boolean> = _isConnectionLost.asStateFlow()

    private var playerId: String? = null
    var opponentName: String? = null
    private var localPlayerName: String? = null
    private var currentMatchId: String? = null
    private var recordsData: RecordsData? = null
    private var currentTimeLimit: Int = 30
    private var currentPracticeMode: Boolean = false

    private var messageListenerJob: Job? = null
    private var timerJob: Job? = null
    private var reconnectJob: Job? = null

    // Flag: are we currently showing a match-end overlay? Block GAME_STATE nav during this.
    @Volatile private var isMatchEndPending = false

    // For automatic reconnection
    private var lastHost: String = "localhost"
    private var lastPort: Int = 5678
    private var lastPlayerName: String = "Player"

    suspend fun connect(host: String, port: Int, playerName: String, allowResume: Boolean = false) {
        stopMessageListener()
        lastHost = host
        lastPort = port
        lastPlayerName = playerName

        _uiState.value = AppUiState.Loading("Inicializando...")

        val previousId: String? = if (allowResume)
            playerId ?: settings?.getString("player_id")
        else null

        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            try {
                try { network.close() } catch (_: Exception) {}

                _uiState.value = AppUiState.Loading("Conectando a $host:$port...")
                network.connect(host, port)

                _uiState.value = AppUiState.Loading("Esperando datos del servidor...")
                val initialMsg = withTimeout(5000) { network.receive() }

                if (initialMsg.type == MessageType.RECORDS_SYNC) {
                    recordsData = jsonParser.decodeFromString<RecordsData>(initialMsg.payload)
                    println("[GameClient] Processed ${recordsData?.records?.size ?: 0} records")
                }

                _uiState.value = AppUiState.Loading("Autenticando...")
                network.send(NetworkMessage(
                    type = MessageType.CONNECT,
                    payload = jsonParser.encodeToString(ConnectRequest(playerName, previousPlayerId = previousId))
                ))

                val response = withTimeout(5000) { network.receive() }
                if (response.type == MessageType.CONNECT) {
                    val cr = jsonParser.decodeFromString<ConnectResponse>(response.payload)
                    if (cr.success) {
                        playerId = cr.playerId
                        cr.playerId?.let { settings?.saveString("player_id", it) }
                        localPlayerName = playerName
                        println("[GameClient] Connected with ID: $playerId")
                        startMessageListener()
                        _isConnectionLost.value = false
                        _uiState.value = AppUiState.Content(Screen.Menu)
                        return
                    } else {
                        throw Exception(cr.message)
                    }
                } else {
                    throw Exception("Respuesta inesperada: ${response.type}")
                }

            } catch (e: Exception) {
                attempts++
                println("[GameClient] Intento $attempts fallido: ${e.message}")
                try { network.close() } catch (_: Exception) {}

                if (attempts < maxAttempts) {
                    val delayMs = (1000 * 2.0.pow(attempts - 1)).toLong()
                    _uiState.value = AppUiState.Loading("Reintentando... ($attempts/$maxAttempts)")
                    delay(delayMs)
                } else {
                    _uiState.value = AppUiState.Error(
                        message = "No se pudo conectar después de $maxAttempts intentos: ${e.message}",
                        canRetry = true
                    )
                }
            }
        }
    }

    private fun startMessageListener() {
        stopMessageListener()
        println("[GameClient] Starting message listener...")
        messageListenerJob = scope.launch(Dispatchers.Default) {
            try {
                while (isActive && network.isConnected()) {
                    try {
                        val message = network.receive()
                        handleMessage(message)
                    } catch (e: Exception) {
                        if (isActive) {
                            println("ERROR: Message listener error: ${e.message}")
                            val isDisconnect = !network.isConnected() ||
                                e.message?.contains("Connection reset", ignoreCase = true) == true ||
                                e.message?.contains("Connection closed", ignoreCase = true) == true ||
                                e.message?.contains("closed", ignoreCase = true) == true ||
                                e.message?.contains("EOF", ignoreCase = true) == true
                            if (isDisconnect) {
                                println("[GameClient] Connection lost, attempting auto-reconnect...")
                                scope.launch(Dispatchers.Main) { _isConnectionLost.value = true }
                                startAutoReconnect()
                                break
                            }
                            delay(500)
                        }
                    }
                }
            } finally {
                println("[GameClient] Message listener stopped")
            }
        }
    }

    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 0
            val maxAttempts = 8
            while (attempt < maxAttempts && _isConnectionLost.value) {
                attempt++
                val delayMs = (1000 * 2.0.pow(attempt - 1).toLong()).coerceAtMost(30_000L)
                println("[GameClient] Reconexión automática intento $attempt en ${delayMs}ms...")
                delay(delayMs)

                try {
                    try { network.close() } catch (_: Exception) {}
                    network.connect(lastHost, lastPort)

                    val initialMsg = withTimeout(5000) { network.receive() }
                    if (initialMsg.type == MessageType.RECORDS_SYNC) {
                        recordsData = jsonParser.decodeFromString<RecordsData>(initialMsg.payload)
                    }

                    val savedId = playerId ?: settings?.getString("player_id")
                    network.send(NetworkMessage(
                        type = MessageType.CONNECT,
                        payload = jsonParser.encodeToString(ConnectRequest(
                            playerName = lastPlayerName,
                            previousPlayerId = savedId
                        ))
                    ))

                    val response = withTimeout(5000) { network.receive() }
                    if (response.type == MessageType.CONNECT) {
                        val cr = jsonParser.decodeFromString<ConnectResponse>(response.payload)
                        if (cr.success) {
                            playerId = cr.playerId
                            cr.playerId?.let { settings?.saveString("player_id", it) }
                            _isConnectionLost.value = false
                            println("[GameClient] Reconectado con ID: $playerId")
                            startMessageListener()
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    println("[GameClient] Reconexión intento $attempt fallida: ${e.message}")
                }
            }

            if (_isConnectionLost.value) {
                _uiState.value = AppUiState.Error(
                    message = "No se pudo reconectar al servidor.",
                    canRetry = true
                )
                _isConnectionLost.value = false
            }
        }
    }

    private fun stopMessageListener() {
        messageListenerJob?.cancel()
        messageListenerJob = null
    }

    private fun handleMessage(message: NetworkMessage) {
        println("[GameClient] Received message: ${message.type}")
        try {
            when (message.type) {
                MessageType.GAME_STATE -> {
                    val gameState = jsonParser.decodeFromString<GameState>(message.payload)
                    println("[GameClient] Decoded GameState: matchId=${gameState.matchId}, currentPlayer=${gameState.currentPlayer}")

                    // FIX: Don't clear round-end overlay when a new GAME_STATE arrives.
                    // The overlay has its own auto-dismiss timer. Only clear it if the round
                    // in the new state is strictly greater (i.e. a new round actually started).
                    val previousState = _currentGameState.value
                    val roundChanged = previousState != null && gameState.currentRound > previousState.currentRound

                    if (roundChanged && _roundEndResult.value != null) {
                        // New round started server-side, safe to clear the round overlay now
                        _roundEndResult.value = null
                    }

                    _currentGameState.value = gameState
                    currentMatchId = gameState.matchId
                    currentTimeLimit = gameState.timeLimit
                    currentPracticeMode = gameState.practiceMode

                    val turnChanged = previousState?.currentPlayer != gameState.currentPlayer
                    if (previousState == null || turnChanged || roundChanged) {
                        startTimer()
                    }

                    scope.launch(Dispatchers.Main) {
                        // Don't navigate away from the game screen if match-end overlay is showing
                        if (isMatchEndPending) return@launch

                        val currentUiState = _uiState.value
                        if (currentUiState !is AppUiState.Content || currentUiState.currentScreen !is Screen.Game) {
                            println("[GameClient] Navigating to Game: ${gameState.matchId}")
                            _uiState.value = AppUiState.Content(Screen.Game(gameState.matchId))
                        }
                    }
                }

                MessageType.MOVE_RESULT -> {
                    val moveResult = jsonParser.decodeFromString<MoveResult>(message.payload)
                    if (!moveResult.valid) println("[GameClient] Invalid move: ${moveResult.errorMessage}")
                }

                MessageType.ROUND_END -> {
                    val roundEnd = jsonParser.decodeFromString<RoundEnd>(message.payload)
                    println("[GameClient] Round ended. Winner: ${roundEnd.winner}")
                    stopTimer()
                    _roundEndResult.value = roundEnd
                    // The overlay auto-dismisses via GameScreen observing roundEndResult.
                    // We do NOT set a timer here to null it — GAME_STATE arrival handles it.
                }

                MessageType.MATCH_END -> {
                    val matchEnd = jsonParser.decodeFromString<MatchEnd>(message.payload)
                    println("[GameClient] Match ended. Winner: ${matchEnd.winner}")
                    stopTimer()
                    isMatchEndPending = true

                    val currentGState = _currentGameState.value
                    if (currentGState != null) {
                        val myId = playerId
                        val amIX = currentGState.playerXId == myId
                        val mySymbol = if (amIX) "X" else "O"
                        val opponentSymbol = if (amIX) "O" else "X"
                        val winnerSymbol = when {
                            matchEnd.winner == "DRAW" -> null
                            matchEnd.winner == localPlayerName -> mySymbol
                            else -> opponentSymbol
                        }
                        // Show the match-end overlay by setting roundEndResult with reason
                        _roundEndResult.value = RoundEnd(
                            winner = winnerSymbol,
                            isDraw = matchEnd.winner == "DRAW",
                            reason = "🏆 Fin del Match — ${matchEnd.reason ?: ""}"
                        )
                    }

                    scope.launch(Dispatchers.Main) {
                        delay(4500)
                        isMatchEndPending = false
                        _uiState.value = AppUiState.Content(Screen.Menu)
                        _currentGameState.value = null
                        currentMatchId = null
                        _roundEndResult.value = null
                        _matchEndResult.value = null
                    }
                }

                MessageType.RECORDS_SYNC -> {
                    recordsData = jsonParser.decodeFromString<RecordsData>(message.payload)
                    println("[GameClient] Records updated: ${recordsData?.records?.size}")
                }

                MessageType.ERROR -> {
                    val error = jsonParser.decodeFromString<ErrorMessage>(message.payload)
                    println("[GameClient] Server error: ${error.message}")
                }

                MessageType.GAME_FOUND -> {
                    val gameFound = jsonParser.decodeFromString<GameFound>(message.payload)
                    opponentName = gameFound.opponentName
                    scope.launch(Dispatchers.Main) {
                        _uiState.value = AppUiState.Loading("Oponente encontrado: ${gameFound.opponentName}. Preparando...")
                    }
                }

                MessageType.OPPONENT_DISCONNECTED -> {
                    println("[GameClient] Opponent disconnected!")
                    _isOpponentDisconnected.value = true
                    stopTimer()
                }

                MessageType.OPPONENT_RECONNECTED -> {
                    println("[GameClient] Opponent reconnected!")
                    _isOpponentDisconnected.value = false
                }

                else -> println("[GameClient] Ignoring: ${message.type}")
            }
        } catch (e: Exception) {
            println("ERROR handling ${message.type}: ${e.message}")
        }
    }

    suspend fun surrenderGame() {
        currentMatchId ?: return
        if (!network.isConnected()) return
        network.send(NetworkMessage(type = MessageType.SURRENDER, payload = ""))
    }

    suspend fun createPVEGame(
        boardSize: Int = 3,
        winLength: Int = 3,
        totalRounds: Int = 3,
        difficulty: Difficulty = Difficulty.EASY,
        timeLimit: Int = 30,
        turboMode: Boolean = false,
        practiceMode: Boolean = false
    ) {
        if (!network.isConnected()) {
            _uiState.value = AppUiState.Error("No conectado al servidor. Reinicia la app.", false)
            return
        }

        _currentGameState.value = null
        _roundEndResult.value = null
        _matchEndResult.value = null
        currentMatchId = null
        currentTimeLimit = timeLimit
        currentPracticeMode = practiceMode
        isMatchEndPending = false

        try {
            network.send(NetworkMessage(
                type = MessageType.CREATE_GAME,
                payload = jsonParser.encodeToString(GameConfig(
                    boardSize = boardSize, winLength = winLength, totalRounds = totalRounds,
                    difficulty = difficulty, timeLimit = timeLimit, turboMode = turboMode,
                    practiceMode = practiceMode
                ))
            ))
            _uiState.value = AppUiState.Loading("Creando partida...")
        } catch (e: Exception) {
            _uiState.value = AppUiState.Error("Error creando partida: ${e.message}", true)
        }
    }

    suspend fun makeMove(row: Int, col: Int) {
        println("[GameClient] Sending move: row $row, col $col")
        stopTimer()
        network.send(NetworkMessage(
            type = MessageType.MAKE_MOVE,
            payload = jsonParser.encodeToString(MoveRequest(Position(row, col)))
        ))
    }

    suspend fun requestUndo() {
        val matchId = currentMatchId ?: return
        network.send(NetworkMessage(
            type = MessageType.UNDO_REQUEST,
            payload = jsonParser.encodeToString(UndoRequest(matchId))
        ))
    }

    suspend fun joinQueue(
        preferredBoardSize: Int = 3,
        timeLimit: Int = 30,
        totalRounds: Int = 3,
        turboMode: Boolean = false
    ) {
        if (!network.isConnected()) {
            _uiState.value = AppUiState.Error("No conectado al servidor.", false)
            return
        }
        val player = playerId ?: return

        // Reset state before joining queue
        _currentGameState.value = null
        _roundEndResult.value = null
        _matchEndResult.value = null
        isMatchEndPending = false

        network.send(NetworkMessage(
            type = MessageType.JOIN_QUEUE,
            payload = jsonParser.encodeToString(
                JoinQueueRequest(
                    playerName = player,
                    preferredBoardSize = preferredBoardSize,
                    timeLimit = if (turboMode) 10 else timeLimit,
                    totalRounds = totalRounds,
                    turboMode = turboMode
                )
            )
        ))
        _uiState.value = AppUiState.Content(Screen.WaitingForMatch)
    }

    suspend fun cancelQueue() {
        network.send(NetworkMessage(type = MessageType.CANCEL_QUEUE, payload = ""))
        _uiState.value = AppUiState.Content(Screen.Menu)
    }

    private fun startTimer() {
        stopTimer()
        val timeLimit = currentTimeLimit
        _timeRemaining.value = timeLimit
        timerJob = scope.launch(Dispatchers.Main) {
            while (_timeRemaining.value != null && _timeRemaining.value!! > 0) {
                delay(1000)
                val current = _timeRemaining.value ?: break
                if (current > 0) _timeRemaining.value = current - 1 else break
            }
            if (_timeRemaining.value == 0) {
                println("[GameClient] Time's up!")
                _timeRemaining.value = null
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timeRemaining.value = null
    }

    fun navigateTo(screen: Screen) { _uiState.value = AppUiState.Content(screen) }
    fun getPlayerName(): String? = localPlayerName
    fun getPlayerId(): String? = playerId
    fun getRecords(): List<PlayerRecord> = recordsData?.records ?: emptyList()
    fun isPracticeMode(): Boolean = currentPracticeMode

    fun cleanup() {
        disconnect()
        scope.cancel()
    }

    fun disconnect() {
        stopMessageListener()
        reconnectJob?.cancel()
        stopTimer()
        network.close()
        currentMatchId = null
        _currentGameState.value = null
        _isConnectionLost.value = false
        _isOpponentDisconnected.value = false
        isMatchEndPending = false
        _uiState.value = AppUiState.Content(Screen.Menu)
    }
}