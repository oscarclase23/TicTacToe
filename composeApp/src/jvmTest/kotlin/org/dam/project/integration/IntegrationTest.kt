package org.dam.project.integration

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.dam.project.client.AppUiState
import org.dam.project.client.GameClient
import org.dam.project.client.NetworkClient
import org.dam.project.client.Screen
import org.dam.project.network.Difficulty
import org.dam.project.network.GameConfig
import org.dam.project.server.GameServer
import kotlin.test.*
import java.net.ServerSocket

class IntegrationTest {

    private val testHost = "localhost"
    private var testPort = 0
    private lateinit var server: GameServer
    
    @BeforeTest
    fun setup() {
        // Find a free port
        val socket = ServerSocket(0)
        testPort = socket.localPort
        socket.close()
        
        // Start server
        server = GameServer(testPort)
        // Run server in background
        CoroutineScope(Dispatchers.IO).launch {
            server.start()
        }
        // Give server time to start
        Thread.sleep(1200)
    }
    
    @AfterTest
    fun teardown() {
        server.stop()
    }
    
    @Test
    fun testFullGameFlow() = runBlocking {
        println("Starting output integration test...")
        
        // 1. Initialize Client
        val client = GameClient(NetworkClient())
        
        // 2. Connect (retry in case server is still starting)
        println("Connecting to server...")
        var connected = false
        var lastError: String? = null
        repeat(5) { attempt ->
            try {
                client.connect(testHost, testPort, "TestPlayer")
            } catch (e: Exception) {
                lastError = e.message
                println("Connection attempt ${attempt + 1} failed with exception: ${e.message}")
            }
            val state = client.uiState.value
            if (state is AppUiState.Content && state.currentScreen is Screen.Menu) {
                connected = true
                return@repeat
            }
            if (state is AppUiState.Error) {
                lastError = state.message
                println("Connection attempt ${attempt + 1} failed. UI State Error: ${state.message}")
            }
            delay(600)
        }
        assertTrue(connected, "Failed to connect to server. Last error: ${lastError ?: "unknown"}")

        // Verify we are in Menu (wait for UI state to settle)
        val menuState = withTimeout(10000) {
            client.uiState.first { state ->
                state is AppUiState.Content && state.currentScreen is Screen.Menu
            }
        }
        assertTrue(menuState is AppUiState.Content, "State should be Content, was $menuState")
        assertTrue(menuState.currentScreen is Screen.Menu, "Screen should be Menu")
        println("Connected and in Menu")
        
        // 3. Create PVE Game
        println("Creating PVE game...")
        client.createPVEGame(
            boardSize = 3,
            winLength = 3,
            totalRounds = 3,
            difficulty = Difficulty.EASY
        )
        
        // Wait for game to start (Game state update)
        // We can poll currentGameState or uiState
        withTimeout(10000) {
            while (client.currentGameState.value == null) {
                delay(100)
            }
        }
        
        val gameState = client.currentGameState.value
        assertNotNull(gameState, "Game state should not be null")
        assertEquals(3, gameState.boardSize)
        println("Game created with Match ID: ${gameState.matchId}")
        
        // 4. Make a Move
        println("Making a move at (0,0)...")
        // Assuming we are 'X' and it is our turn (PVE usually starts with player)
        assertEquals("X", gameState.currentPlayer, "Player X should start")
        
        client.makeMove(0, 0)
        
        // Wait for update (Player moved, then AI moved, so board should have X and O)
        withTimeout(10000) {
            // Wait until board has 'X' at 0,0
            while (client.currentGameState.value?.board?.get(0)?.get(0) != "X") {
                delay(100)
            }
        }
        
        val updatedState = client.currentGameState.value!!
        assertEquals("X", updatedState.board[0][0], "Cell (0,0) should be X")
        println("Move successful. Board state:\n${updatedState.board.joinToString("\n")}")
        
        // 5. Disconnect
        println("Disconnecting...")
        client.disconnect()
        
        val currentState = client.uiState.value
        assertTrue((currentState as AppUiState.Content).currentScreen is Screen.Menu, "Should return to Menu after disconnect")
        
        println("Integration test passed!")
    }
}
