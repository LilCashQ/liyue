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
    secondaryContainer = Color(0xFF3A2E22),
    onSecondaryContainer = Color(0xFFE8D6BE),
    tertiary = GoldYellow,
    onTertiary = Color(0xFF1A1400),
    tertiaryContainer = Color(0xFF4A3A10),
    onTertiaryContainer = GoldYellow,
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF262626),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF323232),
    onSurfaceVariant = Color(0xFFA9A9A9),
    surfaceContainerLowest = Color(0xFF1A1A1A),
    surfaceContainerLow = Color(0xFF1F1F1F),
    surfaceContainer = Color(0xFF262626),
    surfaceContainerHigh = Color(0xFF303030),
    surfaceContainerHighest = Color(0xFF3A3A3A),
    outline = Color(0xFF5D5D5D),
    outlineVariant = Color(0xFF444444),
    error = RedStone,
    onError = White,
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFC9C9),
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
