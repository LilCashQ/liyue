package com.liyue.mctoolbox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val BevelLight = Color(0xFFFFFFFF)
internal val BevelDark = Color(0xFF555555)
internal val SlotShadow = Color(0xFF373737)

/** 《我的世界》GUI 面板：灰底 + 左上高光 + 右下阴影 + 外描边。 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable () -> Unit,
) {
    val fill = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .drawBehind {
                val b = 2.dp.toPx()
                drawRect(BevelDark, size = size)
                drawRect(fill, topLeft = Offset(b, b), size = Size(size.width - b * 2, size.height - b * 2))
                drawRect(BevelLight.copy(alpha = 0.9f), topLeft = Offset(b, b), size = Size(size.width - b * 2, b))
                drawRect(BevelLight.copy(alpha = 0.9f), topLeft = Offset(b, b), size = Size(b, size.height - b * 2))
                drawRect(BevelDark.copy(alpha = 0.8f), topLeft = Offset(b, size.height - b * 2), size = Size(size.width - b * 2, b))
                drawRect(BevelDark.copy(alpha = 0.8f), topLeft = Offset(size.width - b * 2, b), size = Size(b, size.height - b * 2))
            }
            .padding(contentPadding),
    ) {
        content()
    }
}

/** 像素按钮：三段式凸起，主按钮用草方块绿。 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val face = when {
        !enabled -> Color(0xFF8B8B8B)
        primary -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = if (primary || !enabled) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .background(BevelDark)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .drawBehind {
                val b = 2.dp.toPx()
                drawRect(face, topLeft = Offset(b, b), size = Size(size.width - b * 2, size.height - b * 2))
                drawRect(BevelLight.copy(alpha = 0.55f), topLeft = Offset(b, b), size = Size(size.width - b * 2, b))
                drawRect(BevelLight.copy(alpha = 0.55f), topLeft = Offset(b, b), size = Size(b, size.height - b * 2))
                drawRect(SlotShadow.copy(alpha = 0.35f), topLeft = Offset(b, size.height - b * 2), size = Size(size.width - b * 2, b))
                drawRect(SlotShadow.copy(alpha = 0.35f), topLeft = Offset(size.width - b * 2, b), size = Size(b, size.height - b * 2))
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = textColor)
    }
}

/** 凹槽：坐标、结果这类放进格子里的内容。 */
@Composable
fun PixelSlot(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(10.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .border(2.dp, BevelDark)
            .background(SlotShadow)
            .padding(contentPadding),
    ) {
        content()
    }
}
