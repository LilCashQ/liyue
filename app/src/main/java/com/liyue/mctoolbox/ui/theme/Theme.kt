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
    primary = GrassGreen,
    onPrimary = White,
    primaryContainer = GrassGreenDark,
    onPrimaryContainer = White,
    secondary = DirtBrown,
    onSecondary = White,
    secondaryContainer = PanelDark,
    onSecondaryContainer = White,
    tertiary = GoldYellow,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF7A5A10),
    onTertiaryContainer = Color(0xFFFFF0C2),
    background = Color(0xFF2E2B27),
    onBackground = White,
    surface = PanelLight,
    onSurface = Ink,
    surfaceVariant = PanelMid,
    onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = PanelMid,
    surfaceContainerLow = PanelMid,
    surfaceContainer = PanelLight,
    surfaceContainerHigh = PanelDark,
    surfaceContainerHighest = Color(0xFF7A7A7A),
    outline = BevelDark,
    outlineVariant = Color(0xFF8B8B8B),
    error = RedStone,
    onError = White,
    errorContainer = Color(0xFF6E1F1F),
    onErrorContainer = Color(0xFFFFD9D9),
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
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
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
