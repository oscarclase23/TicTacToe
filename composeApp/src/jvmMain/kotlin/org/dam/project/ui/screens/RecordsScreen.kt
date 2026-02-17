package org.dam.project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dam.project.client.GameClient
import org.dam.project.client.Screen
import org.dam.project.network.PlayerRecord
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource

/**
 * Records screen displaying player statistics.
 */
@Composable
fun RecordsScreen(gameClient: GameClient) {
    val scope = rememberCoroutineScope()
    
    // Get records from GameClient
    val records = gameClient.getRecords()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hall of Fame",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(
                onClick = {
                    scope.launch {
                        gameClient.navigateTo(Screen.Menu)
                    }
                }
            ) {
                Image(
                    painter = painterResource(GameAssets.homeIcon),
                    contentDescription = "Back to menu",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Records list
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records yet. Play some games!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(records) { record ->
                    RecordCard(record)
                }
            }
        }
    }
}

/**
 * Card displaying a single player record.
 */
@Composable
private fun RecordCard(record: PlayerRecord) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Name + Basic Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.playerName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Total Games: ${record.wins + record.losses + record.draws}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                // Basic Win Rate
                Column(horizontalAlignment = Alignment.End) {
                    val total = record.wins + record.losses + record.draws
                    val winRate = if (total > 0) (record.wins.toFloat() / total * 100).toInt() else 0
                    
                    Text(
                        text = "$winRate% Win Rate",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Best Streak: ${record.bestStreak}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Divider()
            
            // Primary Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn("Wins", "${record.wins}")
                StatColumn("Losses", "${record.losses}")
                StatColumn("Draws", "${record.draws}")
            }
            
            // Expand Button
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (expanded) "Show Less" else "Show Details")
            }
            
            // Detailed Stats (Expanded)
            if (expanded) {
                Divider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // PVP Stats
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PVP Performance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("W: ${record.pvpWins}  L: ${record.pvpLosses}  D: ${record.pvpDraws}", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    // PVE Stats
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PVE Performance", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("W: ${record.pveWins}  L: ${record.pveLosses}  D: ${record.pveDraws}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Advanced Metrics
                Text("Advanced Metrics", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                
                val totalGames = record.wins + record.losses + record.draws
                val avgTime = if (totalGames > 0) record.totalTimeSeconds / totalGames else 0
                val favMove = record.favoriteMove?.let { "(${it.row},${it.col})" } ?: "None"
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Avg Time/Match: ${avgTime}s", style = MaterialTheme.typography.bodySmall)
                    Text("Total Moves: ${record.totalMoves}", style = MaterialTheme.typography.bodySmall)
                    Text("Favorite Move: $favMove", style = MaterialTheme.typography.bodySmall)
                }
                
                // Wins by Board Size
                if (record.winsByBoardSize.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Wins by Board Size", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        record.winsByBoardSize.forEach { (size, wins) ->
                            Text("${size}x${size}: $wins", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                // Wins by AI Difficulty
                if (record.winVsAI.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Wins vs AI Difficulty", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        record.winVsAI.forEach { (difficulty, wins) ->
                            Text("$difficulty: $wins", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Column displaying a statistic label and value.
 */
@Composable
private fun StatColumn(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
