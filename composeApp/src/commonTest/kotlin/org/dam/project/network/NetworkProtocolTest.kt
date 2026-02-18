package org.dam.project.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NetworkProtocolTest {
    
    private val json = Json
    
    @Test
    fun testPositionSerialization() {
        val position = Position(row = 1, col = 2)
        val jsonString = json.encodeToString(position)
        val decoded = json.decodeFromString<Position>(jsonString)
        assertEquals(position, decoded)
    }
    
    @Test
    fun testGameConfigSerialization() {
        val config = GameConfig(
            boardSize = 3,
            winLength = 3,
            totalRounds = 5,
            difficulty = Difficulty.HARD
        )
        val jsonString = json.encodeToString(config)
        val decoded = json.decodeFromString<GameConfig>(jsonString)
        assertEquals(config, decoded)
    }
    
    @Test
    fun testMoveRequestSerialization() {
        val moveRequest = MoveRequest(
            position = Position(1, 2)
        )
        val jsonString = json.encodeToString(moveRequest)
        val decoded = json.decodeFromString<MoveRequest>(jsonString)
        assertEquals(moveRequest, decoded)
    }
    
    @Test
    fun testMoveResultSerialization() {
        val moveResult = MoveResult(
            player = "X",
            position = Position(1, 2),
            valid = true
        )
        val jsonString = json.encodeToString(moveResult)
        val decoded = json.decodeFromString<MoveResult>(jsonString)
        assertEquals(moveResult, decoded)
    }
    
    @Test
    fun testMoveResultWithError() {
        val moveResult = MoveResult(
            player = "X",
            position = Position(1, 2),
            valid = false,
            errorMessage = "Cell already occupied"
        )
        val jsonString = json.encodeToString(moveResult)
        val decoded = json.decodeFromString<MoveResult>(jsonString)
        assertEquals(moveResult, decoded)
    }
    
    @Test
    fun testGameStateSerialization() {
        val gameState = GameState(
            matchId = "match123",
            board = listOf(
                listOf("X", "O", ""),
                listOf("", "X", ""),
                listOf("", "", "O")
            ),
            boardSize = 3,
            currentPlayer = "X",
            nextPlayer = "X",
            currentRound = 1,
            scores = mapOf("X" to 0, "O" to 0),
            playerXId = "player-x",
            playerOId = "player-o",
            timeLimit = 30
        )
        val jsonString = json.encodeToString(gameState)
        val decoded = json.decodeFromString<GameState>(jsonString)
        assertEquals(gameState, decoded)
    }
    
    @Test
    fun testRoundEndWithWinner() {
        val roundEnd = RoundEnd(
            winner = "X",
            winningLine = listOf(
                Position(0, 0),
                Position(1, 1),
                Position(2, 2)
            ),
            isDraw = false
        )
        val jsonString = json.encodeToString(roundEnd)
        val decoded = json.decodeFromString<RoundEnd>(jsonString)
        assertEquals(roundEnd, decoded)
    }
    
    @Test
    fun testRoundEndWithDraw() {
        val roundEnd = RoundEnd(
            winner = null,
            winningLine = null,
            isDraw = true
        )
        val jsonString = json.encodeToString(roundEnd)
        val decoded = json.decodeFromString<RoundEnd>(jsonString)
        assertEquals(roundEnd, decoded)
    }
    
    @Test
    fun testMatchEndSerialization() {
        val matchEnd = MatchEnd(
            winner = "Player1",
            score = mapOf(
                "player1" to 3,
                "player2" to 1
            )
        )
        val jsonString = json.encodeToString(matchEnd)
        val decoded = json.decodeFromString<MatchEnd>(jsonString)
        assertEquals(matchEnd, decoded)
    }
    
    @Test
    fun testPlayerRecordSerialization() {
        val record = PlayerRecord(
            playerName = "TestPlayer",
            wins = 10,
            losses = 5,
            draws = 2,
            currentStreak = 3,
            bestStreak = 15
        )
        val jsonString = json.encodeToString(record)
        val decoded = json.decodeFromString<PlayerRecord>(jsonString)
        assertEquals(record, decoded)
    }
    
    @Test
    fun testRecordsDataSerialization() {
        val recordsData = RecordsData(
            records = listOf(
                PlayerRecord("Player1", wins = 10, losses = 5),
                PlayerRecord("Player2", wins = 8, losses = 7)
            )
        )
        val jsonString = json.encodeToString(recordsData)
        val decoded = json.decodeFromString<RecordsData>(jsonString)
        assertEquals(recordsData, decoded)
    }
    
    @Test
    fun testConnectRequestSerialization() {
        val connectRequest = ConnectRequest(
            playerName = "TestPlayer",
            clientVersion = "1.0.0"
        )
        val jsonString = json.encodeToString(connectRequest)
        val decoded = json.decodeFromString<ConnectRequest>(jsonString)
        assertEquals(connectRequest, decoded)
    }
    
    @Test
    fun testConnectResponseSerialization() {
        val connectResponse = ConnectResponse(
            success = true,
            message = "Connected successfully",
            playerId = "player123"
        )
        val jsonString = json.encodeToString(connectResponse)
        val decoded = json.decodeFromString<ConnectResponse>(jsonString)
        assertEquals(connectResponse, decoded)
    }
    
    @Test
    fun testErrorMessageSerialization() {
        val errorMessage = ErrorMessage(
            code = "INVALID_MOVE",
            message = "The move is not valid",
            recoverable = true
        )
        val jsonString = json.encodeToString(errorMessage)
        val decoded = json.decodeFromString<ErrorMessage>(jsonString)
        assertEquals(errorMessage, decoded)
    }
    
    @Test
    fun testNetworkMessageSerialization() {
        val gameConfig = GameConfig(
            boardSize = 3,
            winLength = 3,
            totalRounds = 5,
            difficulty = Difficulty.MEDIUM
        )
        val payload = json.encodeToString(gameConfig)
        
        val networkMessage = NetworkMessage(
            type = MessageType.CREATE_GAME,
            payload = payload
        )
        val jsonString = json.encodeToString(networkMessage)
        val decoded = json.decodeFromString<NetworkMessage>(jsonString)
        
        assertEquals(networkMessage.type, decoded.type)
        assertNotNull(decoded.payload)
        
        // Verify payload can be decoded back to GameConfig
        val decodedConfig = json.decodeFromString<GameConfig>(decoded.payload)
        assertEquals(gameConfig, decodedConfig)
    }
    
    @Test
    fun testDifficultySerialization() {
        val difficulties = listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)
        
        difficulties.forEach { difficulty ->
            val jsonString = json.encodeToString(difficulty)
            val decoded = json.decodeFromString<Difficulty>(jsonString)
            assertEquals(difficulty, decoded)
        }
    }
    
    @Test
    fun testMessageTypeSerialization() {
        val types = listOf(
            MessageType.CONNECT,
            MessageType.CREATE_GAME,
            MessageType.MAKE_MOVE,
            MessageType.GAME_STATE,
            MessageType.MOVE_RESULT,
            MessageType.ROUND_END,
            MessageType.MATCH_END,
            MessageType.RECORDS_SYNC,
            MessageType.ERROR
        )
        
        types.forEach { type ->
            val jsonString = json.encodeToString(type)
            val decoded = json.decodeFromString<MessageType>(jsonString)
            assertEquals(type, decoded)
        }
    }
}
