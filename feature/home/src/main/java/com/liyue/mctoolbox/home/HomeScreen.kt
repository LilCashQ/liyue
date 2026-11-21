package com.liyue.mctoolbox.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState

private val Bg = Color(0xFF2B2B2B)
private val CardFill = Color(0xFF4A423A)
private val CardEdge = Color(0xFF8A8A8A)
private val AccentGreen = Color(0xFF6FBF3F)
private val TextMain = Color(0xFFF2F2F2)
private val TextSub = Color(0xFFB8AFA4)

/** 像素风工具页：大标题 + 右上搜索，2 列像素卡片。 */
@Composable
fun HomeScreen(
    onOpenTool: (ToolEntry) -> Unit,
    modifier: Modifier = Modifier,
    recentToolIds: String? = null,
    frequentToolIds: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val tools = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) ToolCatalog.all
        else ToolCatalog.all.filter { it.title.contains(q, true) || it.subtitle.contains(q, true) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .background(Bg),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "里悦工具箱",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "群系 · 结构 · 种子",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSub,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PixelSearch(query) { query = it }
            }
        }
        items(tools, key = { it.id }) { tool ->
            ToolTile(tool) { if (tool.available) onOpenTool(tool) }
        }
    }
}

@Composable
private fun PixelSearch(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .width(168.dp)
            .height(38.dp)
            .background(Color(0xFF1F1F1F))
            .border(2.dp, CardEdge)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = TextSub,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = TextMain, fontSize = 13.sp),
            cursorBrush = SolidColor(AccentGreen),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("搜索工具…", color = TextSub, fontSize = 13.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun ToolTile(tool: ToolEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "tile")
    val enabled = tool.available
    val iconRes = PixelIcons.tool(tool.id)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val w = size.width
                val h = size.height
                val e = 2.dp.toPx()
                val c = 7.dp.toPx()
                drawRect(CardEdge, size = size)
                drawRect(if (enabled) CardFill else Color(0xFF3A342E),
                    topLeft = Offset(e, e), size = Size(w - e * 2, h - e * 2))
                // 左上与右下的直角装饰
                drawRect(if (enabled) AccentGreen else Color(0xFF5A5A5A), size = Size(c, c))
                drawRect(if (enabled) AccentGreen else Color(0xFF5A5A5A),
                    topLeft = Offset(w - c, h - c), size = Size(c, c))
                drawRect(Color(0x33FFFFFF), topLeft = Offset(e, e), size = Size(w - e * 2, e))
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    colorFilter = if (enabled) null else ColorFilter.tint(Color(0xFF7A7A7A)),
                )
            } else {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) TextMain else Color(0xFF9A938C),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (enabled) tool.subtitle else "即将上线",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) TextSub else Color(0xFFD9A441),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
