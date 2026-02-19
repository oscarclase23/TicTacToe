package org.dam.project.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dam.project.network.*
import kotlin.math.pow

class GameClient(
    private val network: NetworkClient,
    private val settings: ClientSettings? = null
) {
    // Internal scope with SupervisorJob - never cancelled by recomposition
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Content(Screen.Login))
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _currentGameState = MutableStateFlow<GameState?>(null)
    val currentGameState: StateFlow<GameState?> = _currentGameState.asStateFlow()

    private val _roundEndResult = MutableStateFlow<RoundEnd?>(null)
    val roundEndResult: StateFlow<RoundEnd?> = _roundEndResult.asStateFlow()

    private val _timeRemaining = MutableStateFlow<Int?>(null)
    val timeRemaining: StateFlow<Int?> = _timeRemaining.asStateFlow()

    private val _isOpponentDisconnected = MutableStateFlow(false)
    val isOpponentDisconnected: StateFlow<Boolean> = _isOpponentDisconnected.asStateFlow()

    private val _isConnectionLost = MutableStateFlow(false)
    val isConnectionLost: StateFlow<Boolean> = _isConnectionLost.asStateFlow()

    private var playerId: String? = null
    var opponentName: String? = null
    private var localPlayerName: String? = null
    private var pveMode: Boolean = false
    private var currentMatchId: String? = null
    private var recordsData: RecordsData? = null
    private var currentTimeLimit: Int = 30
    private var currentTurboMode: Boolean = false
    private var currentPracticeMode: Boolean = false

    private var messageListenerJob: Job? = null
    private var timerJob: Job? = null

    /**
     * Connects to the server.
     * This MUST be called from a stable coroutine scope (not rememberCoroutineScope).
     * App.kt's appScope handles this correctly.
     */
    suspend fun connect(host: String, port: Int, playerName: String, allowResume: Boolean = false) {
        stopMessageListener()

        _uiState.value = AppUiState.Loading("Initializing...")

        val previousId: String? = if (allowResume) {
            playerId ?: settings?.getString("player_id")?.also {
                println("[GameClient] Loaded saved player ID: $it")
            }
        } else {
            null
        }

        var attempts = 0
        val maxAttempts = 3

        while (attempts < maxAttempts) {
            try {
                // Always close before (re)connecting
                try { network.close() } catch (_: Exception) {}

                _uiState.value = AppUiState.Loading("Connecting to $host:$port...")
                network.connect(host, port)
                println("DEBUG: Client connected. Waiting for first message from server...")

                // 1. Wait for RECORDS_SYNC (server speaks first)
                _uiState.value = AppUiState.Loading("Waiting for server records...")
                val initialMsg = withTimeout(5000) { network.receive() }

                if (initialMsg.type == MessageType.RECORDS_SYNC) {
                    println("DEBUG: Message received: ${initialMsg.type}")
                    recordsData = jsonParser.decodeFromString<RecordsData>(initialMsg.payload)
                    println("[GameClient] Processed ${recordsData?.records?.size ?: 0} records")
                } else {
                    println("[GameClient] WARNING: Expected RECORDS_SYNC, got ${initialMsg.type}")
                }

                // 2. Send CONNECT
                _uiState.value = AppUiState.Loading("Sending handshake...")
                val connectRequest = ConnectRequest(
                    playerName = playerName,
                    previousPlayerId = previousId
                )
                network.send(NetworkMessage(
                    type = MessageType.CONNECT,
                    payload = jsonParser.encodeToString(connectRequest)
                ))
                println("[GameClient] Connect request sent. Waiting for response...")

                // 3. Wait for CONNECT response
                _uiState.value = AppUiState.Loading("Waiting for authentication...")
                val response = withTimeout(5000) { network.receive() }

                if (response.type == MessageType.CONNECT) {
                    val connectResponse = jsonParser.decodeFromString<ConnectResponse>(response.payload)
                    if (connectResponse.success) {
                        playerId = connectResponse.playerId
                        connectResponse.playerId?.let { settings?.saveString("player_id", it) }
                        localPlayerName = playerName
                        println("[GameClient] Connected with ID: $playerId")

                        startMessageListener()

                        println("[GameClient] Handshake complete. Transitioning to Menu.")
                        _uiState.value = AppUiState.Content(Screen.Menu)
                        return  // SUCCESS
                    } else {
                        throw Exception(connectResponse.message)
                    }
                } else {
                    throw Exception("Unexpected response type: ${response.type}")
                }

            } catch (e: Exception) {
                attempts++
                println("[GameClient] Connection attempt $attempts failed: ${e.message}")
                try { network.close() } catch (_: Exception) {}

                if (attempts < maxAttempts) {
                    val delayMs = (1000 * 2.0.pow(attempts - 1)).toLong()
                    println("[GameClient] Retrying in ${delayMs}ms...")
                    _uiState.value = AppUiState.Loading("Retrying... ($attempts/$maxAttempts)")
                    delay(delayMs)
                } else {
                    _uiState.value = AppUiState.Error(
                        message = "Failed to connect after $maxAttempts attempts: ${e.message}",
                        canRetry = true
                    )
                    return
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
                            if (!network.isConnected() ||
                                e.message?.contains("Connection reset") == true ||
                                e.message?.contains("Connection closed") == true ||
                                e.message?.contains("closed") == true) {
                                println("[GameClient] Connection lost, stopping listener")
                                scope.launch(Dispatchers.Main) {
                                    _isConnectionLost.value = true
                                }
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

                    if (_roundEndResult.value != null) {
                        println("[GameClient] Clearing round end overlay due to new game state")
                        _roundEndResult.value = null
                    }

                    val previousState = _currentGameState.value
                    _currentGameState.value = gameState
                    currentMatchId = gameState.matchId
                    currentTimeLimit = gameState.timeLimit

                    val turnChanged = previousState?.currentPlayer != gameState.currentPlayer
                    val roundChanged = previousState?.currentRound != gameState.currentRound
                    if (previousState == null || turnChanged || roundChanged) startTimer()

                    scope.launch(Dispatchers.Main) {
                        val currentState = _uiState.value
                        if (currentState !is AppUiState.Content || currentState.currentScreen !is Screen.Game) {
                            println("[GameClient] Navigating to Game screen for match: ${gameState.matchId}")
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
                    scope.launch(Dispatchers.Main) {
                        delay(3000)
                        _roundEndResult.value = null
                    }
                }
                MessageType.MATCH_END -> {
                    val matchEnd = jsonParser.decodeFromString<MatchEnd>(message.payload)
                    println("[GameClient] Match ended. Winner: ${matchEnd.winner}")
                    stopTimer()

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
                        _roundEndResult.value = RoundEnd(
                            winner = winnerSymbol,
                            isDraw = matchEnd.winner == "DRAW",
                            reason = matchEnd.reason
                        )
                    }

                    scope.launch(Dispatchers.Main) {
                        delay(4000)
                        _uiState.value = AppUiState.Content(Screen.Menu)
                        _currentGameState.value = null
                        currentMatchId = null
                        _roundEndResult.value = null
                    }
                }
                MessageType.RECORDS_SYNC -> {
                    recordsData = jsonParser.decodeFromString<RecordsData>(message.payload)
                    println("[GameClient] Records updated: ${recordsData?.records?.size} records")
                }
                MessageType.ERROR -> {
                    val error = jsonParser.decodeFromString<ErrorMessage>(message.payload)
                    println("[GameClient] Server error: ${error.message}")
                }
                MessageType.GAME_FOUND -> {
                    val gameFound = jsonParser.decodeFromString<GameFound>(message.payload)
                    println("[GameClient] Game found! MatchId: ${gameFound.matchId}, Opponent: ${gameFound.opponentName}")
                    opponentName = gameFound.opponentName
                    scope.launch(Dispatchers.Main) {
                        _uiState.value = AppUiState.Loading("Opponent found: ${gameFound.opponentName}. Preparing game...")
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
                else -> println("[GameClient] Ignoring message type: ${message.type}")
            }
        } catch (e: Exception) {
            println("ERROR: Unexpected error handling message ${message.type}: ${e.message}")
            e.printStackTrace()
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
            _uiState.value = AppUiState.Error(
                message = "Not connected to server. Please restart and try again.",
                canRetry = false
            )
            return
        }

        _currentGameState.value = null
        _roundEndResult.value = null
        currentMatchId = null
        currentTimeLimit = timeLimit
        currentTurboMode = turboMode
        currentPracticeMode = practiceMode

        val config = GameConfig(
            boardSize = boardSize,
            winLength = winLength,
            totalRounds = totalRounds,
            difficulty = difficulty,
            timeLimit = timeLimit,
            turboMode = turboMode,
            practiceMode = practiceMode
        )

        try {
            network.send(NetworkMessage(
                type = MessageType.CREATE_GAME,
                payload = jsonParser.encodeToString(config)
            ))
            pveMode = true
            _uiState.value = AppUiState.Loading("Creating game...")
        } catch (e: Exception) {
            println("[GameClient] Failed to send create game message: ${e.message}")
            _uiState.value = AppUiState.Error(
                message = "Failed to create game: ${e.message}",
                canRetry = true
            )
        }
    }

    suspend fun makeMove(row: Int, col: Int) {
        println("[GameClient] Sending move: row $row, col $col")
        stopTimer()
        network.send(NetworkMessage(
            type = MessageType.MAKE_MOVE,
            payload = jsonParser.encodeToString(MoveRequest(position = Position(row, col)))
        ))
        println("[GameClient] Move sent to server")
    }

    suspend fun requestUndo() {
        val matchId = currentMatchId ?: return
        println("[GameClient] Requesting Undo for match $matchId")
        network.send(NetworkMessage(
            type = MessageType.UNDO_REQUEST,
            payload = jsonParser.encodeToString(UndoRequest(matchId))
        ))
    }

    suspend fun joinQueue(preferredBoardSize: Int = 3, timeLimit: Int = 30, totalRounds: Int = 3) {
        if (!network.isConnected()) {
            _uiState.value = AppUiState.Error(
                message = "Not connected to server. Please restart and try again.",
                canRetry = false
            )
            return
        }
        val player = playerId ?: return
        network.send(NetworkMessage(
            type = MessageType.JOIN_QUEUE,
            payload = jsonParser.encodeToString(JoinQueueRequest(player, preferredBoardSize, timeLimit, totalRounds))
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
        stopTimer()
        network.close()
        currentMatchId = null
        _currentGameState.value = null
        _isConnectionLost.value = false
        _isOpponentDisconnected.value = false
        _uiState.value = AppUiState.Content(Screen.Menu)
    }
}