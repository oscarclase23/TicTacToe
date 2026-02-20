package org.dam.project.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Color palette
val PrimaryGold = Color(0xFFD4AF37)
val SecondaryBronze = Color(0xFFCD7F32)
val AccentStone = Color(0xFF8B8680)
val DarkAccent = Color(0xFF3E3E3E)
val PrimaryBlack = Color(0xFF1A1A1A)
val ErrorRed = Color(0xFFB71C1C)
val SuccessBlue = Color(0xFF1565C0)

// Dark theme (preferred)
val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    secondary = SecondaryBronze,
    tertiary = AccentStone,
    background = PrimaryBlack,
    surface = DarkAccent,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

// Light theme (optional)
val LightColorScheme = lightColorScheme(
    primary = SecondaryBronze,
    secondary = PrimaryGold,
    tertiary = AccentStone,
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
)
