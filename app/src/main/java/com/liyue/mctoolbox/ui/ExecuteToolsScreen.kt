package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liyue.mctoolbox.map.LiYueActionButton
import com.liyue.mctoolbox.map.PillChip

/**
 * 旧版 execute 语法转新版。逐行处理：不是 execute 的行原样保留。
 *
 * 旧：execute @a ~ ~ ~ say hi
 * 新：execute as @a at @a run say hi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecuteConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var input by remember {
        mutableStateOf("execute @a ~ ~ ~ say 你好\nexecute @p ~ ~ ~ detect ~ ~-1 ~ stone 0 say 踩到石头了")
    }
    var keepDetect by remember { mutableStateOf(true) }

    val output = remember(input, keepDetect) {
        input.lines().joinToString("\n") { convertExecuteLine(it, keepDetect) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("execute 语法转换") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "把旧版 execute 升级成新版格式。坐标不是 ~ ~ ~ 时用 positioned 指定位置；" +
                    "detect 会变成 if block。非 execute 的行原样输出。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            BlockField(value = input, onChange = { input = it }, label = "输入（支持多行）", minLines = 5)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(
                    checked = keepDetect,
                    onCheckedChange = { keepDetect = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.outline,
                    ),
                )
                Text("把 detect 转成 if block（新版没有 detect 子命令）", style = MaterialTheme.typography.bodySmall)
            }

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("转换结果", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = output.ifBlank { "（先在上面输入）" },
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp),
                    )
                    LiYueActionButton(
                        text = "复制结果",
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)
                            clip?.setPrimaryClip(ClipData.newPlainText(output, output))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

/**
 * 一行转换。名字里的 old/new 只是可读性，逻辑：
 * execute <目标> <x> <y> <z> [detect <bx> <by> <bz> <方块> [数据]] <命令…>
 */
