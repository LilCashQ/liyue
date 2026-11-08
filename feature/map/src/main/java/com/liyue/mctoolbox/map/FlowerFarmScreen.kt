package com.liyue.mctoolbox.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.liyue.mctoolbox.map.R

private data class FlowerFarmState(
    val seedText: String = "0",
    val version: GameVersion = GameVersions.default,
    val minX: String = "-16384",
    val maxX: String = "16384",
    val minZ: String = "-16384",
    val maxZ: String = "16384",
    val longSide: String = "640",
    val shortSide: String = "320",
    val useShortSide: Boolean = true,
    val moduleGap: String = "10",
    val squareMode: Boolean = false,
    val squareSide: String = "1000001",
    val fastMode: Boolean = true,
    val threads: String = Runtime.getRuntime().availableProcessors()
        .coerceIn(1, 8).toString(),
    val orientation: Int = 0,
    val flowerMargin: String = "0",
    val requireBirch: Boolean = true,
    val requireCherry: Boolean = true,
    val requireBasalt: Boolean = false,
    val requirePale: Boolean = true,
    val running: Boolean = false,
    val paused: Boolean = false,
    val progress: Int = 0,
    val batches: Int = 0,
    val elapsedMs: Long = 0,
    val hits: List<SeedEngine.FlowerFarmHit>? = null,
    val error: String? = null,
)

