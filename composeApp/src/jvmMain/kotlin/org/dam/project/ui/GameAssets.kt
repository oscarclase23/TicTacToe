package org.dam.project.ui

import org.jetbrains.compose.resources.DrawableResource
import tictactoe.composeapp.generated.resources.*

/**
 * Maps game logic symbols to visual assets.
 */
object GameAssets {
    /**
     * Gets the drawable resource for a player symbol.
     * 
     * @param symbol "X" or "O"
     * @return Corresponding drawable resource
     */
    fun getSymbolDrawable(symbol: String): DrawableResource {
        return when (symbol) {
            "X" -> Res.drawable.cross
            "O" -> Res.drawable.target
            else -> Res.drawable.cross // Default
        }
    }
    
    /**
     * Icon resources for UI elements.
     */
    val pvpIcon: DrawableResource = Res.drawable.multiplayer
    val pveIcon: DrawableResource = Res.drawable.singleplayer
    val recordsIcon: DrawableResource = Res.drawable.trophy
    val homeIcon: DrawableResource = Res.drawable.home
    val exitIcon: DrawableResource = Res.drawable.cross
    
    /**
     * Game outcome icons.
     */
    val winIcon: DrawableResource = Res.drawable.crown_a
    val loseIcon: DrawableResource = Res.drawable.skull
    val drawIcon: DrawableResource = Res.drawable.contrast
    val restartIcon: DrawableResource = Res.drawable.contrast // Fallback icon for Undo

}
