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
    onPrimaryContainer = Blue20,
    secondary = Slate40,
    onSecondary = White,
    secondaryContainer = Slate90,
    onSecondaryContainer = Ink,
    tertiary = Amber30,
    onTertiary = White,
    tertiaryContainer = Amber90,
    onTertiaryContainer = Amber30,
    background = Mist,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = MistCard,
    surfaceContainerHigh = Mist,
    surfaceContainerHighest = Line,
    outline = Line,
    outlineVariant = Hairline,
    error = Color(0xFFD93025),
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
    onSecondaryContainer = NightText,
    tertiary = Amber80,
    onTertiary = Amber30,
    tertiaryContainer = Color(0xFF3A2A10),
    onTertiaryContainer = Amber90,
    background = NightBase,
    onBackground = NightText,
    surface = Night1,
    onSurface = NightText,
    surfaceVariant = Night2,
    onSurfaceVariant = NightTextSoft,
    surfaceContainerLowest = Night1,
    surfaceContainerLow = Night1,
    surfaceContainer = Night2,
    surfaceContainerHigh = Night3,
    surfaceContainerHighest = Night4,
    outline = NightLine,
    outlineVariant = Color(0xFF1C222B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** 一套统一圆角：卡片 20、按钮 14、胶囊 999。 */
val LiYueShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
