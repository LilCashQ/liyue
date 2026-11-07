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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FlowerFarmState(
    val seedText: String = "0",
    val version: GameVersion = GameVersions.default,
    val radius: Int = 2048,
    val longSide: Int = 480,
    val shortSide: Int = 320,
    val squareOnly: Boolean = false,
    val requireBothBonus: Boolean = false,
    val requireBasalt: Boolean = false,
    val requirePale: Boolean = true,
    val running: Boolean = false,
    val elapsedMs: Long = 0,
    val hits: List<SeedEngine.FlowerFarmHit>? = null,
    val error: String? = null,
)

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

    fun run() {
        if (state.running) return
        val snapshot = state
        state = snapshot.copy(running = true, hits = null, error = null)
        scope.launch {
            val started = System.nanoTime()
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val seed = SeedEngine.parseJavaSeed(snapshot.seedText)
                    val half = snapshot.radius
                    engine.flowerSearchBlocking(
                        seed = seed,
                        version = snapshot.version.engine,
                        minX = -half,
                        minZ = -half,
                        maxX = half,
                        maxZ = half,
                        longSide = snapshot.longSide,
                        shortSide = snapshot.shortSide,
                        squareOnly = snapshot.squareOnly,
                        requirePale = snapshot.requirePale &&
                            snapshot.version.engine.ordinal >= McVersion.MC_1_21_4.ordinal,
                        requireBothBonus = snapshot.requireBothBonus,
                        requireBasalt = snapshot.requireBasalt,
                        maxRows = 16,
                    )
                }
            }
            val millis = (System.nanoTime() - started) / 1_000_000
            state = result.fold(
                onSuccess = { snapshot.copy(running = false, elapsedMs = millis, hits = it) },
                onFailure = {
                    snapshot.copy(running = false, elapsedMs = millis, error = it.message ?: "搜索失败")
                },
            )
        }
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
                        Text("窗口尺寸", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "窗口里同时要有花林和沼泽" + if (paleApplies) "，1.21.4 起还必须有苍白花园" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(320, 480, 640, 960).forEach { side ->
                                FilterChip(
                                    selected = state.longSide == side,
                                    onClick = {
                                        state = state.copy(
                                            longSide = side,
                                            shortSide = if (state.shortSide > side) side else state.shortSide,
                                        )
                                    },
                                    label = { Text("$side") },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(320, 480, 640).forEach { side ->
                                FilterChip(
                                    selected = state.shortSide == side,
                                    onClick = { state = state.copy(shortSide = side) },
                                    label = { Text("短边 $side") },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("搜索范围（半径方块）", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(512, 1024, 2048, 4096).forEach { radius ->
                                FilterChip(
                                    selected = state.radius == radius,
                                    onClick = { state = state.copy(radius = radius) },
                                    label = { Text("$radius") },
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.squareOnly,
                                onClick = { state = state.copy(squareOnly = !state.squareOnly) },
                                label = { Text("只要正方形") },
                            )
                            FilterChip(
                                selected = state.requireBothBonus,
                                onClick = { state = state.copy(requireBothBonus = !state.requireBothBonus) },
                                label = { Text("野花+花瓣") },
                            )
                            FilterChip(
                                selected = state.requireBasalt,
                                onClick = { state = state.copy(requireBasalt = !state.requireBasalt) },
                                label = { Text("要玄武岩") },
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { run() },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
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
                    Text(if (state.running) "搜索中…" else "开始搜索")
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
                            "窗口内花林面积要超过 2000 格，且有沼泽（1.21.4+ 还要苍白花园）；" +
                                "花田高度是让蒲公英与铃兰两端都落在 32 格内的那个 Y。" +
                                "「野花+花瓣」要求同时有野花（白桦/草甸）与粉红花瓣（樱花林）。",
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
                text = "中心 ${hit.centerX} / ${hit.centerZ}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "窗口 ${hit.sideX}×${hit.sideZ} · 花林 ${hit.flowerArea} · 沼泽 ${hit.swampArea}" +
                    if (hit.paleArea > 0) " · 苍白 ${hit.paleArea}" else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "花田 Y ${if (hit.flowerY > 0) hit.flowerY else "—"} · 下界 ${hit.nether}" +
                    if (hit.bonus.isNotBlank()) " · ${hit.bonus}" else "",
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
                TextButton(
                    onClick = {
                        onOpenMap(
                            MapTarget(
                                seed = seed,
                                seedText = state.seedText,
                                versionLabel = state.version.label,
                                dimension = Dimension.OVERWORLD,
                                centerX = hit.centerX.toDouble(),
                                centerZ = hit.centerZ.toDouble(),
                            ),
                        )
                    },
                ) { Text("窗口中心") }
            }
        }
    }
}
