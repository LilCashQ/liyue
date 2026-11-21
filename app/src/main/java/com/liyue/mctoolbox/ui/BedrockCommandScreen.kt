package com.liyue.mctoolbox.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.liyue.mctoolbox.map.LiYueActionButton
import com.liyue.mctoolbox.map.LiYueSheetHandle
import com.liyue.mctoolbox.map.PillChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基岩版命令速查。数据取自 CHelper（yanceymc.cn，作者 Yancey）的正式版原版数据，
 * 收录命令名、中文说明与语法（部分命令的语法尚未收全）。
 */
private data class CommandEntry(val name: String, val description: String, val syntax: String)

private object CommandTable {
    private var entries: List<CommandEntry> = emptyList()

    suspend fun load(context: android.content.Context): List<CommandEntry> = withContext(Dispatchers.IO) {
        if (entries.isNotEmpty()) return@withContext entries
        val list = ArrayList<CommandEntry>(128)
        runCatching {
            context.assets.open("bedrock_commands.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isEmpty()) return@forEach
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    list.add(
                        CommandEntry(
                            name = parts[0],
                            description = parts[1],
                            syntax = parts.getOrElse(2) { "" },
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
fun BedrockCommandScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var all by remember { mutableStateOf<List<CommandEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var onlyWithSyntax by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<CommandEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { all = CommandTable.load(context) }

    val shown = remember(all, query, onlyWithSyntax) {
        val trimmed = query.trim()
        all.asSequence()
            .filter { !onlyWithSyntax || it.syntax.isNotEmpty() }
            .filter {
                trimmed.isEmpty() ||
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }
            .toList()
    }
    val withSyntax = remember(all) { all.count { it.syntax.isNotEmpty() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基岩版命令速查") },
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
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(0.dp)),
                placeholder = { Text("搜命令或说明，例如 effect、传送") },
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
                shape = RoundedCornerShape(0.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 10.dp),
            ) {
                PillChip(
                    label = "全部 ${all.size}",
                    selected = !onlyWithSyntax,
                    onClick = { onlyWithSyntax = false },
                )
                PillChip(
                    label = "含语法 $withSyntax",
                    selected = onlyWithSyntax,
                    onClick = { onlyWithSyntax = true },
                )
            }

            Surface(
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (all.isEmpty()) "正在读取命令表…" else "没有匹配「$query」的命令",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        itemsIndexed(shown, key = { _, e -> e.name }) { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = 16.dp),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = entry }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (entry.syntax.isNotEmpty()) {
                                        Text(
                                            text = "  含语法",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                text = "数据来自 CHelper（yanceymc.cn）· 基岩版正式版原版",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }

    val entry = selected
    if (entry != null) {
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = sheetState,
            dragHandle = { LiYueSheetHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.syntax.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = entry.syntax,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    LiYueActionButton(
                        text = "复制语法",
                        onClick = {
                            val clip = context.getSystemService(ClipboardManager::class.java)
                            clip?.setPrimaryClip(ClipData.newPlainText(entry.syntax, entry.syntax))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "这条命令的语法还没收进离线数据，可以在 CHelper 网页上查完整分支。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "在 CHelper 打开",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.yanceymc.cn/chelper/"),
                                    ),
                                )
                            }
                        }
                        .padding(vertical = 6.dp),
                )
            }
        }
    }
}
