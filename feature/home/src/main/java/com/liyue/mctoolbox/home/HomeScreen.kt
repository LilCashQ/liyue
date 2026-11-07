package com.liyue.mctoolbox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(version = appVersion())
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchField(query) { query = it }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            HeroRow(onOpenTool = onOpenTool)
        }

        if (recent != null && query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionTitle("最近使用")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                RecentRow(recent) { onOpenTool(recent) }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle(
                if (query.isBlank()) "全部工具" else "搜索结果",
                trailing = "${tools.size}",
            )
        }

        if (tools.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "没有匹配「$query」的工具",
                    modifier = Modifier.padding(vertical = 28.dp),
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
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "里悦",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = version,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "群系、结构、种子，全在本机算",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        placeholder = {
            Text(
                "搜索工具",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroRow(onOpenTool: (ToolEntry) -> Unit) {
    val seedMap = ToolCatalog.byId(ToolCatalog.SEED_MAP)
    val finder = ToolCatalog.byId(ToolCatalog.STRUCTURE_FINDER)
    val flower = ToolCatalog.byId(ToolCatalog.FLOWER_FARM)
    val bedrock = ToolCatalog.byId(ToolCatalog.BEDROCK_LIBRARY)
    val pageWidth = (LocalConfiguration.current.screenWidthDp * 0.78f).dp

    LazyRow(
        contentPadding = PaddingValues(top = 12.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (seedMap != null) {
            item {
                HeroCard(
                    title = "种子地图",
                    subtitle = "真实群系、结构、地表高度",
                    imageRes = R.drawable.hero_seed_map,
                    onClick = { onOpenTool(seedMap) },
                    modifier = Modifier.width(pageWidth),
                )
            }
        }
        if (finder != null) {
            item {
                HeroCard(
                    title = "查找器",
                    subtitle = "36 个 Chunkbase 玩法一次装齐",
                    imageRes = R.drawable.hero_finder,
                    onClick = { onOpenTool(finder) },
                    modifier = Modifier.width(pageWidth),
                )
            }
        }
        if (flower != null) {
            item {
                HeroCard(
                    title = "全花种农场",
                    subtitle = "花林、沼泽、苍白花园同窗",
                    imageRes = R.drawable.hero_flower,
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
                    imageRes = R.drawable.hero_bedrock,
                    onClick = { onOpenTool(bedrock) },
                    modifier = Modifier.width(pageWidth),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "heroPress")

    Surface(
        modifier = modifier
            .height(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
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
                            0.45f to Color.Black.copy(alpha = 0.10f),
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RecentRow(tool: ToolEntry, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    tool.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "继续",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ToolCard(tool: ToolEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.972f else 1f, label = "cardPress")
    val enabled = tool.available

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = { if (enabled) onClick() },
        interactionSource = interaction,
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Icon(
                imageVector = tool.icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (enabled) tool.subtitle else "即将上线",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
