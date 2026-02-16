package org.dam.project.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

/**
 * Main game client logic.
 * Manages network connection, game state, and UI state.
 */
class GameClient(
    private val network: NetworkClient,
    private val settings: ClientSettings? = null // Optional for now to avoid breaking other platforms if any
) {
    
    private val scope = CoroutineScope(Dispatchers.Main)
    private val jsonParser = Json { ignoreUnknownKeys = true }
    // UI State
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    
    // Game State
    private val _currentGameState = MutableStateFlow<GameState?>(null)
    val currentGameState: StateFlow<GameState?> = _currentGameState.asStateFlow()
    
    // Round End State (for showing win/lose/draw messages)
    private val _roundEndResult = MutableStateFlow<RoundEnd?>(null)
    val roundEndResult: StateFlow<RoundEnd?> = _roundEndResult.asStateFlow()
    
    // Timer State (for countdown display)
    private val _timeRemaining = MutableStateFlow<Int?>(null)
    val timeRemaining: StateFlow<Int?> = _timeRemaining.asStateFlow()
    
    // Connection State Overlay
    private val _isOpponentDisconnected = MutableStateFlow(false)
    val isOpponentDisconnected: StateFlow<Boolean> = _isOpponentDisconnected.asStateFlow()
    
    private val _isConnectionLost = MutableStateFlow(false)
    val isConnectionLost: StateFlow<Boolean> = _isConnectionLost.asStateFlow()

    // Connection info
    private var playerId: String? = null
    var opponentName: String? = null // Public so GameScreen can read it
    private var localPlayerName: String? = null // Captured on connect
    private var pveMode: Boolean = false
    private var currentMatchId: String? = null
    private var recordsData: RecordsData? = null
    private var currentTimeLimit: Int = 30
    private var currentTurboMode: Boolean = false
    private var currentPracticeMode: Boolean = false;
    
    // Message listener job
    private var messageListenerJob: Job? = null
    private var timerJob: Job? = null

    suspend fun connect(host: String, port: Int, playerName: String, allowResume: Boolean = false) {
        _uiState.value = AppUiState.Loading("Initializing...")
        
        if (!allowResume) {
            // Fresh session by default to avoid auto-resuming old matches.
            playerId = null
        } else {
            // Load saved ID if available and not already set
            if (playerId == null && settings != null) {
                val savedId = settings.getString("player_id")
                if (savedId != null) {
                    println("[GameClient] Loaded saved player ID: $savedId")
                    playerId = savedId
                }
            }
        }
        
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            try {
                // Attempt connection
                _uiState.value = AppUiState.Loading("Connecting to $host:$port...")
                network.connect(host, port)
                println("DEBUG: Client connected. Waiting for first message from server...")
                
                // 1. Wait for Records (Server speaks first)
                _uiState.value = AppUiState.Loading("Waiting for server records...")
                val initialMsg = withTimeout(5000) {
                    network.receive()
                }
                
                if (initialMsg.type == MessageType.RECORDS_SYNC) {
                    println("DEBUG: Message received: ${initialMsg.type}")
                    recordsData = jsonParser.decodeFromString<RecordsData>(initialMsg.payload)
                    println("[GameClient] Processed ${recordsData?.records?.size ?: 0} records")
                } else {
                    println("[GameClient] WARNING: Expected RECORDS_SYNC, got ${initialMsg.type}")
                }
                
                // 2. Send Connect Request
                _uiState.value = AppUiState.Loading("Sending handshake...")
                val connectRequest = ConnectRequest(
                    playerName = playerName,
                    previousPlayerId = if (allowResume) playerId else null
                )
                val message = NetworkMessage(
                    type = MessageType.CONNECT,
                    payload = jsonParser.encodeToString(connectRequest)
                )
                network.send(message)
                println("[GameClient] Connect request sent. Waiting for response...")
                
                // 3. Wait for Connect Response
                _uiState.value = AppUiState.Loading("Waiting for authentication...")
                val response = withTimeout(5000) {
                    network.receive()
                }
                
                if (response.type == MessageType.CONNECT) {
                    val connectResponse = jsonParser.decodeFromString<ConnectResponse>(response.payload)
                    if (connectResponse.success) {
                        playerId = connectResponse.playerId
                        // Save ID
                        connectResponse.playerId?.let {
                            settings?.saveString("player_id", it)
                        }
                        
                        localPlayerName = playerName
                        println("[GameClient] Connected with ID: $playerId")
                        
                        // Start message listener
                        startMessageListener()
                        
                        // Show menu
                        println("[GameClient] Handshake complete. Transitioning to Menu.")
                        _uiState.value = AppUiState.Content(Screen.Menu)
                        return
                    } else {
                        throw Exception(connectResponse.message)
                    }
                } else {
                    throw Exception("Unexpected response type: ${response.type}")
                }
                
            } catch (e: Exception) {
                attempts++
                println("[GameClient] Connection attempt $attempts failed: ${e.message}")
                e.printStackTrace()
                
                if (attempts < maxAttempts) {
                    // Exponential backoff: 1s, 2s, 4s
                    val delayMs = (1000 * 2.0.pow(attempts - 1)).toLong()
                    println("[GameClient] Retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    _uiState.value = AppUiState.Error(
                        message = "Failed to connect to server after $maxAttempts attempts: ${e.message}",
                        canRetry = true
                    )
                    network.close()
                    return
                }
            }
        }
    }

    // ...

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
                            println("ERROR: Message listener error receiving message: ${e.message}")
                            e.printStackTrace()
                            
                            // Check if connection is lost or reset
                            if (e.message?.contains("Connection reset") == true || 
                                e.cause?.message?.contains("Connection reset") == true ||
                                !network.isConnected()) {
                                println("[GameClient] Connection lost (Reset), stopping listener")
                                scope.launch(Dispatchers.Main) {
                                    // Use overlay instead of full error screen to allow auto-reconnect logic if we add it
                                    _isConnectionLost.value = true
                                    // Also show visual feedback immediately
                                    // _uiState.value = AppUiState.Error("Connection lost to server.", true) 
                                }
                                break
                            }
                            // Add a small delay to avoid tight loop on repeated errors
                            delay(1000)
                        }
                    }
                }
            } catch (e: Exception) {
                println("ERROR: Message listener crashed: ${e.message}")
                e.printStackTrace()
            } finally {
                println("[GameClient] Message listener stopped")
                // Ensure we disconnect gracefully if the loop exits
                // network.close() // Optional, maybe let the user retry explicitly
            }
        }
    }
    
    /**
     * Stops the message listener.
     */
    private fun stopMessageListener() {
        messageListenerJob?.cancel()
        messageListenerJob = null
    }
    
    /**
     * Handles incoming messages.
     */
    private fun handleMessage(message: NetworkMessage) {
        println("[GameClient] Received message: ${message.type}")
        
        try {
            when (message.type) {
                MessageType.GAME_STATE -> {
                    try {
                        val gameState = jsonParser.decodeFromString<GameState>(message.payload)
                        println("[GameClient] Decoded GameState: matchId=${gameState.matchId}, currentPlayer=${gameState.currentPlayer}")
                        
                        // Clear any round end result immediately when new state arrives
                        // This prevents blocking user interaction while the timer for the next round is running
                        if (_roundEndResult.value != null) {
                            println("[GameClient] Clearing round end overlay due to new game state")
                            _roundEndResult.value = null
                        }
                        
                        val previousState = _currentGameState.value
                        _currentGameState.value = gameState
                        currentMatchId = gameState.matchId
                        
                        // Start/stop timer based on turn
                        val playerId = this@GameClient.playerId
                        val playerSymbol = when (playerId) {
                            gameState.playerXId -> "X"
                            gameState.playerOId -> "O"
                            else -> null
                        }
                        
                        val isPlayerTurn = playerSymbol == gameState.currentPlayer
                        
                        // Update current time limit from server state to ensure synchronization
                        currentTimeLimit = gameState.timeLimit
                        // Since we have the definitive time from server, update turbo mode flag too if needed
                        // (though timeLimit is the source of truth now)
                        if (currentTimeLimit <= 10) currentTurboMode = true

                        // Check if turn or round changed
                        val turnChanged = previousState?.currentPlayer != gameState.currentPlayer
                        val roundChanged = previousState?.currentRound != gameState.currentRound
                        
                        // Always start/restart timer on turn change, regardless of whose turn it is
                        if (previousState == null || turnChanged || roundChanged) {
                             startTimer()
                        }
                        
                        // Note: If we just reconnected, startTimer() above handles it.
                        
                        // Update UI to show game screen on Main thread
                        scope.launch(Dispatchers.Main) {
                            val currentState = _uiState.value
                            if (currentState !is AppUiState.Content || currentState.currentScreen !is Screen.Game) {
                                println("[GameClient] Navigating to Game screen for match: ${gameState.matchId}")
                                _uiState.value = AppUiState.Content(Screen.Game(gameState.matchId))
                            }
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode GameState: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.MOVE_RESULT -> {
                    try {
                        val moveResult = jsonParser.decodeFromString<MoveResult>(message.payload)
                        if (!moveResult.valid) {
                            println("[GameClient] Invalid move: ${moveResult.errorMessage}")
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode MoveResult: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.ROUND_END -> {
                    try {
                        val roundEnd = jsonParser.decodeFromString<RoundEnd>(message.payload)
                        println("[GameClient] Round ended. Winner: ${roundEnd.winner}")
                        
                        // Stop timer immediately
                        stopTimer()
                        
                        // Store round end result to show in UI (for both PVE and PVP)
                        _roundEndResult.value = roundEnd
                        
                        // Clear the result after 3 seconds
                        scope.launch(Dispatchers.Main) {
                            delay(3000)
                            _roundEndResult.value = null
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode RoundEnd: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.MATCH_END -> {
                    try {
                        val matchEnd = jsonParser.decodeFromString<MatchEnd>(message.payload)
                        println("[GameClient] Match ended. Winner: ${matchEnd.winner}")
                        
                        // Stop timer immediately
                        stopTimer()
                        
                        // Map MatchEnd to RoundEnd to show the overlay
                        val currentGState = _currentGameState.value
                        if (currentGState != null) {
                            val myId = playerId
                            val amIX = currentGState.playerXId == myId
                            val mySymbol = if (amIX) "X" else "O"
                            val opponentSymbol = if (amIX) "O" else "X"
                            
                            // Determine winner symbol from Winner Name
                            // Logic: If winnerName == myName -> mySymbol
                            // Else -> opponentSymbol
                            val winnerSymbol = if (matchEnd.winner == localPlayerName) mySymbol else 
                                              (if (matchEnd.winner == "DRAW") null else opponentSymbol)
                            
                            val isDraw = matchEnd.winner == "DRAW"
                            
                            val roundEnd = RoundEnd(
                                winner = winnerSymbol,
                                isDraw = isDraw,
                                reason = matchEnd.reason
                            )
                            
                            _roundEndResult.value = roundEnd
                        }
                        
                        // Navigate back to menu after match ends
                        scope.launch(Dispatchers.Main) {
                            println("[GameClient] Waiting 4 seconds before returning to menu...")
                            delay(4000) // Show final state for 4 seconds (increased time to read reason)
                            println("[GameClient] Navigating to menu")
                            _uiState.value = AppUiState.Content(Screen.Menu)
                            _currentGameState.value = null
                            currentMatchId = null
                            _roundEndResult.value = null
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode MatchEnd: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.RECORDS_SYNC -> {
                    try {
                        recordsData = jsonParser.decodeFromString<RecordsData>(message.payload)
                        println("[GameClient] Records updated: ${recordsData?.records?.size} records")
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode RecordsData: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.ERROR -> {
                    try {
                        val error = jsonParser.decodeFromString<ErrorMessage>(message.payload)
                        println("[GameClient] Server error: ${error.message}")
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode ErrorMessage: ${e.message}")
                        e.printStackTrace()
                    }
                }
                MessageType.GAME_FOUND -> {
                    try {
                        val gameFound = jsonParser.decodeFromString<GameFound>(message.payload)
                        println("[GameClient] Game found! MatchId: ${gameFound.matchId}, Opponent: ${gameFound.opponentName}")
                        opponentName = gameFound.opponentName
                        
                        // Set state to loading while receiving initial game state
                        scope.launch(Dispatchers.Main) {
                            _uiState.value = AppUiState.Loading("Opponent found: ${gameFound.opponentName}. Preparing game...")
                        }
                    } catch (e: Exception) {
                        println("ERROR: Failed to decode GameFound: ${e.message}")
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
                    // Game state message usually follows, which will restart timer
                }
                else -> {
                    println("[GameClient] Ignoring message type: ${message.type}")
                }
            }
        } catch (e: Exception) {
            println("ERROR: Unexpected error handling message ${message.type}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Surrenders the current game.
     */
    suspend fun surrenderGame() {
        val matchId = currentMatchId ?: return
        if (!network.isConnected()) return

        val message = NetworkMessage(
            type = MessageType.SURRENDER,
            payload = ""
        )
        network.send(message)
    }
    
    /**
     * Creates a new PVE game.
     */
    suspend fun createPVEGame(
        boardSize: Int = 3,
        winLength: Int = 3,
        totalRounds: Int = 3,
        difficulty: Difficulty = Difficulty.EASY,
        timeLimit: Int = 30,
        turboMode: Boolean = false,
        practiceMode: Boolean = false
    ) {
        // Check if connected
        if (!network.isConnected()) {
            println("[GameClient] Not connected, attempting to reconnect...")
            try {
                connect("localhost", 5678, "Player")
            } catch (e: Exception) {
                println("[GameClient] Failed to reconnect: ${e.message}")
                _uiState.value = AppUiState.Error(
                    message = "Not connected to server. Please check if the server is running.",
                    canRetry = true
                )
                return
            }
        }
        
        // Clear any previous game state
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
        
        val message = NetworkMessage(
            type = MessageType.CREATE_GAME,
            payload = jsonParser.encodeToString(config)
        )
        
        try {
            network.send(message)
            pveMode = true
            
            // Optimistic UI update
            _uiState.value = AppUiState.Loading("Creating game...")
        } catch (e: Exception) {
            println("[GameClient] Failed to send create game message: ${e.message}")
            _uiState.value = AppUiState.Error(
                message = "Failed to create game: ${e.message}",
                canRetry = true
            )
        }
    }
    
    /**
     * Makes a move in the current game.
     */
    suspend fun makeMove(row: Int, col: Int) {
        println("[GameClient] Sending move: row $row, col $col")
        stopTimer() // Stop timer when making a move
        val pos = Position(row, col)
        val moveRequest = MoveRequest(position = pos)
        
        val message = NetworkMessage(
            type = MessageType.MAKE_MOVE,
            payload = jsonParser.encodeToString(moveRequest)
        )
        
        network.send(message)
        println("[GameClient] Move sent to server")
    }
    
    /**
     * Requests to undo the last move.
     */
    suspend fun requestUndo() {
        val matchId = currentMatchId ?: return
        println("[GameClient] Requesting Undo for match $matchId")
        
        val message = NetworkMessage(
            type = MessageType.UNDO_REQUEST,
            payload = jsonParser.encodeToString(UndoRequest(matchId))
        )
        network.send(message)
    }

    /**
     * Joins the multiplayer matchmaking queue.
     */
    suspend fun joinQueue(
        preferredBoardSize: Int = 3,
        timeLimit: Int = 30,
        totalRounds: Int = 3
    ) {
        // Check if connected
        if (!network.isConnected()) {
            println("[GameClient] Not connected, attempting to connect...")
            try {
                connect("localhost", 5678, "Player")
            } catch (e: Exception) {
                println("[GameClient] Failed to connect: ${e.message}")
                _uiState.value = AppUiState.Error(
                    message = "Not connected to server. Please check if the server is running.",
                    canRetry = true
                )
                return
            }
        }
        
        val player = playerId ?: return
        val message = NetworkMessage(
            type = MessageType.JOIN_QUEUE,
            payload = jsonParser.encodeToString(JoinQueueRequest(player, preferredBoardSize, timeLimit, totalRounds))
        )
        network.send(message)
        
        // Update UI State to Waiting
        _uiState.value = AppUiState.Content(Screen.WaitingForMatch)
    }
    
    /**
     * Cancels joining the queue.
     */
    suspend fun cancelQueue() {
        val message = NetworkMessage(
             type = MessageType.CANCEL_QUEUE,
             payload = ""
        )
        network.send(message)
        _uiState.value = AppUiState.Content(Screen.Menu)
    }
    /**
     * Starts the countdown timer for the current turn.
     */
    private fun startTimer() {
        stopTimer() // Stop any existing timer
        // Use the synchronized time limit
        val timeLimit = currentTimeLimit
        _timeRemaining.value = timeLimit
        
        timerJob = scope.launch(Dispatchers.Main) {
            while (_timeRemaining.value != null && _timeRemaining.value!! > 0) {
                delay(1000)
                val current = _timeRemaining.value
                if (current != null && current > 0) {
                    _timeRemaining.value = current - 1
                } else {
                    break
                }
            }
            
            // Time's up - could notify server or handle timeout
            if (_timeRemaining.value == 0) {
                println("[GameClient] Time's up!")
                _timeRemaining.value = null
            }
        }
    }
    
    /**
     * Stops the countdown timer.
     */
    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _timeRemaining.value = null
    }
    
    /**
     * Navigates to a specific screen.
     */
    fun navigateTo(screen: Screen) {
        _uiState.value = AppUiState.Content(screen)
    }
    
    /**
     * Gets the current player ID.
     */
    fun getPlayerId(): String? = playerId
    
    /**
     * Gets the list of player records.
     */
    fun getRecords(): List<PlayerRecord> {
        return recordsData?.records ?: emptyList()
    }

    /**
     * Checks if current game is in practice mode.
     */
    fun isPracticeMode(): Boolean = currentPracticeMode
    
    /**
     * Cleans up resources when the application is closing.
     */
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
