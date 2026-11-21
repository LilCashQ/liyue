package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/** 这些参数的值是 {...} 结构，可以用子编辑器拼。 */
private val NESTED_KEYS = setOf("scores", "nbt", "advancements")

/** 目标选择器里常用的参数，点了就加一行。 */
private val SELECTOR_KEYS = listOf(
    "name" to "玩家名，可用 ! 取反",
    "type" to "实体类型，如 minecraft:zombie",
    "tag" to "计分板标签",
    "team" to "队伍名",
    "distance" to "距离，如 ..10 或 5..20",
    "gamemode" to "creative / survival / spectator",
    "level" to "经验等级，如 30..",
    "scores" to "计分项，如 {points=1..}",
    "limit" to "最多返回几个",
    "sort" to "nearest / furthest / random / arbitrary",
    "nbt" to "NBT 匹配，如 {IsBaby:1b}",
    "predicate" to "谓词，如 minecraft:example",
    "x" to "相对坐标基准",
    "y" to "",
    "z" to "",
)

/**
 * 选择器参数编辑器：拼出 @a[参数=值,…]，空值参数自动省略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var base by remember { mutableStateOf("@a") }
    val params = remember { mutableStateListOf<Pair<String, String>>() }
    // 复合参数（scores / nbt / advancements）用子编辑器拼 {...}
    var nestedIndex by remember { mutableStateOf(-1) }

    val result = remember(base, params.toList()) {
        val body = params
            .filter { it.second.isNotBlank() }
            .joinToString(",") { "${it.first}=${it.second}" }
        if (body.isBlank()) base else "$base[$body]"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择器编辑器") },
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
                text = "选一个基础选择器，再加参数。值为空的行不会写进结果，所以可以先加着再填。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("基础", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("@a", "@p", "@r", "@s", "@e").forEach { preset ->
                            PillChip(
                                label = preset,
                                selected = base == preset,
                                onClick = { base = preset },
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "参数 ${params.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    params.forEachIndexed { index, pair ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                text = pair.first,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            SmallField(
                                value = pair.second,
                                onChange = { params[index] = pair.first to it },
                                placeholder = SELECTOR_KEYS.firstOrNull { it.first == pair.first }?.second.orEmpty(),
                                modifier = Modifier.weight(1f),
                            )
                            if (pair.first in NESTED_KEYS) {
                                Text(
                                    text = "≡",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { nestedIndex = index }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Text(
                                text = "删",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable { params.removeAt(index) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    Text(
                        text = "点下面的参数名添加一行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SELECTOR_KEYS.forEach { key ->
                        Text(
                            text = "+ ${key.first}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { params.add(key.first to "") }
                                .padding(vertical = 6.dp),
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
                    Text("结果", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = result,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 24.sp),
                    )
                    LiYueActionButton(
                        text = "复制选择器",
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)
                            clip?.setPrimaryClip(ClipData.newPlainText(result, result))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (nestedIndex in params.indices) {
        val key = params[nestedIndex].first
        NestedEditor(
            title = "$key={…}",
            initial = params[nestedIndex].second,
            onDismiss = { nestedIndex = -1 },
            onConfirm = { text ->
                params[nestedIndex] = key to text
                nestedIndex = -1
            },
        )
    }
}

/** scores={a=1..,b=2} 这类复合参数的小编辑器。 */
@Composable
private fun NestedEditor(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val rows = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            val body = initial.trim().removePrefix("{").removeSuffix("}")
            if (body.isNotBlank()) {
                body.split(',').forEach { part ->
                    val idx = part.indexOf('=')
                    if (idx > 0) add(part.substring(0, idx).trim() to part.substring(idx + 1).trim())
                }
            }
            if (isEmpty()) add("" to "")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { index, pair ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        SmallField(
                            value = pair.first,
                            onChange = { rows[index] = it to pair.second },
                            placeholder = "键",
                            modifier = Modifier.weight(1f),
                        )
                        Text(" = ", style = MaterialTheme.typography.labelMedium)
                        SmallField(
                            value = pair.second,
                            onChange = { rows[index] = pair.first to it },
                            placeholder = "值",
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "删",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { rows.removeAt(index) }
                                .padding(8.dp),
                        )
                    }
                }
                TextButton(onClick = { rows.add("" to "") }) { Text("+ 加一项") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = rows
                    .filter { it.first.isNotBlank() }
                    .joinToString(",") { "${it.first}=${it.second}" }
                onConfirm(if (body.isBlank()) "" else "{$body}")
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SmallField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(0.dp)),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
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
