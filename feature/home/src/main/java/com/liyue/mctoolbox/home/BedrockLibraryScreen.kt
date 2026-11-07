package com.liyue.mctoolbox.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** One downloadable Bedrock build plus its net-disk mirrors. */
data class BedrockRelease(
    val version: String,
    val beta: Boolean,
    val date: String,
    val size: String,
    val wiki: String?,
    val links: List<BedrockLink>,
) {
    val channel: String get() = if (beta) "测试版" else "正式版"
}

data class BedrockLink(val label: String, val url: String) {
    /** Friendly mirror name derived from the URL host. */
    val mirror: String
        get() = when {
            url.contains("drive.uc.cn") -> "UC 网盘"
            url.contains("123pan") -> "123 网盘"
            url.contains("quark.cn") -> "夸克网盘"
            url.contains("guangyapan") -> "光鸭云盘"
            url.contains("pan.baidu.com") -> "百度网盘"
            url.contains("xunlei.com") -> "迅雷网盘"
            url.contains("endyun") -> "端云"
            else -> if (label.isBlank()) "下载" else label
        }
}

/** Parsed asset cache; the catalogue is ~450 KB so it is read once per process. */
private object BedrockCatalogue {
    private var cache: List<BedrockRelease>? = null

    suspend fun load(context: Context): List<BedrockRelease> {
        cache?.let { return it }
        val parsed = withContext(Dispatchers.IO) {
            val text = context.assets.open("bedrock_versions.json")
                .bufferedReader()
                .use { it.readText() }
            val array = JSONArray(text)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val row = array.getJSONObject(index)
                    val linkArray = row.optJSONArray("l") ?: continue
                    val links = ArrayList<BedrockLink>(linkArray.length())
                    for (linkIndex in 0 until linkArray.length()) {
                        val link = linkArray.getJSONObject(linkIndex)
                        links += BedrockLink(link.optString("n"), link.optString("u"))
                    }
                    add(
                        BedrockRelease(
                            version = row.optString("v"),
                            beta = row.optBoolean("b"),
                            date = row.optString("d"),
                            size = row.optString("s"),
                            wiki = row.optString("w").takeIf { it.isNotBlank() },
                            links = links,
                        )
                    )
                }
            }
        }
        cache = parsed
        return parsed
    }
}

/**
 * Bedrock Edition catalogue: every build published by MCAPKS with its net-disk
 * mirrors, so a player can grab the exact build they need and jump straight to
 * the seed map for it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BedrockLibraryScreen(
    onBack: () -> Unit,
    onOpenSeedMap: (String) -> Unit,
) {
    val context = LocalContext.current
    var releases by remember { mutableStateOf<List<BedrockRelease>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var channelFilter by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<BedrockRelease?>(null) }

    LaunchedEffect(Unit) {
        releases = BedrockCatalogue.load(context)
        loading = false
    }

    val visible = remember(releases, query, channelFilter) {
        val needle = query.trim()
        releases.filter { release ->
            (channelFilter == null || release.channel == channelFilter) &&
                (needle.isEmpty() || release.version.contains(needle, ignoreCase = true))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("基岩版版本库", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = if (loading) "读取中…" else "${releases.size} 个版本 · 数据来自 MCAPKS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索版本号，如 1.21.80") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(null to "全部", "正式版" to "正式版", "测试版" to "测试版").forEach { (key, label) ->
                    ChannelPill(
                        label = label,
                        active = channelFilter == key,
                        onClick = { channelFilter = key },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${visible.size} 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.version }) { release ->
                    ReleaseCard(
                        release = release,
                        onOpen = { url -> context.openUrl(url) },
                        onShowAll = { detail = release },
                        onMap = { onOpenSeedMap("基岩 ${release.version}") },
                    )
                }
            }
        }
    }

    detail?.let { release ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("基岩 ${release.version} 下载") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${release.channel} · ${release.date} · ${release.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    release.links.forEach { link ->
                        TextButton(
                            onClick = { context.openUrl(link.url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(link.mirror)
                                Text(
                                    text = link.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    release.wiki?.let { wiki ->
                        TextButton(onClick = { context.openUrl(wiki) }) {
                            Text("版本说明（Minecraft Wiki）")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun ChannelPill(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (active) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseCard(
    release: BedrockRelease,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
    onMap: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = release.version,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (release.beta) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Text(
                                text = release.channel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (release.beta) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                            )
                        }
                    }
                    Text(
                        text = "${release.date} · ${release.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = onMap,
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "用这个版本看种子地图",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                release.links.take(4).forEach { link ->
                    MirrorButton(link.mirror) { onOpen(link.url) }
                }
                if (release.links.size > 4) {
                    MirrorButton("全部 ${release.links.size} 个", onClick = onShowAll)
                }
            }
        }
    }
}

@Composable
private fun MirrorButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
