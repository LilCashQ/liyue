package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.map.PillChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基岩版命令 ID 速查：方块、物品、实体、魔咒…… 全部来自对应的基岩版本数据，
 * 搜索后点一行即可复制 ID。
 */
private data class IdCategory(val key: String, val label: String)

private val CATEGORIES = listOf(
    IdCategory("block", "方块"),
    IdCategory("item", "物品"),
    IdCategory("entity", "实体"),
    IdCategory("effect", "状态效果"),
    IdCategory("enchant", "魔咒"),
    IdCategory("fog", "迷雾"),
    IdCategory("biome", "生物群系"),
    IdCategory("location", "结构"),
    IdCategory("gamerule", "游戏规则"),
    IdCategory("entitySlot", "槽位类型"),
    IdCategory("damageCause", "伤害类型"),
    IdCategory("entityEvent", "实体事件"),
    IdCategory("entityFamily", "实体族"),
    IdCategory("inputPermission", "输入权限"),
    IdCategory("cameraPreset", "摄像机预设"),
    IdCategory("recipe", "配方"),
    IdCategory("hudElement", "HUD 元素"),
    IdCategory("animation", "动画"),
    IdCategory("animationController", "动画控制器"),
    IdCategory("feature", "地物"),
    IdCategory("featureRule", "地物规则"),
    IdCategory("sound", "声音"),
    IdCategory("lootTable", "战利品表"),
    IdCategory("command", "命令"),
)

private data class IdEntry(val category: String, val id: String, val name: String)

private object IdTable {
    private var loaded = false
    private var entries: List<IdEntry> = emptyList()

    suspend fun load(context: Context): List<IdEntry> = withContext(Dispatchers.IO) {
        if (loaded && entries.isNotEmpty()) return@withContext entries
        val list = ArrayList<IdEntry>(13000)
        runCatching {
            context.assets.open("bedrock_ids.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val first = line.indexOf('\t')
                    if (first <= 0) return@forEach
                    val second = line.indexOf('\t', first + 1)
                    val category = line.substring(0, first)
                    val id = if (second < 0) line.substring(first + 1) else line.substring(first + 1, second)
                    val name = if (second < 0) "" else line.substring(second + 1)
                    list.add(IdEntry(category, id, name))
                }
            }
        }
        entries = list
        loaded = true
        list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdTableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<IdEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { all = IdTable.load(context) }

    val shown = remember(all, query, category) {
        val trimmed = query.trim()
        all.asSequence()
            .filter { category == null || it.category == category }
            .filter {
                trimmed.isEmpty() ||
                    it.id.contains(trimmed, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true)
            }
            .take(600)
            .toList()
    }
    val counts = remember(all) { all.groupingBy { it.category }.eachCount() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基岩版 ID 表") },
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
                .padding(horizontal = 16.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                placeholder = { Text("搜 ID 或中文名，例如 diamond、燧石") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(14.dp),
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 10.dp),
            ) {
                item {
                    PillChip(
                        label = "全部 ${all.size}",
                        selected = category == null,
                        onClick = { category = null },
                    )
                }
                items(CATEGORIES) { item ->
                    PillChip(
                        label = "${item.label} ${counts[item.key] ?: 0}",
                        selected = category == item.key,
                        onClick = { category = if (category == item.key) null else item.key },
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (all.isEmpty()) "正在读取 ID 表…" else "没有匹配「$query」的条目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        itemsIndexed(shown, key = { _, item -> item.category + "/" + item.id }) { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                            IdRow(item, highlight = copied == item.id) {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(ClipData.newPlainText(item.id, item.id))
                                copied = item.id
                            }
                        }
                    }
                }
            }
            Text(
                text = "数据对应基岩版 1.26.62 · 点一行复制 ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun IdRow(entry: IdEntry, highlight: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.id,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
            if (entry.name.isNotEmpty()) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = if (highlight) "已复制" else "复制",
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.height(1.dp))
    }
}
