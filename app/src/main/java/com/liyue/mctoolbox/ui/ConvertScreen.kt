package com.liyue.mctoolbox.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.frenetic.litechunk.api.FormatVersion
import com.liyue.mctoolbox.convert.WorldConverter
import com.liyue.mctoolbox.map.LiYueActionButton
import com.liyue.mctoolbox.map.LiYueField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 存档转换：Java ⇄ 基岩。引擎来自 Chunker（同 LiteChunk），这里只做
 * 「选目录 → 检测 → 选目标版本 → 转换 → 存成 zip」这一条直线流程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var worldDir by remember { mutableStateOf<File?>(null) }
    var detected by remember { mutableStateOf("未选择存档") }
    var pickedName by remember { mutableStateOf("") }
    var formats by remember { mutableStateOf<List<FormatVersion>>(emptyList()) }
    var target by remember { mutableStateOf<FormatVersion?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var stage by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showVersions by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            runCatching {
                WorldConverter.engine(context).formats().flatMap { it.versions.toList() }
            }.getOrDefault(emptyList())
        }
        formats = list
        if (target == null) target = list.firstOrNull()
    }

    val pickTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        stage = "正在读取存档…"
        message = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = WorldConverter.materialize(context, uri, File(context.cacheDir, "world-in"))
                        ?: error("这个目录里没有 level.dat，也没有 db/")
                    val info = WorldConverter.detect(context, dir)
                    val list = WorldConverter.engine(context).formats()
                        .flatMap { group -> group.versions.toList() }
                    Triple(dir, info, list)
                }
            }
            busy = false
            stage = ""
            result.onSuccess { (dir, info, list) ->
                worldDir = dir
                pickedName = dir.name
                formats = list
                detected = if (info == null) {
                    "没认出这个存档的版本"
                } else {
                    "${info.formatLabel} · ${info.version ?: "未知版本"}"
                }
            }.onFailure { error ->
                message = error.message ?: "读取失败"
            }
        }
    }

    val saveZip = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        val zip = File(context.cacheDir, "converted.zip")
        if (uri == null || !zip.isFile) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        zip.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("写入失败")
                }.isSuccess
            }
            message = if (ok) "已保存到所选位置" else "保存失败"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("存档转换") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .heightIn()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            ConvertSection(title = "存档", hint = "选存档文件夹（含 level.dat 或 db/），会自动复制到应用缓存") {
                LiYueField(
                    value = if (pickedName.isEmpty()) "未选择" else pickedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("来源") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detected,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LiYueActionButton(
                    text = "选择存档文件夹",
                    onClick = { pickTree.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                )
            }

            ConvertSection(title = "目标版本", hint = "转换后写出的格式，越靠上越新") {
                PickerRow(
                    text = target?.let { "${it.id} · ${it.version ?: ""}" } ?: "未选择",
                    enabled = !busy && formats.isNotEmpty(),
                    onClick = { showVersions = !showVersions },
                )
                if (showVersions) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(formats) { format ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        target = format
                                        showVersions = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = format.id,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = format.version ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stage, style = MaterialTheme.typography.labelSmall)
            }

            LiYueActionButton(
                text = "开始转换",
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && worldDir != null && target != null,
                onClick = {
                    val dir = worldDir ?: return@LiYueActionButton
                    val format = target ?: return@LiYueActionButton
                    busy = true
                    progress = 0f
                    message = null
                    stage = "准备中…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val output = File(context.cacheDir, "world-out")
                                WorldConverter.convert(context, dir, output, format.id) { value, text ->
                                    progress = value
                                    if (text.isNotEmpty()) stage = text
                                }
                                val zip = File(context.cacheDir, "converted.zip")
                                WorldConverter.zip(output, zip)
                                zip
                            }
                        }
                        busy = false
                        stage = ""
                        result.onSuccess { zip ->
                            message = "转换完成（${zip.length() / 1024 / 1024} MB），选个位置保存"
                            saveZip.launch("${pickedName.ifEmpty { "world" }}-${format.let { it.id }}.zip")
                        }.onFailure { error ->
                            message = "转换失败：${error.message}"
                        }
                    }
                },
            )

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Suppress("unused")
private fun Context.noop() = Unit

@Composable
private fun ConvertSection(
    title: String,
    hint: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun PickerRow(text: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (text == "未选择") {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
