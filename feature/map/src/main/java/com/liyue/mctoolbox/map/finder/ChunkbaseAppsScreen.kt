package com.liyue.mctoolbox.map.finder

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyue.mctoolbox.map.MapGlyph
import com.liyue.mctoolbox.map.MapIcons

/**
 * Chunkbase's app index, rebuilt for a phone: two columns of cards, grouped the
 * same way the website groups them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChunkbaseAppsScreen(
    onBack: () -> Unit,
    onOpenApp: (ChunkbaseApp) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("全部工具")
                        Text(
                            text = "${ChunkbaseApps.all.size} 个查找器 · 与 Chunkbase 对应",
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
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppGroup.entries.forEach { group ->
                val apps = ChunkbaseApps.byGroup(group)
                if (apps.isEmpty()) return@forEach
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(apps, key = { it.id }) { app ->
                    AppCard(app = app, onClick = { if (app.available) onOpenApp(app) })
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: ChunkbaseApp, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (app.available) 1f else 0.55f)
            .clickable(enabled = app.available, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(app.color.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val glyph = app.structure?.let { MapIcons.structure(it) }
                if (glyph != null) {
                    MapGlyph(glyph, 27.dp, 8.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(app.color, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = app.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (!app.available) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEF6C00).copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "施工中",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFEF6C00),
                    )
                }
            }
        }
    }
}

/** Apps whose search has no structure grid: shown as a plain list header. */
@Composable
internal fun AppUnavailableNote(app: ChunkbaseApp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = app.unavailableReason ?: "该功能尚未接入",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
