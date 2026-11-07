package com.liyue.mctoolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Moss40,
    onPrimary = Color.White,
    primaryContainer = Moss90,
    onPrimaryContainer = Moss10,
    secondary = Clay40,
    onSecondary = Color.White,
    secondaryContainer = Clay90,
    onSecondaryContainer = Color(0xFF3A220B),
    tertiary = Color(0xFF3F5E7A),
    onTertiary = Color.White,
    background = Paper,
    onBackground = Ink10,
    surface = Paper,
    onSurface = Ink10,
    surfaceVariant = Ink90,
    onSurfaceVariant = Color(0xFF4A5347),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFBF5),
    surfaceContainer = PaperDim,
    surfaceContainerHigh = Color(0xFFE9EBE0),
    surfaceContainerHighest = Color(0xFFE2E5D8),
    outline = Color(0xFF77816F),
    outlineVariant = Color(0xFFCBD2C4),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Moss80,
    onPrimary = Moss10,
    primaryContainer = Moss30,
    onPrimaryContainer = Moss90,
    secondary = Clay80,
    onSecondary = Color(0xFF2C1A08),
    secondaryContainer = Color(0xFF5B3A18),
    onSecondaryContainer = Clay90,
    tertiary = Color(0xFFA9C6E4),
    onTertiary = Color(0xFF0B2437),
    background = PaperDark,
    onBackground = Color(0xFFE7EADC),
    surface = PaperDark,
    onSurface = Color(0xFFE7EADC),
    surfaceVariant = Ink20,
    onSurfaceVariant = Color(0xFFB9C2AE),
    surfaceContainerLowest = Color(0xFF0A0D0A),
    surfaceContainerLow = Color(0xFF141813),
    surfaceContainer = Color(0xFF181D17),
    surfaceContainerHigh = Color(0xFF212720),
    surfaceContainerHighest = Color(0xFF2A3129),
    outline = Color(0xFF8A9385),
    outlineVariant = Color(0xFF3B4238),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** One radius scale, so cards, sheets and chips feel like the same app. */
val LiYueShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun LiYueTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LiYueTypography,
        shapes = LiYueShapes,
        content = content,
    )
}