private fun String.toIntOr(fallback: Int): Int = trim().toIntOrNull() ?: fallback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowerFarmScreen(
    onBack: () -> Unit,
    onOpenMap: (MapTarget) -> Unit,
) {
    val engine = remember { SeedEngine() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(FlowerFarmState()) }
    var versionMenu by remember { mutableStateOf(false) }
    var orientationMenu by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pausedFlag by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var history by remember { mutableStateOf<List<FlowerHistoryEntry>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    val historySheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        history = withContext(Dispatchers.IO) { FlowerHistoryStore.load(context) }
    }

    fun bounds(snapshot: FlowerFarmState): IntArray {
        if (snapshot.squareMode) {
            val half = (snapshot.squareSide.toIntOr(0) / 2).coerceAtLeast(256)
            return intArrayOf(-half, -half, half, half)
        }
        return intArrayOf(
            snapshot.minX.toIntOr(-16384),
            snapshot.minZ.toIntOr(-16384),
            snapshot.maxX.toIntOr(16384),
            snapshot.maxZ.toIntOr(16384),
        )
    }

    fun run(override: FlowerFarmState? = null) {
        if (state.running) return
        val snapshot = override ?: state
        val rect = bounds(snapshot)
        val longSide = snapshot.longSide.toIntOr(640).coerceIn(64, 1024)
        val shortSide = if (snapshot.useShortSide) {
            snapshot.shortSide.toIntOr(320).coerceIn(64, 1024)
        } else {
            longSide
        }
        val threads = snapshot.threads.toIntOr(4)
            .coerceIn(1, Runtime.getRuntime().availableProcessors().coerceIn(1, 8))
        val seed = SeedEngine.parseJavaSeed(snapshot.seedText)
        val requirePale = snapshot.requirePale &&
            snapshot.version.engine.ordinal >= McVersion.MC_1_21_4.ordinal
        val margin = snapshot.flowerMargin.toIntOr(0).coerceIn(0, 512)
        val fastMode = snapshot.fastMode
        val orientation = snapshot.orientation
        val birch = snapshot.requireBirch
        val cherry = snapshot.requireCherry
        val basalt = snapshot.requireBasalt
        val version = snapshot.version.engine

        pausedFlag = false
        cancelled = false

        // One native call has to build a climate field over its whole rectangle,
        // so a big search area is split into square tiles that are then run side
        // by side with the requested thread count.
        val sideMin = if (longSide < shortSide) longSide else shortSide
        val maxStep = (sideMin / 2).coerceAtLeast(16)
        val tileSide = (maxStep * 1000).coerceAtMost(65536).coerceAtLeast(4096)
        val tilesX = ((rect[2] - rect[0]) + tileSide - 1) / tileSide
        val tilesZ = ((rect[3] - rect[1]) + tileSide - 1) / tileSide
        val tiles = ArrayList<IntArray>(tilesX * tilesZ)
        for (tz in 0 until tilesZ) {
            for (tx in 0 until tilesX) {
                val x0 = rect[0] + tx * tileSide
                val z0 = rect[1] + tz * tileSide
                tiles.add(
                    intArrayOf(
                        x0, z0,
                        (x0 + tileSide).coerceAtMost(rect[2]),
                        (z0 + tileSide).coerceAtMost(rect[3]),
                    ),
                )
            }
        }

        state = snapshot.copy(
            running = true, paused = false, hits = null, error = null,
            progress = 0, batches = tiles.size,
        )
        job = scope.launch {
            val started = System.nanoTime()
            val collected = mutableListOf<SeedEngine.FlowerFarmHit>()
            var failure: String? = null
            var done = 0
            try {
                coroutineScope {
                    val gate = kotlinx.coroutines.sync.Semaphore(threads)
                    tiles.forEach { tile ->
                        launch(Dispatchers.Default) {
                            gate.acquire()
                            try {
                                while (pausedFlag && !cancelled) delay(120)
                                if (cancelled) return@launch
                                val found = runCatching {
                                    engine.flowerSearchBlocking(
                                        seed = seed,
                                        version = version,
                                        minX = tile[0],
                                        minZ = tile[1],
                                        maxX = tile[2],
                                        maxZ = tile[3],
                                        longSide = longSide,
                                        shortSide = shortSide,
                                        squareOnly = false,
                                        requirePale = requirePale,
                                        requireBirch = birch,
                                        requireCherry = cherry,
                                        requireBasalt = basalt,
                                        orientation = orientation,
                                        flowerMargin = margin,
                                        fastMode = fastMode,
                                        maxRows = 8,
                                    )
                                }
                                found.onFailure { if (failure == null) failure = it.message ?: "搜索失败" }
                                synchronized(collected) {
                                    found.getOrNull()?.let { collected.addAll(it) }
                                    done += 1
                                    state = state.copy(progress = done)
                                }
                            } finally {
                                gate.release()
                            }
                        }
                    }
                }
            } finally {
                val millis = (System.nanoTime() - started) / 1_000_000
                // Windows found in neighbouring tiles can overlap: keep the ones
                // that rank best and drop duplicates.
                val accepted = mutableListOf<SeedEngine.FlowerFarmHit>()
                collected
                    .sortedWith(
                        compareByDescending<SeedEngine.FlowerFarmHit> { it.bonus.isNotBlank() }
                            .thenByDescending { it.nether == "玄武岩三角洲" }
                            .thenBy { kotlin.math.abs(it.flowerX) + kotlin.math.abs(it.flowerZ) },
                    )
                    .forEach { hit ->
                        val clash = accepted.any {
                            kotlin.math.abs(it.flowerX - hit.flowerX) < (hit.sideX + hit.sideZ) / 4 &&
                                kotlin.math.abs(it.flowerZ - hit.flowerZ) < (hit.sideX + hit.sideZ) / 4
                        }
                        if (!clash && accepted.size < 40) accepted.add(hit)
                    }
                state = state.copy(
                    running = false,
                    paused = false,
                    elapsedMs = millis,
                    hits = accepted,
                    error = if (cancelled) null else failure,
                )
                if (!cancelled) {
                    val entry = FlowerHistoryEntry(
                        id = System.currentTimeMillis(),
                        seedText = snapshot.seedText,
                        versionLabel = snapshot.version.label,
                        minX = rect[0],
                        minZ = rect[1],
                        maxX = rect[2],
                        maxZ = rect[3],
                        longSide = longSide,
                        shortSide = shortSide,
                        orientation = orientation,
                        flowerMargin = margin,
                        fastMode = fastMode,
                        requirePale = requirePale,
                        requireBirch = birch,
                        requireCherry = cherry,
                        requireBasalt = basalt,
                        hits = accepted.size,
                        elapsedMs = millis,
                        createdAt = System.currentTimeMillis(),
                    )
                    history = withContext(Dispatchers.IO) { FlowerHistoryStore.add(context, entry) }
                }
            }
        }
    }

    fun stop() {
        cancelled = true
        pausedFlag = false
        job?.cancel()
        state = state.copy(running = false, paused = false)
    }

    val paleApplies = state.version.engine.ordinal >= McVersion.MC_1_21_4.ordinal

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全花种农场") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "搜索历史")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.Bottom) {
                    LiYueField(
                        value = state.seedText,
                        onValueChange = { state = state.copy(seedText = it) },
                        label = { Text("种子") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box {
                        PillChip(
                            label = state.version.label,
                            selected = false,
                            onClick = { versionMenu = true },
                        )
                        DropdownMenu(
                            expanded = versionMenu,
                            onDismissRequest = { versionMenu = false },
                            modifier = Modifier.heightIn(max = 420.dp),
                        ) {
                            GameVersions.grouped.forEach { (group, versions) ->
                                DropdownMenuItem(
                                    text = { Text(group, style = MaterialTheme.typography.labelMedium) },
                                    enabled = false,
                                    onClick = {},
                                )
                                versions.forEach { version ->
                                    DropdownMenuItem(
                                        text = { Text(version.label) },
                                        onClick = {
                                            state = state.copy(version = version)
                                            versionMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                FormSection(
                    title = "搜索范围",
                    hint = "主世界方块坐标。±16384 一般几秒，±500000 要几分钟。",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiYueField(
                            value = state.minX,
                            onValueChange = { state = state.copy(minX = it) },
                            label = { Text("X 最小") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                        LiYueField(
                            value = state.maxX,
                            onValueChange = { state = state.copy(maxX = it) },
                            label = { Text("X 最大") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiYueField(
                            value = state.minZ,
                            onValueChange = { state = state.copy(minZ = it) },
                            label = { Text("Z 最小") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                        LiYueField(
                            value = state.maxZ,
                            onValueChange = { state = state.copy(maxZ = it) },
                            label = { Text("Z 最大") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        InlineLink("重置默认") {
                            state = state.copy(
                                minX = "-16384", maxX = "16384",
                                minZ = "-16384", maxZ = "16384",
                            )
                        }
                        InlineLink("世界边界") {
                            state = state.copy(
                                minX = "-500000", maxX = "500000",
                                minZ = "-500000", maxZ = "500000",
                            )
                        }
                    }
                }
            }

            item {
                FormSection(
                    title = "窗口尺寸",
                    hint = "窗口里要同时有繁花森林、沼泽" +
                        (if (paleApplies) "和苍白花园" else "") +
                        "，繁花森林里还要有完整的树花梯度。",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiYueField(
                            value = state.longSide,
                            onValueChange = { state = state.copy(longSide = it) },
                            label = { Text("长边") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                        LiYueField(
                            value = state.shortSide,
                            onValueChange = { state = state.copy(shortSide = it) },
                            label = { Text("短边") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                        LiYueField(
                            value = state.moduleGap,
                            onValueChange = { state = state.copy(moduleGap = it) },
                            label = { Text("模距") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    InlineLink("按模距定长边（32 × 模距 + 16）") {
                        val gap = state.moduleGap.toIntOr(10).coerceIn(1, 31)
                        val side = (32 * gap + 16).coerceAtMost(1024)
                        state = state.copy(longSide = side.toString(), useShortSide = false)
                    }
                    Spacer(Modifier.height(4.dp))
                    FormSwitch(
                        label = "启用短边",
                        caption = "关掉就是正方形窗口",
                        checked = state.useShortSide,
                        onChange = { state = state.copy(useShortSide = it) },
                    )
                    FormSwitch(
                        label = "快速模式",
                        caption = "隔 16 格粗算，先出结果；关掉是逐格精确",
                        checked = state.fastMode,
                        onChange = { state = state.copy(fastMode = it) },
                    )
                }
            }

            item {
                FormSection(
                    title = "附加条件",
                    hint = "打开的都是硬条件；关掉的只影响排序（有樱花/野花优先，再看离原点远近）。",
                ) {
                    FormSwitch(
                        label = "要白桦森林",
                        checked = state.requireBirch,
                        onChange = { state = state.copy(requireBirch = it) },
                    )
                    FormSwitch(
                        label = "要樱花林",
                        checked = state.requireCherry,
                        onChange = { state = state.copy(requireCherry = it) },
                    )
                    FormSwitch(
                        label = if (paleApplies) "必须有苍白花园" else "苍白花园（1.21.4+ 才有）",
                        enabled = paleApplies,
                        checked = state.requirePale && paleApplies,
                        onChange = { state = state.copy(requirePale = it) },
                    )
                    FormSwitch(
                        label = "要玄武岩三角洲",
                        caption = "对应下界群系",
                        checked = state.requireBasalt,
                        onChange = { state = state.copy(requireBasalt = it) },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            PillChip(
                                label = when (state.orientation) {
                                    1 -> "横向窗口"
                                    2 -> "竖向窗口"
                                    else -> "任意朝向"
                                },
                                selected = false,
                                onClick = { orientationMenu = true },
                            )
                            DropdownMenu(
                                expanded = orientationMenu,
                                onDismissRequest = { orientationMenu = false },
                            ) {
                                listOf(0 to "任意朝向", 1 to "横向", 2 to "竖向").forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            state = state.copy(orientation = value)
                                            orientationMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        LiYueField(
                            value = state.flowerMargin,
                            onValueChange = { state = state.copy(flowerMargin = it) },
                            label = { Text("花林离边") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.weight(1f),
                        )
                        LiYueField(
                            value = state.threads,
                            onValueChange = { state = state.copy(threads = it) },
                            label = { Text("线程") },
                            singleLine = true,
                            textStyle = NumStyle,
                            modifier = Modifier.width(84.dp),
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiYueActionButton(
                        text = if (state.running) "${state.progress}/${state.batches}" else "开始搜索",
                        onClick = { run() },
                        enabled = !state.running,
                        modifier = Modifier.weight(1f),
                        leading = {
                            if (state.running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.width(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            }
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            if (state.running) {
                                pausedFlag = !pausedFlag
                                state = state.copy(paused = pausedFlag)
                            }
                        },
                        enabled = state.running,
                    ) {
                        Text(if (state.paused) "继续" else "暂停")
                    }
                    OutlinedButton(onClick = { stop() }, enabled = state.running || job != null) {
                        Text("停止")
                    }
                }
            }

            state.error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            val hits = state.hits
            if (hits != null) {
                item {
                    Text(
                        text = if (hits.isEmpty()) {
                            "这个范围里没找到合规窗口 · ${state.elapsedMs} ms"
                        } else {
                            "${hits.size} 个窗口 · ${state.elapsedMs} ms"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(hits) { hit ->
                    FlowerFarmCard(hit, onOpenMap, state)
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("判定规则", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "窗口内必须有繁花森林与沼泽（1.21.4+ 还要苍白花园）；繁花森林面积要占窗口的 1/20 以上" +
                                "且不小于 4000 格；花田点周围 96 格内要同时出现蒲公英与铃兰两种花簇端，" +
                                "高度差不超过 32 格——这就是能搭出全部 11 种花的分层平台的位置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = historySheet,
            dragHandle = { LiYueSheetHandle() },
        ) {
            FlowerHistorySheet(
                entries = history,
                onPick = { entry ->
                    val exact = GameVersions.find(entry.versionLabel)
                    val applied = state.copy(
                        seedText = entry.seedText,
                        version = exact ?: state.version,
                        minX = entry.minX.toString(),
                        minZ = entry.minZ.toString(),
                        maxX = entry.maxX.toString(),
                        maxZ = entry.maxZ.toString(),
                        squareMode = false,
                        longSide = entry.longSide.toString(),
                        shortSide = entry.shortSide.toString(),
                        useShortSide = entry.longSide != entry.shortSide,
                        orientation = entry.orientation,
                        flowerMargin = entry.flowerMargin.toString(),
                        fastMode = entry.fastMode,
                        requirePale = entry.requirePale,
                        requireBirch = entry.requireBirch,
                        requireCherry = entry.requireCherry,
                        requireBasalt = entry.requireBasalt,
                    )
                    state = applied
                    showHistory = false
                    run(applied)
                },
                onDelete = { entry ->
                    scope.launch {
                        history = withContext(Dispatchers.IO) {
                            FlowerHistoryStore.remove(context, entry.id)
                        }
                    }
                },
                onClear = {
                    scope.launch {
                        withContext(Dispatchers.IO) { FlowerHistoryStore.clear(context) }
                        history = emptyList()
                    }
                },
            )
        }
    }
}


private val NumStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
)

/** 一张白纸卡片包住一组同类的输入项，标题在上，说明在下。 */
@Composable
private fun FormSection(
    title: String,
    hint: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (hint != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** 卡片里的次要操作，用带下划线的文字而不是又一个按钮。 */
@Composable
private fun InlineLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** 二选一的开关行：左边文字，右边 Switch，整行可点。 */
@Composable
private fun FormSwitch(
    label: String,
    checked: Boolean,
    onClickCaption: String? = null,
    caption: String? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                },
            )
            val sub = caption ?: onClickCaption
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                checkedBorderColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
private fun FlowerFarmCard(
    hit: SeedEngine.FlowerFarmHit,
    onOpenMap: (MapTarget) -> Unit,
    state: FlowerFarmState,
) {
    val seed = SeedEngine.parseJavaSeed(state.seedText)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${hit.flowerX} / ${hit.flowerZ}",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (hit.flowerY > 0) "Y ${hit.flowerY}" else "Y —",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(10.dp))
            StatRow("窗口", "${hit.sideX}×${hit.sideZ} · ${hit.orientation}")
            StatRow("花林", "${hit.flowerArea} 格")
            StatRow("沼泽", "${hit.swampArea} 格")
            if (hit.paleArea > 0) StatRow("苍白花园", "${hit.paleArea} 格")
            StatRow("下界", hit.nether)
            if (hit.bonus.isNotBlank()) StatRow("加分群系", hit.bonus)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "从 Y ${if (hit.flowerY > 0) hit.flowerY else "—"} 往上 32 格搭分层平台，就是完整的树花梯度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                InlineLink("打开花田") {
                    onOpenMap(
                        MapTarget(
                            seed = seed,
                            seedText = state.seedText,
                            versionLabel = state.version.label,
                            dimension = Dimension.OVERWORLD,
                            centerX = hit.flowerX.toDouble(),
                            centerZ = hit.flowerZ.toDouble(),
                        ),
                    )
                }
                InlineLink("打开沼泽") {
                    onOpenMap(
                        MapTarget(
                            seed = seed,
                            seedText = state.seedText,
                            versionLabel = state.version.label,
                            dimension = Dimension.OVERWORLD,
                            centerX = hit.swampX.toDouble(),
                            centerZ = hit.swampZ.toDouble(),
                        ),
                    )
                }
            }
        }
    }
}

/** 结果卡里的「标签 —— 数值」一行。 */
@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowerHistorySheet(
    entries: List<FlowerHistoryEntry>,
    onPick: (FlowerHistoryEntry) -> Unit,
    onDelete: (FlowerHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("搜索历史", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                "${entries.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        }
        Text(
            "点一条就会把种子、版本和全部参数填回去，并立刻重跑一次。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entries.isEmpty()) {
            Text(
                "还没有记录：跑完一次搜索就会自动存下来。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(entry) }
                            .padding(vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "种子 ${entry.seedText} · ${entry.versionLabel}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    entry.summary(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "范围 ${entry.minX}..${entry.maxX} / ${entry.minZ}..${entry.maxZ} · " +
                                        "${entry.elapsedMs / 1000} 秒",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onPick(entry) }) { Text("复跑") }
                            IconButton(onClick = { onDelete(entry) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
