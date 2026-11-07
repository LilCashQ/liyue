package com.liyue.mctoolbox.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** One row a script produced with `emit(x, z, label)`. */
data class ScriptHit(val x: Int, val z: Int, val label: String)

/** Parsed report of a script run. */
data class ScriptReport(
    val ok: Boolean,
    val error: String?,
    val logs: List<String>,
    val hits: List<ScriptHit>,
    val elapsedMs: Long,
) {
    companion object {
        fun parse(json: String?, elapsedMs: Long): ScriptReport {
            if (json.isNullOrBlank()) {
                return ScriptReport(false, "原生层没有返回结果", emptyList(), emptyList(), elapsedMs)
            }
            return runCatching {
                val root = JSONObject(json)
                ScriptReport(
                    ok = root.optBoolean("ok", false),
                    error = if (root.isNull("error")) null else root.optString("error"),
                    logs = root.optJSONArray("logs").strings(),
                    hits = root.optJSONArray("hits").hits(),
                    elapsedMs = elapsedMs,
                )
            }.getOrElse {
                ScriptReport(false, it.message ?: "结果解析失败", emptyList(), emptyList(), elapsedMs)
            }
        }

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotEmpty() } }
        }

        private fun JSONArray?.hits(): List<ScriptHit> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index ->
                val item = optJSONObject(index) ?: return@mapNotNull null
                ScriptHit(item.optInt("x"), item.optInt("z"), item.optString("label"))
            }
        }
    }
}

data class ScriptStudioState(
    val script: String = EXAMPLE,
    val seedText: String = "0",
    val version: GameVersion = GameVersions.default,
    val running: Boolean = false,
    val report: ScriptReport? = null,
) {
    companion object {
        val EXAMPLE = """
            -- 里悦脚本：找 4 座离原点最近的村庄
            local villages = getStructures(STRUCTURE.village, -2000, -2000, 2000, 2000)
            print('找到 ' .. #villages .. ' 座村庄')

            table.sort(villages, function (a, b)
                return (a.x * a.x + a.z * a.z) < (b.x * b.x + b.z * b.z)
            end)

            for i = 1, math.min(4, #villages) do
                local v = villages[i]
                emit(v.x, v.z, '村庄 ' .. i .. ' · 群系 ' .. getBiomeAt(v.x, v.z))
            end
        """.trimIndent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(
    onBack: () -> Unit,
    onOpenMap: (MapTarget) -> Unit,
) {
    val engine = remember { SeedEngine() }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ScriptStudioState()) }
    val report = state.report
    var versionMenu by remember { mutableStateOf(false) }

    fun runScript() {
        if (state.running) return
        val snapshot = state
        state = snapshot.copy(running = true, report = null)
        scope.launch {
            val started = System.nanoTime()
            val report = try {
                val raw = withContext(Dispatchers.Default) {
                    engine.runScriptBlocking(
                        script = snapshot.script,
                        seed = SeedEngine.parseJavaSeed(snapshot.seedText),
                        version = snapshot.version.engine,
                    )
                }
                ScriptReport.parse(raw, (System.nanoTime() - started) / 1_000_000)
            } catch (t: Throwable) {
                ScriptReport(false, t.message ?: "脚本执行失败", emptyList(), emptyList(), 0)
            }
            state = snapshot.copy(running = false, report = report)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("脚本工坊") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TextButton(onClick = { state = state.copy(script = ScriptStudioState.EXAMPLE) }) { Text("示例") } },
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(18.dp)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.script_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                    ),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text("脚本工坊", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "写几行 Lua，让整片区域替你算",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiYueField(
                        value = state.seedText,
                        onValueChange = { state = state.copy(seedText = it) },
                        label = { Text("种子") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
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
                                    text = {
                                        Text(group, style = MaterialTheme.typography.labelMedium)
                                    },
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
                LiYueField(
                    value = state.script,
                    onValueChange = { state = state.copy(script = it) },
                    label = { Text("脚本") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                )
            }
            item {
                Button(
                    onClick = { runScript() },
                    enabled = !state.running,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    Text(if (state.running) "运行中…" else "运行")
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("可用接口", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "print(...) 控制台输出\n" +
                                "emit(x, z, 标签) 加一条结果\n" +
                                "getBiomeAt(x, z) 或 getBiomeAt(x, y, z) 群系 id\n" +
                                "getStructures(STRUCTURE.village, x0, z0, x1, z1)\n" +
                                "getStrongholds(n) 前 n 座要塞\n" +
                                "slimeChunk(cx, cz) 是否史莱姆区块\n" +
                                "BIOME.plains、STRUCTURE.village 常量表\n" +
                                "全局 seed、mc",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
            if (report != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (report.ok) {
                                    "完成 · ${report.elapsedMs} ms · ${report.hits.size} 条结果"
                                } else {
                                    "出错"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (report.ok) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                            report.error?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            report.logs.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
                items(report.hits) { hit ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hit.label.ifBlank { "结果" }, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "X ${hit.x} · Z ${hit.z}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    onOpenMap(
                                        MapTarget(
                                            seed = SeedEngine.parseJavaSeed(state.seedText),
                                            seedText = state.seedText,
                                            versionLabel = state.version.label,
                                            dimension = Dimension.OVERWORLD,
                                            centerX = hit.x.toDouble(),
                                            centerZ = hit.z.toDouble(),
                                        ),
                                    )
                                },
                            ) { Text("地图") }
                        }
                    }
                }
            }
        }
    }
}
