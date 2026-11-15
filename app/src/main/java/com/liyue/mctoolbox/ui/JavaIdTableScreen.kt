package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * Java 版方块与物品 ID 速查：中文名、现代字符串 ID、数字 ID 一次看全，
 * 点一行复制字符串 ID。数据取自 MCID（伶宁域）的 1.13+ 扁平化 ID 表。
 */
private data class JavaIdEntry(
    val category: String,
    val id: String,
    val name: String,
    val english: String,
    val numeric: String,
)

private object JavaIdTable {
    private var entries: List<JavaIdEntry> = emptyList()

    suspend fun load(context: android.content.Context): List<JavaIdEntry> = withContext(Dispatchers.IO) {
        if (entries.isNotEmpty()) return@withContext entries
        val list = ArrayList<JavaIdEntry>(2048)
        runCatching {
            context.assets.open("java_ids.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 3) return@forEach
                    list.add(
                        JavaIdEntry(
                            category = parts[0],
                            id = parts[1],
                            name = parts[2],
                            english = parts.getOrElse(3) { "" },
                            numeric = parts.getOrElse(4) { "" },
                        ),
                    )
                }
            }
        }
        entries = list
        list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JavaIdTableScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<JavaIdEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { all = JavaIdTable.load(context) }

    val shown = remember(all, query, category) {
        val trimmed = query.trim()
        all.asSequence()
            .filter { category == null || it.category == category }
            .filter {
                trimmed.isEmpty() ||
                    it.id.contains(trimmed, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.english.contains(trimmed, ignoreCase = true) ||
                    it.numeric == trimmed
            }
            .take(800)
            .toList()
    }
    val blockCount = remember(all) { all.count { it.category == "block" } }
    val itemCount = remember(all) { all.count { it.category == "item" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Java 版 ID 表") },
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
                placeholder = { Text("搜中文名 / 英文 ID / 数字 ID，例如 钻石、stone、1:1") },
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
                item {
                    PillChip(
                        label = "方块 $blockCount",
                        selected = category == "block",
                        onClick = { category = if (category == "block") null else "block" },
                    )
                }
                item {
                    PillChip(
                        label = "物品 $itemCount",
                        selected = category == "item",
                        onClick = { category = if (category == "item") null else "item" },
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
                            JavaIdRow(item, highlight = copied == item.id) {
                                val clip = context.getSystemService(ClipboardManager::class.java)
                                clip?.setPrimaryClip(ClipData.newPlainText(item.id, item.id))
                                copied = item.id
                            }
                        }
                    }
                }
            }
            Text(
                text = "共 ${all.size} 条 · 点一行复制字符串 ID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun JavaIdRow(entry: JavaIdEntry, highlight: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (entry.numeric.isNotEmpty()) {
                    Text(
                        text = "  #${entry.numeric}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = entry.id,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.english.isNotEmpty()) {
                Text(
                    text = entry.english,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = if (highlight) "已复制" else "复制",
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
