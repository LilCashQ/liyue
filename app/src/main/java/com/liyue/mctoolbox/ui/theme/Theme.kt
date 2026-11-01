package com.liyue.mctoolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Cyan40,
    onSecondary = Color.White,
    secondaryContainer = Cyan90,
    onSecondaryContainer = Color(0xFF00344A),
    background = SurfaceLight,
    onBackground = Slate10,
    surface = Color.White,
    onSurface = Slate10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Color(0xFF44546A),
    outline = Color(0xFF8A9BB4),
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Cyan80,
    onSecondary = Color(0xFF00344A),
    secondaryContainer = Color(0xFF00506F),
    onSecondaryContainer = Cyan90,
    background = SurfaceDark,
    onBackground = Color(0xFFE8EEF8),
    surface = Color(0xFF121A28),
    onSurface = Color(0xFFE8EEF8),
    surfaceVariant = Slate20,
    onSurfaceVariant = Color(0xFFB6C4D8),
    outline = Color(0xFF6F8099),
)

@Composable
fun LiYueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LiYueTypography,
        content = content,
    )
}
