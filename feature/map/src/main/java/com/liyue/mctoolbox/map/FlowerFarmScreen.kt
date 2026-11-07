package com.liyue.mctoolbox.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val threads: String = "8",
    val orientation: Int = 0,
    val flowerMargin: String = "0",
    val requireBothBonus: Boolean = false,
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

    fun run() {
        if (state.running) return
        val snapshot = state
        val rect = bounds(snapshot)
        val longSide = snapshot.longSide.toIntOr(640).coerceIn(64, 1024)
        val shortSide = if (snapshot.useShortSide) {
            snapshot.shortSide.toIntOr(320).coerceIn(64, 1024)
        } else {
            longSide
        }
        val threads = snapshot.threads.toIntOr(8).coerceIn(1, 32)
        val seed = SeedEngine.parseJavaSeed(snapshot.seedText)
        val requirePale = snapshot.requirePale &&
            snapshot.version.engine.ordinal >= McVersion.MC_1_21_4.ordinal
        val margin = snapshot.flowerMargin.toIntOr(0).coerceIn(0, 512)
        val fastMode = snapshot.fastMode
        val orientation = snapshot.orientation
        val bothBonus = snapshot.requireBothBonus
        val basalt = snapshot.requireBasalt
        val version = snapshot.version.engine

        pausedFlag = false
        cancelled = false

        // The native search is a blocking call over a rectangle, so the thread
        // count is spent by splitting the area into horizontal bands and running
        // them side by side. Pause takes effect between bands.
        val bandCount = threads.coerceAtMost(((rect[3] - rect[1]) / 4096).coerceAtLeast(1))
        val bandHeight = ((rect[3] - rect[1]) / bandCount).coerceAtLeast(512)

        state = snapshot.copy(
            running = true, paused = false, hits = null, error = null,
            progress = 0, batches = bandCount,
        )
        job = scope.launch {
            val started = System.nanoTime()
            val collected = mutableListOf<SeedEngine.FlowerFarmHit>()
            var failure: String? = null
            try {
                coroutineScope {
                    val bands = (0 until bandCount).map { index ->
                        val z0 = rect[1] + index * bandHeight
                        val z1 = if (index == bandCount - 1) rect[3] else z0 + bandHeight
                        Triple(z0, z1, index)
                    }
                    val results = bands.map { (z0, z1, index) ->
                        launch(Dispatchers.Default) {
                            while (pausedFlag && !cancelled) delay(120)
                            if (cancelled) return@launch
                            val found = runCatching {
                                engine.flowerSearchBlocking(
                                    seed = seed,
                                    version = version,
                                    minX = rect[0],
                                    minZ = z0,
                                    maxX = rect[2],
                                    maxZ = z1,
                                    longSide = longSide,
                                    shortSide = shortSide,
                                    squareOnly = false,
                                    requirePale = requirePale,
                                    requireBothBonus = bothBonus,
                                    requireBasalt = basalt,
                                    orientation = orientation,
                                    flowerMargin = margin,
                                    fastMode = fastMode,
                                    maxRows = 12,
                                )
                            }
                            found.onFailure { if (failure == null) failure = it.message ?: "搜索失败" }
                            synchronized(collected) {
                                found.getOrNull()?.let { collected.addAll(it) }
                            }
                            state = state.copy(progress = state.progress + 1)
                            index
                        }
                    }
                    results.forEach { it.join() }
                }
            } finally {
                val millis = (System.nanoTime() - started) / 1_000_000
                val merged = collected
                    .sortedWith(
                        compareByDescending<SeedEngine.FlowerFarmHit> { it.bonus.isNotBlank() }
                            .thenByDescending { it.nether == "玄武岩三角洲" }
                            .thenBy { kotlin.math.abs(it.flowerX) + kotlin.math.abs(it.flowerZ) },
                    )
                    .take(40)
                state = state.copy(
                    running = false,
                    paused = false,
                    elapsedMs = millis,
                    hits = merged,
                    error = if (cancelled) null else failure,
                )
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
                title = { Text("全花农场") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.seedText,
                        onValueChange = { state = state.copy(seedText = it) },
                        label = { Text("种子") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        OutlinedButton(onClick = { versionMenu = true }) {
                            Text(state.version.label)
                        }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("搜索范围", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "主世界方块坐标，范围越大越慢；±16384 一般几秒，±500000 要几分钟。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.minX,
                                onValueChange = { state = state.copy(minX = it) },
                                label = { Text("MinX") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.maxX,
                                onValueChange = { state = state.copy(maxX = it) },
                                label = { Text("MaxX") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.minZ,
                                onValueChange = { state = state.copy(minZ = it) },
                                label = { Text("MinZ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.maxZ,
                                onValueChange = { state = state.copy(maxZ = it) },
                                label = { Text("MaxZ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                state = state.copy(
                                    minX = "-16384", maxX = "16384",
                                    minZ = "-16384", maxZ = "16384",
                                )
                            }) { Text("重置为默认区域") }
                            TextButton(onClick = {
                                state = state.copy(
                                    minX = "-500000", maxX = "500000",
                                    minZ = "-500000", maxZ = "500000",
                                )
                            }) { Text("世界边界") }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("窗口尺寸", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "窗口里要同时有繁花森林、沼泽" +
                                (if (paleApplies) "和苍白花园" else "") +
                                "，而且繁花森林里要有完整的树花梯度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.longSide,
                                onValueChange = { state = state.copy(longSide = it) },
                                label = { Text("窗口长边") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.shortSide,
                                onValueChange = { state = state.copy(shortSide = it) },
                                label = { Text("窗口短边") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = state.moduleGap,
                                onValueChange = { state = state.copy(moduleGap = it) },
                                label = { Text("模距") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val gap = state.moduleGap.toIntOr(10).coerceIn(1, 31)
                                val side = (32 * gap + 16).coerceAtMost(1024)
                                state = state.copy(longSide = side.toString(), useShortSide = false)
                            }) { Text("按模距定长边") }
                        }
                        Text(
                            "长边 = 32 × 模距 + 16（封顶 1024）；取消短边就是正方形窗口。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.useShortSide,
                                onClick = { state = state.copy(useShortSide = !state.useShortSide) },
                                label = { Text("启用短边") },
                            )
                            FilterChip(
                                selected = state.fastMode,
                                onClick = { state = state.copy(fastMode = !state.fastMode) },
                                label = { Text("快速模式") },
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("附加条件", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "勾上的必须是硬条件；没勾的只影响排序（有樱花/野花优先，再看离原点远近）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.requireBothBonus,
                                onClick = { state = state.copy(requireBothBonus = !state.requireBothBonus) },
                                label = { Text("要野花+樱花") },
                            )
                            FilterChip(
                                selected = state.requireBasalt,
                                onClick = { state = state.copy(requireBasalt = !state.requireBasalt) },
                                label = { Text("要玄武岩") },
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column {
                                OutlinedButton(onClick = { orientationMenu = true }) {
                                    Text(
                                        when (state.orientation) {
                                            1 -> "横向"
                                            2 -> "竖向"
                                            else -> "任意朝向"
                                        },
                                    )
                                }
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
                            OutlinedTextField(
                                value = state.flowerMargin,
                                onValueChange = { state = state.copy(flowerMargin = it) },
                                label = { Text("花林离边距离") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.threads,
                                onValueChange = { state = state.copy(threads = it) },
                                label = { Text("线程数") },
                                singleLine = true,
                                modifier = Modifier.width(96.dp),
                            )
                        }
                        FilterChip(
                            selected = state.requirePale && paleApplies,
                            onClick = { state = state.copy(requirePale = !state.requirePale) },
                            enabled = paleApplies,
                            label = { Text(if (paleApplies) "必须有苍白花园" else "苍白花园（1.21.4+ 才有）") },
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { run() },
                        enabled = !state.running,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.running) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.running) "${state.progress}/${state.batches}" else "开始搜索")
                    }
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
}

@Composable
private fun FlowerFarmCard(
    hit: SeedEngine.FlowerFarmHit,
    onOpenMap: (MapTarget) -> Unit,
    state: FlowerFarmState,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "花田 ${hit.flowerX} / ${hit.flowerZ} · Y ${if (hit.flowerY > 0) hit.flowerY else "—"}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "窗口 ${hit.sideX}×${hit.sideZ}（${hit.orientation}） · 花林 ${hit.flowerArea}" +
                    " · 沼泽 ${hit.swampArea}" + if (hit.paleArea > 0) " · 苍白 ${hit.paleArea}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "下界 ${hit.nether}" + if (hit.bonus.isNotBlank()) " · ${hit.bonus}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "从 Y ${if (hit.flowerY > 0) hit.flowerY else "—"} 往上 32 格搭分层平台，" +
                    "就是完整的树花梯度",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val seed = SeedEngine.parseJavaSeed(state.seedText)
                TextButton(
                    onClick = {
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
                    },
                ) { Text("花田") }
                TextButton(
                    onClick = {
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
                    },
                ) { Text("沼泽") }
            }
        }
    }
}
