package com.liyue.mctoolbox.feature.seeddebug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedGenScreen(
    onBack: (() -> Unit)? = null,
    viewModel: SeedGenViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("里悦 · 种子地图自检") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "本页调用 NDK 编译的 cubiomes，直接计算真实群系数据，不使用任何模拟数据。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                SeedInputCard(
                    state = state,
                    onSeedChange = viewModel::onSeedTextChange,
                    onVersionChange = viewModel::onVersionChange,
                    onDimensionChange = viewModel::onDimensionChange,
                    onRun = viewModel::runSelfCheck,
                )
            }
            state.error?.let { message ->
                item {
                    Text(
                        text = "自检失败：$message",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.grid?.let { grid ->
                item {
                    GridSummaryCard(
                        state = state,
                        cellCount = grid.ids.size,
                        cellBlocks = grid.cellBlocks,
                    )
                }
            }
            if (state.probes.isNotEmpty()) {
                item {
                    Text(
                        text = "版本支持情况（当前维度）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.probes, key = { it.version.name }) { probe ->
                    ProbeRow(probe)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedInputCard(
    state: SeedGenUiState,
    onSeedChange: (String) -> Unit,
    onVersionChange: (McVersion) -> Unit,
    onDimensionChange: (Dimension) -> Unit,
    onRun: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.seedText,
                onValueChange = onSeedChange,
                label = { Text("种子（Java 版：整数或任意文本）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = state.version.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("游戏版本") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    McVersion.entries.forEach { version ->
                        DropdownMenuItem(
                            text = { Text(version.label) },
                            onClick = {
                                onVersionChange(version)
                                expanded = false
                            },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dimension.entries.forEach { dimension ->
                    FilterChip(
                        selected = state.dimension == dimension,
                        onClick = { onDimensionChange(dimension) },
                        label = { Text(dimension.label) },
                    )
                }
            }

            Button(
                onClick = onRun,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.running) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (state.running) "计算中…" else "运行 JNI 自检")
            }

            state.parsedSeed?.let { seed ->
                Text(
                    text = "解析后种子：$seed",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun GridSummaryCard(
    state: SeedGenUiState,
    cellCount: Int,
    cellBlocks: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("群系抽样（${SeedGenViewModel.GRID_SIZE} × ${SeedGenViewModel.GRID_SIZE} 格）", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "$cellCount 格，1 格 = $cellBlocks 方块，耗时 ${state.gridMillis} ms",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            state.gridSummary.forEach { (name, count) ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "%.0f%%".format(count * 100.0 / cellCount),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(probe: VersionProbe) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = probe.version.label,
            modifier = Modifier.width(80.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = if (probe.supported) "${probe.biomeName} (${probe.biomeId})" else "不支持",
            modifier = Modifier.weight(1f),
            color = if (probe.supported) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${probe.millis} ms",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
