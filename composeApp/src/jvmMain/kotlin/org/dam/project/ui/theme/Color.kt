package org.dam.project.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Medieval-themed color palette
val MedievalGold = Color(0xFFD4AF37)
val MedievalBronze = Color(0xFFCD7F32)
val MedievalStone = Color(0xFF8B8680)
val MedievalDarkStone = Color(0xFF3E3E3E)
val MedievalBlack = Color(0xFF1A1A1A)
val MedievalRed = Color(0xFFB71C1C)
val MedievalBlue = Color(0xFF1565C0)

// Dark theme (preferred)
val DarkColorScheme = darkColorScheme(
    primary = MedievalGold,
    secondary = MedievalBronze,
    tertiary = MedievalStone,
    background = MedievalBlack,
    surface = MedievalDarkStone,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Light theme (optional)
val LightColorScheme = lightColorScheme(
    primary = MedievalBronze,
    secondary = MedievalGold,
    tertiary = MedievalStone,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)
