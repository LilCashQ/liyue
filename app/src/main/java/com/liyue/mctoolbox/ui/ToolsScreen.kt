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

/** 每组一个淡色底，与参考应用「推荐/玩机/解析」的处理一致。 */
private val GROUP_TINTS: IntArray = intArrayOf(
    0xFFE4EAF9.toInt(), // 淡蓝
    0xFFF3E8F6.toInt(), // 淡紫
    0xFFE9F3EA.toInt(), // 淡绿
    0xFFF9EDE4.toInt(), // 淡橙
    0xFFE4F0F5.toInt(), // 淡青
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
            shape = RoundedCornerShape(12.dp),
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
    val scale by animateFloatAsState(if (pressed) 0.972f else 1f, label = "catCardPress")
    val enabled = tool.available

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(22.dp),
        color = androidx.compose.ui.graphics.Color(tint).copy(alpha = 0.55f),
        onClick = onClick,
        interactionSource = interaction,
        enabled = enabled,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = if (enabled) tool.subtitle else "即将上线",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
