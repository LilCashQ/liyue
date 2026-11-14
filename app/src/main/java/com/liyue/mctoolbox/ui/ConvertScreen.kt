package com.liyue.mctoolbox.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frenetic.litechunk.api.FormatGroup
import com.liyue.mctoolbox.convert.ConvertService
import com.liyue.mctoolbox.convert.WorldConverter
import com.liyue.mctoolbox.map.LiYueActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ConvertEntry(
    val id: Long,
    val name: String,
    val dir: File,
    val detected: String,
    val sizeMb: Long,
)

private data class OutputEntry(val name: String, val file: File, val sizeMb: Long)

private enum class ConvertTab(val label: String) { CONVERT("转换"), FILES("文件") }

/**
 * 存档转换。版式与 LiteChunk 一致：顶部标题栏 + 两个页签，转换页是
 * 「转化列表」和「输出格式」两张卡，文件页列出转好的存档。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(ConvertTab.CONVERT) }
    var entries by remember { mutableStateOf<List<ConvertEntry>>(emptyList()) }
    var groups by remember { mutableStateOf<List<FormatGroup>>(emptyList()) }
    var engineVersion by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("JAVA") }
    var targetId by remember { mutableStateOf<String?>(null) }
    var outputName by remember { mutableStateOf("") }
    var deleteSource by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var outputs by remember { mutableStateOf<List<OutputEntry>>(emptyList()) }
    var showVersions by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    fun refreshOutputs() {
        val dir = File(context.filesDir, "outputs")
        outputs = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.map { OutputEntry(it.name, it, it.length() / 1024 / 1024) } ?: emptyList()
    }

    LaunchedEffect(Unit) {
        refreshOutputs()
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val engine = WorldConverter.engine(context)
                engine.engineVersion() to engine.formats()
            }.getOrNull()
        }
        if (loaded != null) {
            engineVersion = loaded.first ?: ""
            groups = loaded.second
            groups.firstOrNull()?.let { first ->
                groupId = first.id
                targetId = first.versions.firstOrNull()?.id
            }
        }
    }

    fun addSource(uri: Uri, isFile: Boolean) {
        busy = true
        stage = if (isFile) "正在解包…" else "正在复制存档…"
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val slot = File(context.cacheDir, "world-in-${System.currentTimeMillis()}")
                    val dir = if (isFile) {
                        WorldConverter.unpack(context, uri, slot, "source")
                    } else {
                        WorldConverter.materialize(context, uri, slot)
                    } ?: error("这个文件夹里没有 level.dat，也没有 db/")
                    val info = WorldConverter.detect(context, dir)
                    val label = if (info == null) {
                        "未识别版本"
                    } else {
                        "${info.formatLabel} · ${info.version ?: "未知版本"}"
                    }
                    val size = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024 / 1024
                    ConvertEntry(System.currentTimeMillis(), dir.name, dir, label, size)
                }
            }
            busy = false
            stage = ""
            result.onSuccess { entry ->
                entries = entries + entry
                if (outputName.isBlank()) outputName = "${entry.name}-${groupId.lowercase()}"
                message = null
            }.onFailure { message = it.message ?: "读取失败" }
        }
    }

    val addFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? -> if (uri != null) addSource(uri, isFile = false) }

    val addFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) addSource(uri, isFile = true) }

    var pendingSave by remember { mutableStateOf<File?>(null) }
    val saveOutput = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val pending = pendingSave
        if (uri == null || pending == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        pending.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("写入失败")
                }.isSuccess
            }
            message = if (ok) "已保存到所选位置" else "保存失败"
            pendingSave = null
        }
    }

    fun runConversion() {
        val entry = entries.firstOrNull() ?: return
        val format = targetId ?: return
        busy = true
        progress = 0f
        stage = "准备中…"
        message = null
        val ext = if (groupId == "BEDROCK") "mcworld" else "zip"
        val name = (outputName.ifBlank { entry.name }).let {
            if (it.endsWith(".zip") || it.endsWith(".mcworld")) it else "$it.$ext"
        }
        val outDir = File(context.cacheDir, "world-out")
        val target = File(context.filesDir, "outputs").apply { mkdirs() }.let { File(it, name) }
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                if (resultData == null) return
                when (resultData.getInt(ConvertService.KEY_KIND)) {
                    ConvertService.KIND_PROGRESS -> {
                        progress = resultData.getFloat(ConvertService.KEY_PROGRESS, 0f)
                        stage = resultData.getString(ConvertService.KEY_STAGE).orEmpty().ifBlank { "转换中…" }
                    }
                    ConvertService.KIND_DONE -> {
                        busy = false
                        stage = ""
                        message = "转换完成：$name"
                        refreshOutputs()
                        tab = ConvertTab.FILES
                        if (deleteSource) {
                            entry.dir.deleteRecursively()
                            entries = entries.filterNot { it.id == entry.id }
                        }
                    }
                    ConvertService.KIND_ERROR -> {
                        busy = false
                        stage = ""
                        message = if (resultData.getBoolean(ConvertService.KEY_OOM)) {
                            "内存不足：这张地图太大，转换引擎吃不下。可以先把地图拆小，或换台内存更大的手机。"
                        } else {
                            "转换失败：" + resultData.getString(ConvertService.KEY_MESSAGE).orEmpty()
                        }
                    }
                }
            }
        }
        val intent = Intent(context, ConvertService::class.java).apply {
            putExtra(ConvertService.EXTRA_INPUT, entry.dir.absolutePath)
            putExtra(ConvertService.EXTRA_OUTPUT, outDir.absolutePath)
            putExtra(ConvertService.EXTRA_TARGET, target.absolutePath)
            putExtra(ConvertService.EXTRA_FORMAT, format)
            putExtra(ConvertService.EXTRA_RECEIVER, receiver)
        }
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            busy = false
            message = "启动转换服务失败：${it.message}"
        }
    }

    Scaffold(
        topBar = {
            ConvertHeader(
                engineVersion = engineVersion,
                onBack = onBack,
                onInfo = { showAbout = true },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ConvertTabs(selected = tab, onSelect = { tab = it })
            when (tab) {
                ConvertTab.CONVERT -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "转化列表",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { addFile.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "添加存档")
                                }
                            }
                            if (entries.isEmpty()) {
                                DashedEmptyBox(
                                    title = "点右上角 + 添加存档",
                                    hint = "支持 .zip / .mcworld / 世界文件夹",
                                    onClick = { addFolder.launch(null) },
                                )
                            } else {
                                entries.forEachIndexed { index, entry ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }
                                    EntryRow(
                                        entry = entry,
                                        onRemove = {
                                            entry.dir.deleteRecursively()
                                            entries = entries.filterNot { it.id == entry.id }
                                        },
                                        onOpen = {
                                            outputName = "${entry.name}-${groupId.lowercase()}"
                                        },
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = { addFolder.launch(null) }) {
                                        Text("添加文件夹")
                                    }
                                    TextButton(onClick = { addFile.launch(arrayOf("*/*")) }) {
                                        Text("添加压缩包")
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("输出格式", style = MaterialTheme.typography.titleMedium)
                            SegmentedRow(
                                options = groups.map { it.id to it.label },
                                selected = groupId,
                                onSelect = { id ->
                                    groupId = id
                                    targetId = groups.firstOrNull { it.id == id }
                                        ?.versions?.firstOrNull()?.id
                                },
                            )
                            PickerCard(
                                label = "目标版本",
                                value = targetId ?: "未选择",
                                enabled = groups.isNotEmpty() && !busy,
                                onClick = { showVersions = !showVersions },
                            )
                            if (showVersions) {
                                val versions = groups.firstOrNull { it.id == groupId }?.versions ?: emptyArray()
                                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                    items(versions.toList()) { version ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    targetId = version.id
                                                    showVersions = false
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = version.version ?: version.id,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = version.id,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            PickerCard(
                                label = "输出命名",
                                value = outputName.ifBlank { "未命名" },
                                enabled = !busy && entries.isNotEmpty(),
                                onClick = {
                                    outputName = (outputName.substringBeforeLast('.')
                                        .ifBlank { entries.firstOrNull()?.name ?: "world" }) + "-" +
                                        groupId.lowercase()
                                },
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(enabled = !busy) {
                                    deleteSource = !deleteSource
                                },
                            ) {
                                Checkbox(
                                    checked = deleteSource,
                                    onCheckedChange = { deleteSource = it },
                                    enabled = !busy,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.outline,
                                    ),
                                )
                                Text(
                                    text = "转出后自动删除原存档",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Text(
                                text = "引擎 ${engineVersion.ifBlank { "—" }} · 支持 ${groups.size} 个平台",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (busy) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stage.ifBlank { "转换中…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    LiYueActionButton(
                        text = "开始转换",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && entries.isNotEmpty() && targetId != null,
                        onClick = { runConversion() },
                    )
                    message?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }

                ConvertTab.FILES -> Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "转换完成的存档会出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                    if (outputs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(18.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("还没有输出文件", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "转换完成的存档会出现在这里",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp)) {
                            items(outputs) { output ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = output.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                            )
                                            Text(
                                                text = "${output.sizeMb} MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        TextButton(onClick = {
                                            pendingSave = output.file
                                            saveOutput.launch(output.name)
                                        }) { Text("保存") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("关于转换引擎") },
            text = {
                Text(
                    "引擎来自 Chunker 的精简版（LiteChunk 使用的同一套实现），" +
                        "全部转换在本机完成，不上传任何数据。\n\n" +
                        "引擎版本 ${engineVersion.ifBlank { "—" }}，支持 Java 版与基岩版互转。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun ConvertHeader(
    engineVersion: String,
    onBack: () -> Unit,
    onInfo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "存档转换",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "引擎 ${engineVersion.ifBlank { "加载中" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, contentDescription = "说明")
            }
        }
    }
}

@Composable
private fun ConvertTabs(selected: ConvertTab, onSelect: (ConvertTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        ConvertTab.entries.forEach { entry ->
            val active = entry == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 3.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun DashedEmptyBox(title: String, hint: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .drawBehind {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    ),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryRow(entry: ConvertEntry, onRemove: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = "${entry.detected} · ${entry.sizeMb} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SegmentedRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { (id, label) ->
                val active = id == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(11.dp),
                        )
                        .clickable { onSelect(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerCard(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
