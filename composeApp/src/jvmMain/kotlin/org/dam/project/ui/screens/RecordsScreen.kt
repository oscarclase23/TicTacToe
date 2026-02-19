package org.dam.project.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.dam.project.client.GameClient
import org.dam.project.client.Screen
import org.dam.project.network.PlayerRecord
import org.dam.project.ui.GameAssets
import org.jetbrains.compose.resources.painterResource

@Composable
fun RecordsScreen(gameClient: GameClient) {
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
                text = "🏆 Hall of Fame",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { gameClient.navigateTo(Screen.Menu) }) {
                Image(
                    painter = painterResource(GameAssets.homeIcon),
                    contentDescription = "Back",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thickness = 2.dp)
        Spacer(modifier = Modifier.height(16.dp))

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay registros aún.\n¡Juega una partida!",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(records) { index, record ->
                    RecordCard(rank = index + 1, record = record)
                }
            }
        }
    }
}

@Composable
private fun RecordCard(rank: Int, record: PlayerRecord) {
    var expanded by remember { mutableStateOf(false) }

    val totalGames = record.wins + record.losses + record.draws
    val winRate = if (totalGames > 0) (record.wins.toFloat() / totalGames * 100).toInt() else 0

    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // ── Header Row ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(rankColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.titleSmall,
                        color = rankColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.playerName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$totalGames partidas · Racha actual: ${record.currentStreak}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$winRate%",
                        style = MaterialTheme.typography.headlineMedium,
                        color = when {
                            winRate >= 60 -> Color(0xFF4CAF50)
                            winRate >= 40 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.error
                        },
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "victorias",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Primary Stats ────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatPill("✅ Victorias", "${record.wins}", Color(0xFF4CAF50))
                StatPill("❌ Derrotas", "${record.losses}", MaterialTheme.colorScheme.error)
                StatPill("🤝 Empates", "${record.draws}", Color(0xFFFF9800))
                StatPill("🔥 Mejor racha", "${record.bestStreak}", Color(0xFFFFD700))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Expand button ────────────────────────────────────────────
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    if (expanded) "▲ Ocultar detalles" else "▼ Ver estadísticas completas",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── Detailed Stats ───────────────────────────────────────────
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // PVP vs PVE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "⚔️ PVP",
                        wins = record.pvpWins,
                        losses = record.pvpLosses,
                        draws = record.pvpDraws
                    )
                    ModeStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "🤖 PVE",
                        wins = record.pveWins,
                        losses = record.pveLosses,
                        draws = record.pveDraws
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── % Victorias vs IA por dificultad ───────────────────
                // FIX: Use String keys ("EASY", "MEDIUM", "HARD") matching new PlayerRecord
                if (record.gamesVsAI.isNotEmpty()) {
                    SectionTitle("Porcentaje de victorias vs IA")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("EASY" to "Fácil", "MEDIUM" to "Medio", "HARD" to "Difícil").forEach { (key, label) ->
                            val gamesPlayed = record.gamesVsAI[key] ?: 0
                            val winsVsDiff = record.winVsAI[key] ?: 0
                            val pct = if (gamesPlayed > 0) (winsVsDiff * 100 / gamesPlayed) else 0
                            AiWinCard(
                                modifier = Modifier.weight(1f),
                                label = label,
                                wins = winsVsDiff,
                                games = gamesPlayed,
                                pct = pct
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Victorias por tamaño de tablero ────────────────────
                if (record.winsByBoardSize.isNotEmpty()) {
                    SectionTitle("Victorias por tamaño de tablero")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        record.winsByBoardSize.entries.sortedBy { it.key }.forEach { (size, wins) ->
                            StatPill("${size}×${size}", "$wins wins", MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Métricas avanzadas ──────────────────────────────────
                SectionTitle("Métricas avanzadas")
                val avgTimeSecs = if (record.totalMoves > 0) record.totalMoveTimeSeconds / record.totalMoves else 0L
                val favMove = record.favoriteMove?.let { "(fila ${it.row}, col ${it.col})" } ?: "—"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill("⏱️ Tiempo/mov", "${avgTimeSecs}s", MaterialTheme.colorScheme.secondary)
                    StatPill("🎯 Movimientos", "${record.totalMoves}", MaterialTheme.colorScheme.secondary)
                    StatPill("❤️ Mov. favorito", favMove, MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ModeStatsCard(modifier: Modifier, title: String, wins: Int, losses: Int, draws: Int) {
    val total = wins + losses + draws
    val pct = if (total > 0) (wins * 100 / total) else 0
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("$wins V · $losses D · $draws E", style = MaterialTheme.typography.bodySmall)
            Text("$pct% victorias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun AiWinCard(modifier: Modifier, label: String, wins: Int, games: Int, pct: Int) {
    val barColor = when {
        pct >= 60 -> Color(0xFF4CAF50)
        pct >= 30 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.error
    }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "$pct%",
                style = MaterialTheme.typography.titleLarge,
                color = barColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$wins / $games",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(4.dp)
                        .background(barColor)
                )
            }
        }
    }
}