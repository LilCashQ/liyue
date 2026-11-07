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
    primary = Blue40,
    onPrimary = White,
    primaryContainer = Blue95,
    onPrimaryContainer = Blue15,
    secondary = Slate40,
    onSecondary = White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Color(0xFF1B2330),
    tertiary = Amber30,
    onTertiary = White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Color(0xFF2A1800),
    background = White,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Mist100,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = White,
    surfaceContainerLow = Mist50,
    surfaceContainer = Mist100,
    surfaceContainerHigh = Mist200,
    surfaceContainerHighest = Mist300,
    outline = Color(0xFFC9D3E1),
    outlineVariant = Color(0xFFE3E9F2),
    error = Color(0xFFC62828),
    onError = White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF5C0F0B),
)

private val DarkColors = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue15,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = Slate80,
    onSecondary = Slate40,
    secondaryContainer = Night3,
    onSecondaryContainer = Slate90,
    tertiary = Amber80,
    onTertiary = Color(0xFF3A2200),
    tertiaryContainer = Color(0xFF5C3A05),
    onTertiaryContainer = Amber90,
    background = NightBase,
    onBackground = NightText,
    surface = NightBase,
    onSurface = NightText,
    surfaceVariant = Night2,
    onSurfaceVariant = NightTextSoft,
    surfaceContainerLowest = Color(0xFF070A10),
    surfaceContainerLow = Night1,
    surfaceContainer = Night2,
    surfaceContainerHigh = Night3,
    surfaceContainerHighest = Night4,
    outline = NightOutline,
    outlineVariant = Color(0xFF232D3B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
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