internal fun convertExecuteLine(line: String, keepDetect: Boolean): String {
    val trimmed = line.trim()
    val tokens = trimmed.split(' ').filter { it.isNotEmpty() }
    if (tokens.size < 6 || tokens[0] != "execute") return line

    val target = tokens[1]
    val x = tokens[2]
    val y = tokens[3]
    val z = tokens[4]
    val rest = tokens.drop(5)
    if (rest.isEmpty()) return line

    val positionPart = if (x == "~" && y == "~" && z == "~") {
        "at $target"
    } else {
        "at $target positioned $x $y $z"
    }

    if (rest[0] == "detect") {
        if (!keepDetect) return line
        // detect <bx> <by> <bz> <block> [data] <command…>
        if (rest.size < 6) return line
        val block = rest[4]
        val afterBlock = rest.drop(5)
        // 旧语法里方块后面可能跟一个数字数据值，丢掉它
        val commandStart = if (afterBlock.size > 1 && afterBlock[0].toIntOrNull() != null) {
            afterBlock.drop(1)
        } else {
            afterBlock
        }
        if (commandStart.isEmpty()) return line
        val check = "if block ${rest[1]} ${rest[2]} ${rest[3]} $block"
        return "execute as $target $positionPart $check run ${commandStart.joinToString(" ")}"
    }

    // 旧语法里还能再套一层 execute，递归转换里面的那条
    if (rest[0] == "execute") {
        val inner = convertExecuteLine(rest.joinToString(" "), keepDetect)
        return "execute as $target $positionPart run $inner"
    }

    // 旧版还有 block 子命令的写法（少数版本支持）：execute @a x y z block <bx> <by> <bz> <block> <cmd>
    if (rest[0] == "block" && rest.size >= 6) {
        val block = rest[4]
        val command = rest.drop(5).joinToString(" ")
        return "execute as $target $positionPart if block ${rest[1]} ${rest[2]} ${rest[3]} $block run $command"
    }

    return "execute as $target $positionPart run ${rest.joinToString(" ")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypingAnimationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var objective by remember { mutableStateOf("T显") }
    var initialScore by remember { mutableStateOf("") }
    var initialize by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("@a") }

    val scope = rememberCoroutineScope()
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("T 显动画生成") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "生成打字机动画效果的 rawJSON",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("计分板名称")
                    BlockField(
                        value = objective,
                        onChange = { objective = it },
                        label = "",
                        minLines = 1,
                        placeholder = "例如：T显",
                    )
                    Text(
                        text = "动画靠这个计分板的分数推进，分数加 1 字数加 1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FieldLabel("初始的分数")
                    BlockField(
                        value = initialScore,
                        onChange = { initialScore = it },
                        label = "",
                        minLines = 1,
                        placeholder = "不填默认为 0",
                    )

                    FieldLabel("是否初始化")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillChip(label = "否", selected = !initialize, onClick = { initialize = false })
                        PillChip(label = "是", selected = initialize, onClick = { initialize = true })
                    }
                    Text(
                        text = "选「是」时每条外面会再套一层 /execute 与 titleraw，可直接整条粘贴执行；" +
                            "选「否」只输出 rawJSON 片段。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    FieldLabel("输入文本")
                    BlockField(
                        value = text,
                        onChange = { text = it },
                        label = "",
                        minLines = 4,
                        placeholder = "支持 §颜色码 和 \\n 换行",
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiYueActionButton(
                            text = "转换",
                            onClick = {
                                result = buildTypingFrames(
                                    text = text,
                                    objective = objective.ifBlank { "T显" },
                                    target = target,
                                    initialize = initialize,
                                    initialScore = initialScore.trim().toIntOrNull() ?: 0,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                        LiYueActionButton(
                            text = "复制",
                            onClick = {
                                if (result.isNotBlank()) {
                                    val clip = context.getSystemService(ClipboardManager::class.java)
                                    clip?.setPrimaryClip(ClipData.newPlainText(result, result))
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        LiYueActionButton(
                            text = "清空",
                            onClick = { text = ""; result = "" },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("转换结果", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = result.ifBlank { "转换结果将显示在这里…" },
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp),
                        color = if (result.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    if (result.isNotBlank()) {
                        LiYueActionButton(
                            text = "导出 Java 数据包（.zip）",
                            onClick = {
                                pendingBytes = buildTypingDatapack(
                                    chars = text.replace("\n", " ").toList(),
                                    target = target,
                                    objective = objective.ifBlank { "T显" },
                                )
                                saveZip.launch("里悦-T显动画.zip")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
}

/**
 * 逐字生成：每行一个 rawJSON 帧；选择初始化时前面套 execute if score + titleraw。
 * 支持 §颜色码，\n 会转成 JSON 里的换行转义。
 */
internal fun buildTypingFrames(
    text: String,
    objective: String,
    target: String,
    initialize: Boolean,
    initialScore: Int,
): String {
    val chars = text.replace("\n", " ").toList()
    if (chars.isEmpty()) return ""
    val head = buildString {
        if (initialize) {
            appendLine("scoreboard objectives add $objective dummy")
            appendLine("scoreboard players set $target $objective $initialScore")
        }
    }
    val frames = chars.indices.joinToString("\n") { index ->
        val shown = chars.take(index + 1).joinToString("")
        val json = "{\"rawtext\":[{\"text\":\"${shown.replace("\"", "\\\"")}\"}]}"
        if (initialize) {
            "execute if score $target $objective matches ${initialScore + index + 1} run titleraw $target title $json"
        } else {
            json
        }
    }
    return head + frames
}

@Composable
private fun BlockField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    minLines: Int,
    placeholder: String = "",
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(0.dp)),
            minLines = minLines,
            maxLines = 10,
            placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(0.dp),
        )
    }
}

/**
 * 打包成 Java 版数据包：pack.mcmeta + liyue:tick + liyue:start + 每帧的 title 命令。
 * 全部写进 tick.mcfunction，靠分数匹配触发，不依赖命令方块。
 */
internal fun buildTypingDatapack(chars: List<Char>, target: String, objective: String): ByteArray {
    val ns = "liyue"
    val packMeta = """{"pack":{"pack_format":48,"description":"里悦 T 显动画"}}"""
    val start = buildString {
        appendLine("scoreboard objectives add $objective dummy")
        appendLine("scoreboard players set $target $objective 0")
        appendLine("say [里悦] T 显动画已就绪，运行 /function $ns:tick 开始")
    }
    val tick = buildString {
        appendLine("scoreboard players add $target $objective 1")
        chars.forEachIndexed { index, _ ->
            val shown = chars.take(index + 1).joinToString("")
            val json = "{\"text\":\"${shown.replace("\"", "\\\"")}\"}"
            appendLine("execute if score $target $objective matches ${index + 1} run title $target title $json")
        }
        appendLine("execute if score $target $objective matches ${chars.size + 1} run scoreboard players set $target $objective 0")
    }
    val tag = """{"values":["$ns:tick"]}"""

    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        fun put(path: String, text: String) {
            zip.putNextEntry(ZipEntry(path))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        put("pack.mcmeta", packMeta)
        put("data/minecraft/tags/function/tick.json", tag)
        put("data/$ns/function/start.mcfunction", start)
        put("data/$ns/function/tick.mcfunction", tick)
    }
    return out.toByteArray()
}
