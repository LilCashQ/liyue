package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.map.LiYueActionButton
import com.liyue.mctoolbox.map.PillChip
import org.json.JSONArray
import org.json.JSONObject

/**
 * rawtext 构建器：可视化拼出 tellraw 的 JSON。
 * 支持文本、翻译键、计分板、选择器四种元素，可增删与上下移动。
 */
private enum class ElementKind(val label: String, val hint: String) {
    TEXT("文本", "直接写一句话"),
    TRANSLATE("翻译键", "如 chat.type.text"),
    SCORE("计分板", "显示某个记分项的值"),
    SELECTOR("选择器", "如 @a[name=Steve]"),
}

private data class Element(
    val kind: ElementKind,
    val primary: String = "",
    val secondary: String = "",
    val third: String = "",
)

private fun Element.toJson(): JSONObject {
    val obj = JSONObject()
    when (kind) {
        ElementKind.TEXT -> obj.put("text", primary)
        ElementKind.TRANSLATE -> {
            obj.put("translate", primary)
            if (secondary.isNotBlank()) {
                obj.put("with", JSONArray().apply { put(JSONObject().put("text", secondary)) })
            }
        }
        ElementKind.SCORE -> {
            obj.put("score", JSONObject().apply {
                put("name", primary.ifBlank { "*" })
                put("objective", secondary.ifBlank { "points" })
            })
        }
        ElementKind.SELECTOR -> obj.put("selector", primary.ifBlank { "@a" })
    }
    if (kind == ElementKind.SCORE && third.isNotBlank()) {
        obj.put("score", JSONObject().apply {
            put("name", primary.ifBlank { "*" })
            put("objective", secondary.ifBlank { "points" })
            put("value", third)
        })
    }
    return obj
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawTextScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var target by remember { mutableStateOf("@a") }
    var command by remember { mutableStateOf("tellraw") }
    val elements = remember { mutableStateListOf(Element(ElementKind.TEXT, "欢迎来到里悦")) }
    var editing by remember { mutableStateOf(-1) }

    val json = remember(elements.toList()) {
        val root = JSONObject()
        root.put("rawtext", JSONArray().apply { elements.forEach { put(it.toJson()) } })
        root.toString(2)
    }
    val oneLine = remember(elements.toList()) {
        val root = JSONObject()
        root.put("rawtext", JSONArray().apply { elements.forEach { put(it.toJson()) } })
        root.toString()
    }
    val fullCommand = "$command $target $oneLine"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("rawtext 构建器") },
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
                text = "拼好元素后直接复制命令，粘到命令方块或聊天框里就能用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("目标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("@a", "@p", "@s", "@r").forEach { preset ->
                            PillChip(
                                label = preset,
                                selected = target == preset,
                                onClick = { target = preset },
                            )
                        }
                    }
                    PlainField(
                        value = target,
                        onChange = { target = it },
                        placeholder = "@a 或 @a[name=Steve]",
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("元素 ${elements.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    elements.forEachIndexed { index, element ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { editing = if (editing == index) -1 else index },
                            ) {
                                Text(
                                    text = "${element.kind.label} · ${element.primary.ifBlank { "（空）" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = element.secondary.ifBlank { element.kind.hint },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "删",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable {
                                        elements.removeAt(index)
                                        if (editing >= elements.size) editing = -1
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        if (editing == index) {
                            PlainField(
                                value = element.primary,
                                onChange = { elements[index] = element.copy(primary = it) },
                                placeholder = if (element.kind == ElementKind.SCORE) "玩家名或 *" else "内容",
                            )
                            PlainField(
                                value = element.secondary,
                                onChange = { elements[index] = element.copy(secondary = it) },
                                placeholder = when (element.kind) {
                                    ElementKind.SCORE -> "记分项名，如 points"
                                    ElementKind.TRANSLATE -> "可选：翻译参数"
                                    else -> "可选补充"
                                },
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ElementKind.entries.toList()) { kind ->
                            PillChip(
                                label = "+ ${kind.label}",
                                selected = false,
                                onClick = {
                                    elements.add(Element(kind))
                                    editing = elements.size - 1
                                },
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("JSON", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = json,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiYueActionButton(
                            text = "复制 JSON",
                            onClick = {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(ClipData.newPlainText(json, json))
                            },
                            modifier = Modifier.weight(1f),
                        )
                        LiYueActionButton(
                            text = "复制命令",
                            onClick = {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(ClipData.newPlainText(fullCommand, fullCommand))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = fullCommand,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}
