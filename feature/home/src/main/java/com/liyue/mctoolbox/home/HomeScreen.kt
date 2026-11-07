package com.liyue.mctoolbox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    recentToolId: String?,
    onOpenTool: (ToolEntry) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val tools = remember(query) {
        val keyword = query.trim()
        // 查找器搬进了种子地图的顶栏，首页不再重复放一张卡
        val homeTools = ToolCatalog.all.filter { it.id != ToolCatalog.STRUCTURE_FINDER }
        if (keyword.isEmpty()) {
            homeTools
        } else {
            homeTools.filter {
                it.title.contains(keyword, ignoreCase = true) ||
                    it.subtitle.contains(keyword, ignoreCase = true)
            }
        }
    }
    val recent = ToolCatalog.byId(recentToolId)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(version = appVersion())
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            HeroRow(onOpenTool = onOpenTool)
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchField(query) { query = it }
            Spacer(Modifier.height(4.dp))
        }

        if (recent != null && query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    icon = Icons.Filled.Refresh,
                    title = "最近使用",
                    trailing = null,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentRow(recent) { onOpenTool(recent) }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader(
                icon = Icons.Filled.Star,
                title = if (query.isBlank()) "全部工具" else "搜索结果",
                trailing = "${tools.size} 个",
            )
        }

        if (tools.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "没有匹配「$query」的工具",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(tools) { tool -> ToolCard(tool) { onOpenTool(tool) } }
        }
    }
}

@Composable
private fun Header(version: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "里悦",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = version,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Minecraft 工具箱 · 群系、结构、种子都在本机算",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索工具，如 种子、基岩、全花") },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, trailing: String?) {
    Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun HeroRow(onOpenTool: (ToolEntry) -> Unit) {
    val seedMap = ToolCatalog.byId(ToolCatalog.SEED_MAP)
    val bedrock = ToolCatalog.byId(ToolCatalog.BEDROCK_LIBRARY)
    val flower = ToolCatalog.byId(ToolCatalog.FLOWER_FARM)
    val pageWidth = (LocalConfiguration.current.screenWidthDp * 0.74f).dp
    val listState = rememberLazyListState()

    Column {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (seedMap != null) {
                item {
                    HeroCard(
                        title = "种子地图",
                        subtitle = "真实群系、结构、地表高度",
                        badge = "官方",
                        imageRes = R.drawable.hero_seed_map,
                        onClick = { onOpenTool(seedMap) },
                        modifier = Modifier.width(pageWidth),
                    )
                }
            }
            if (flower != null) {
                item {
                    HeroCard(
                        title = "全花种农场",
                        subtitle = "花林、沼泽、苍白花园挤进一个窗口",
                        badge = "新",
                        imageRes = R.drawable.hero_finder,
                        onClick = { onOpenTool(flower) },
                        modifier = Modifier.width(pageWidth),
                    )
                }
            }
            if (bedrock != null) {
                item {
                    HeroCard(
                        title = "基岩版版本库",
                        subtitle = "639 个版本，网盘直达",
                        badge = null,
                        imageRes = R.drawable.hero_bedrock,
                        onClick = { onOpenTool(bedrock) },
                        modifier = Modifier.width(pageWidth),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PageDots(total = 3, active = listState.firstVisibleItemIndex)
    }
}

@Composable
private fun PageDots(total: Int, active: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val selected = index == active.coerceIn(0, total - 1)
            Box(
                modifier = Modifier
                    .height(5.dp)
                    .width(if (selected) 20.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    badge: String?,
    imageRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.982f else 1f, label = "heroPress")

    Surface(
        modifier = modifier
            .height(198.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
        interactionSource = interaction,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.14f),
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
            if (badge != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                        color = Color(0xFF0B2C6B),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RecentRow(tool: ToolEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(tool.icon, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "继续 →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, size: Int = 44) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "cardPress")
    val enabled = tool.available

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 152.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        onClick = { if (enabled) onClick() },
        enabled = true,
        interactionSource = interaction,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(tool.icon)
                Spacer(Modifier.weight(1f))
                if (!enabled) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            text = "即将上线",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Version chip text, read from the installed package instead of a constant. */
@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("v?")
    }
}
