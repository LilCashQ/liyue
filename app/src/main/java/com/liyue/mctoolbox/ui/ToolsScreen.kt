package com.liyue.mctoolbox.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.home.ToolCatalog
import com.liyue.mctoolbox.home.ToolCategory
import com.liyue.mctoolbox.home.ToolEntry
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** 每组一个淡色底，与参考应用「推荐/玩机/解析」的处理一致。 */
/** 每组一个强调色：深底 + 彩色像素描边。 */
private val GROUP_TINTS: IntArray = intArrayOf(
    0xFF4A90D9.toInt(), // 蓝
    0xFFB4553F.toInt(), // 红棕
    0xFF5FA83C.toInt(), // 草绿
    0xFFD9A441.toInt(), // 金
    0xFF8B6FBF.toInt(), // 紫
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val groups = ToolCatalog.grouped

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工具", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.forEach { (category, tools) ->
                val groupIndex: Int = groups.indexOfFirst { it.first == category }
                item(key = "header_${category.name}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                    CategoryHeader(
                        category = category,
                        tint = GROUP_TINTS[groupIndex % GROUP_TINTS.size],
                        modifier = Modifier.padding(top = if (groupIndex == 0) 8.dp else 14.dp),
                    )
                }
                items(tools, key = { it.id }) { tool ->
                    CategoryCard(
                        tool = tool,
                        tint = GROUP_TINTS[groupIndex % GROUP_TINTS.size],
                        onClick = { if (tool.available) onOpenTool(tool.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: ToolCategory, tint: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(0.dp),
            color = androidx.compose.ui.graphics.Color(tint),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryCard(
    tool: ToolEntry,
    tint: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "catCardPress")
    val enabled = tool.available
    val accent = androidx.compose.ui.graphics.Color(tint)
    val iconRes = com.liyue.mctoolbox.home.PixelIcons.tool(tool.id)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pixelCard(accent, enabled)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) androidx.compose.ui.graphics.Color(0xFFF2F2F2)
                else androidx.compose.ui.graphics.Color(0xFF8A8A8A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            if (enabled) {
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color(0xFF9A9A9A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                ComingSoonTag()
            }
        }
    }
}

/** 「即将上线」像素胶囊：2px 描边 + 时钟方块。 */
@Composable
private fun ComingSoonTag() {
    Row(
        modifier = Modifier
            .border(2.dp, androidx.compose.ui.graphics.Color(0xFFD9A441))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "即将上线",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color(0xFFD9A441),
        )
    }
}

/** 像素卡：深底、2px 描边、四角各一块强调色像素点。 */
private fun Modifier.pixelCard(accent: androidx.compose.ui.graphics.Color, enabled: Boolean): Modifier =
    this
        .background(if (enabled) androidx.compose.ui.graphics.Color(0xFF161B22) else androidx.compose.ui.graphics.Color(0xFF121212))
        .drawBehind {
            val w = size.width
            val h = size.height
            val b = 2.dp.toPx()
            val c = 5.dp.toPx()
            val line = if (enabled) accent.copy(alpha = 0.75f) else androidx.compose.ui.graphics.Color(0xFF3A3A3A)
            drawRect(line, size = androidx.compose.ui.geometry.Size(w, b))
            drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(0f, h - b), size = androidx.compose.ui.geometry.Size(w, b))
            drawRect(line, size = androidx.compose.ui.geometry.Size(b, h))
            drawRect(line, topLeft = androidx.compose.ui.geometry.Offset(w - b, 0f), size = androidx.compose.ui.geometry.Size(b, h))
            if (enabled) {
                listOf(
                    androidx.compose.ui.geometry.Offset(0f, 0f) to androidx.compose.ui.geometry.Size(c, c),
                    androidx.compose.ui.geometry.Offset(w - c, 0f) to androidx.compose.ui.geometry.Size(c, c),
                    androidx.compose.ui.geometry.Offset(0f, h - c) to androidx.compose.ui.geometry.Size(c, c),
                    androidx.compose.ui.geometry.Offset(w - c, h - c) to androidx.compose.ui.geometry.Size(c, c),
                ).forEach { (offset, sz) -> drawRect(accent, topLeft = offset, size = sz) }
            }
        }
