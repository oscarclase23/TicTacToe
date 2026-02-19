package org.dam.project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.network.Difficulty
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource

/**
 * Main menu screen with game mode selection.
 */
@Composable
fun MainMenuScreen(gameClient: GameClient) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Title
            Text(
                text = "Medieval Battle",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "Welcome, ${gameClient.getPlayerName() ?: "Warrior"}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Versus Player button
            MenuButton(
                text = "Versus Player",
                icon = GameAssets.pvpIcon,
                onClick = {
                    // Navigate to Config screen with PVP mode
                    gameClient.navigateTo(org.dam.project.client.Screen.Config(isPvp = true))
                }
            )
            
            // Versus AI button
            MenuButton(
                text = "Versus AI",
                icon = GameAssets.pveIcon,
                onClick = {
                    gameClient.navigateTo(org.dam.project.client.Screen.Config(isPvp = false))
                }
            )
            
            // Hall of Fame button
            MenuButton(
                text = "Hall of Fame",
                icon = GameAssets.recordsIcon,
                onClick = {
                    gameClient.navigateTo(org.dam.project.client.Screen.Records)
                }
            )
            
            // Exit button
            MenuButton(
                text = "Exit",
                icon = GameAssets.exitIcon,
                onClick = {
                    System.exit(0)
                }
            )
        }
    }
}

/**
 * Reusable menu button with icon and text.
 */
@Composable
private fun MenuButton(
    text: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}


