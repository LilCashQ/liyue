package com.liyue.mctoolbox.map.finder

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liyue.mctoolbox.map.GameVersion
import com.liyue.mctoolbox.map.GameVersions
import com.liyue.mctoolbox.map.LiYueField
import com.liyue.mctoolbox.map.PillChip
import com.liyue.mctoolbox.map.groupLabel
import com.liyue.mctoolbox.map.MapTarget
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.map.R
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(
    onBack: () -> Unit,
    onOpenMap: (MapTarget) -> Unit = {},
    preset: ChunkbaseApp? = null,
    viewModel: FinderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.mapRequests.collect { target -> onOpenMap(target) }
    }

    // Opened from the seed map: adopt its seed and version instead of retyping.
    LaunchedEffect(Unit) {
        val (seed, versionLabel) = com.liyue.mctoolbox.map.FinderLaunch.take()
        seed?.let(viewModel::onSeedTextChange)
        versionLabel?.let { label ->
            com.liyue.mctoolbox.map.GameVersions.find(label)?.let(viewModel::onVersionChange)
        }
    }

    LaunchedEffect(preset?.id) {
        preset?.let(viewModel::applyPreset)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(preset?.title ?: "查找器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FinderBanner(
                    title = preset?.title ?: "Chunkbase 全都能搜",
                    subtitle = "结构、群系、矿脉、地牢 · 本地运算",
                )
            }

            item {
                FinderSeedCard(
                    state = state,
                    onSeedChange = viewModel::onSeedTextChange,
                    onVersionChange = viewModel::onVersionChange,
                    onTabChange = viewModel::onTabChange,
                )
            }

            item {
                FinderQueryCard(
                    state = state,
                    onStructureChange = viewModel::onStructureChange,
                    onVeinKindChange = viewModel::onVeinKindChange,
                    onBiomeDimensionChange = viewModel::onBiomeDimensionChange,
                    onBiomeChange = viewModel::onBiomeChange,
                    onCenterXChange = viewModel::onCenterXChange,
                    onCenterZChange = viewModel::onCenterZChange,
                    onRadiusChange = viewModel::onRadiusChange,
                    onLimitChange = viewModel::onLimitChange,
                    onSeedQueueChange = viewModel::onSeedQueueChange,
                    onMinCountChange = viewModel::onMinCountChange,
                    onUseSpawn = viewModel::useSpawnAsCenter,
                    onSearch = viewModel::search,
                )
            }

            state.error?.let { message ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("搜索中…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.searched && !state.loading && state.hits.isEmpty() && state.error == null) {
                item {
                    Text(
                        text = "搜索范围内没有找到结果，试着加大半径或换个中心点。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.hits, key = { "${it.title}-${it.x}-${it.z}" }) { hit ->
                FinderHitCard(hit = hit, onOpenMap = { viewModel.openInMap(hit) })
            }

            if (state.searched && state.hits.isNotEmpty()) {
                item {
                    Text(
                        text = "${state.hits.size} 条结果 · ${state.elapsedMs} ms · 由 cubiomes ${state.version.engineLabel} 计算",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinderSeedCard(
    state: FinderUiState,
    onSeedChange: (String) -> Unit,
    onVersionChange: (GameVersion) -> Unit,
    onTabChange: (FinderTab) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = state.seedText,
                    onValueChange = onSeedChange,
                    placeholder = { Text("种子", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                PickerField(
                    label = "版本",
                    selected = state.version.label,
                    options = GameVersions.all,
                    optionLabel = { it.label },
                    onSelect = onVersionChange,
                    modifier = Modifier.width(132.dp),
                    groupOf = { it.edition.groupLabel },
                )
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FinderTab.entries) { tab ->
                    PillChip(
                        label = tab.label,
                        selected = state.tab == tab,
                        onClick = { onTabChange(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FinderQueryCard(
    state: FinderUiState,
    onStructureChange: (StructureType) -> Unit,
    onVeinKindChange: (com.liyue.mctoolbox.seedgen.OreKind) -> Unit,
    onBiomeDimensionChange: (Dimension) -> Unit,
    onBiomeChange: (BiomeOption) -> Unit,
    onCenterXChange: (String) -> Unit,
    onCenterZChange: (String) -> Unit,
    onRadiusChange: (String) -> Unit,
    onLimitChange: (String) -> Unit,
    onSeedQueueChange: (String) -> Unit,
    onMinCountChange: (String) -> Unit,
    onUseSpawn: () -> Unit,
    onSearch: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (state.tab) {
                FinderTab.STRUCTURE -> {
                    val supported = StructureSupport.forVersion(state.version.engine)
                    PickerField(
                        label = "结构类型",
                        selected = state.structure.label,
                        options = supported,
                        optionLabel = { it.label },
                        onSelect = onStructureChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "维度：${state.structure.dimension.label} · 结构间距 ${FinderEngine.regionSizeChunks(state.structure)} 区块",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "搜索半径最大 ${FinderEngine.regionCapBlocks(state.structure)} 方块",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.BIOME -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Dimension.OVERWORLD, Dimension.NETHER, Dimension.END).forEach { dimension ->
                            PillChip(
                                label = dimension.label,
                                selected = state.biomeDimension == dimension,
                                onClick = { onBiomeDimensionChange(dimension) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val candidates = remember(state.biomeOptions, state.biomeDimension) {
                        BiomeCatalog.forDimension(state.biomeOptions, state.biomeDimension)
                    }
                    PickerField(
                        label = "群系",
                        selected = state.biome?.label ?: "请选择",
                        options = candidates,
                        optionLabel = { "${it.label}  (${it.name})" },
                        onSelect = onBiomeChange,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "粗采样 16 方块一格，命中后细化到 4 方块精度。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.STRONGHOLD -> {
                    Text(
                        text = "列出该种子前 ${state.limit} 座要塞，按到中心点的距离排序。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                FinderTab.SLIME -> {
                    Text(
                        text = "搜索中心点周围的史莱姆区块（算法与版本无关，y<40 才会刷史莱姆）。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                FinderTab.ORE_VEIN -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            com.liyue.mctoolbox.seedgen.OreKind.IRON_VEIN,
                            com.liyue.mctoolbox.seedgen.OreKind.COPPER_VEIN,
                        ).forEach { kind ->
                            PillChip(
                                label = kind.label,
                                selected = state.veinKind == kind,
                                onClick = { onVeinKindChange(kind) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "1.18 起的大型矿脉，按区块连通聚成一处，需要 1.18 及以上版本。" +
                            "搜索半径上限 768 方块。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.LAVA_POOL -> {
                    Text(
                        text = "按洞穴雕刻结果找地下的岩浆湖：1.18 起扫描 Y=-54 以下被岩浆填满的空腔，" +
                            "更早的版本扫 Y=10。半径每翻一倍时间翻四倍，512 大概 2 秒。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.DUNGEON -> {
                    Text(
                        text = "地牢是区块装饰阶段的刷怪笼房间，位置由装饰随机数逐次算出并用地形做" +
                            "实心/空气校验，所以给出的是精确坐标。每个区块 14 次尝试" +
                            "（地表下 10 次 Y 0-319，深层 4 次 Y -58 到 -1）。" +
                            "半径越大扫的区块越多，1024 大约 10 秒。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.QUAD_HUT -> {
                    Text(
                        text = "四女巫小屋（quad-hut）：四座沼泽小屋挤在同一个 128 方块球里，" +
                            "中间就是女巫农场的最佳挂机点。先按半径把沼泽小屋全找出来，" +
                            "再配对找近似正方形的四角。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.SEED_QUEUE -> {
                    val supported = StructureSupport.forVersion(state.version.engine)
                    PickerField(
                        label = "结构类型",
                        selected = state.structure.label,
                        options = supported,
                        optionLabel = { it.label },
                        onSelect = onStructureChange,
                    )
                    Spacer(Modifier.height(8.dp))
                    LiYueField(
                        value = state.seedQueueText,
                        onValueChange = onSeedQueueChange,
                        label = { Text("种子列表（一行一颗，或用逗号分隔）") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    LiYueField(
                        value = state.minCountText,
                        onValueChange = onMinCountChange,
                        label = { Text("至少命中几个") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "逐颗种子在半径内数结构，够数的才留下；点结果直接用那颗种子打开地图。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.GOLDEN_APPLE -> {
                    val supported = StructureSupport.forVersion(state.version.engine)
                    PickerField(
                        label = "结构类型",
                        selected = state.structure.label,
                        options = supported,
                        optionLabel = { it.label },
                        onSelect = onStructureChange,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "逐个结构滚算宝箱战利品，只列出真的含金苹果的箱子（Java 版）。" +
                            "沙漠神殿很稀有，种子 0 在 2048 内一个都没有，建议半径写 4096。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                FinderTab.SPAWN -> {
                    Text(
                        text = "按当前种子计算世界出生点。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (state.tab != FinderTab.SPAWN) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiYueField(
                        value = state.centerXText,
                        onValueChange = onCenterXChange,
                        label = { Text("中心 X") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    LiYueField(
                        value = state.centerZText,
                        onValueChange = onCenterZChange,
                        label = { Text("中心 Z") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiYueField(
                        value = state.radiusText,
                        onValueChange = onRadiusChange,
                        label = { Text("半径（方块）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    LiYueField(
                        value = state.limitText,
                        onValueChange = onLimitChange,
                        label = { Text("数量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.tab == FinderTab.STRUCTURE || state.tab == FinderTab.BIOME) {
                    TextButton(onClick = onUseSpawn) { Text("用出生点当中心") }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSearch,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("开始搜索")
            }
        }
    }
}

@Composable
private fun FinderHitCard(hit: FinderHit, onOpenMap: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "X ${hit.x}  Z ${hit.z}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = buildString {
                        append("距离 ${hit.distance} 方块")
                        append(" · ${hit.dimension.label}")
                        hit.detail?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenMap) {
                Icon(Icons.Filled.Place, contentDescription = "在地图打开")
            }
        }
    }
}

/** Compact dropdown used for versions, structures and biomes. */
@Composable
private fun <T> PickerField(
    label: String,
    selected: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    groupOf: ((T) -> String?)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        androidx.compose.material3.OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text("▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            var lastGroup: String? = null
            options.forEach { option ->
                val group = groupOf?.invoke(option)
                if (group != null && group != lastGroup) {
                    lastGroup = group
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = group,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                }
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FinderBanner(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.finder_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.52f),
                        0.75f to Color.Black.copy(alpha = 0.05f),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
