package org.dam.project.client

/**
 * UI state sealed class hierarchy for state-based navigation.
 */
sealed class AppUiState {
    /**
     * Loading state (connecting, initializing).
     */
    data class Loading(val message: String = "Connecting...") : AppUiState()
    
    /**
     * Content state with current screen.
     */
    data class Content(val currentScreen: Screen) : AppUiState()
    
    /**
     * Error state with message and retry capability.
     */
    data class Error(
        val message: String,
        val canRetry: Boolean
    ) : AppUiState()
}

/**
 * Screen sealed class for navigation.
 */
sealed class Screen {
    /**
     * Login screen.
     */
    data object Login : Screen()

    /**
     * Main menu screen.
     */
    data object Menu : Screen()
    
    /**
     * Records/leaderboard screen.
     */
    data object Records : Screen()
    
    /**
     * Configuration screen.
     */
    data class Config(val isPvp: Boolean = false) : Screen()
    
    /**
     * Active game screen.
     */
    data class Game(val matchId: String) : Screen()
    
    /**
     * Waiting for match screen (PVP).
     */
    data object WaitingForMatch : Screen()
}
