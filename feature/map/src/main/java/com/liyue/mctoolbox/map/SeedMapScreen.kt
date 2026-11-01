package com.liyue.mctoolbox.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedMapScreen(
    onBack: (() -> Unit)? = null,
    onOpenDebug: () -> Unit = {},
    viewModel: SeedMapViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLegend by remember { mutableStateOf(false) }
    var showStructureFilters by remember { mutableStateOf(false) }
    var showStructureList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("种子地图") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDebug) {
                        Icon(Icons.Filled.Info, contentDescription = "自检")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SeedControls(
                state = state,
                onSeedChange = viewModel::onSeedTextChange,
                onApply = viewModel::applySeed,
                onVersionChange = viewModel::setVersion,
                onDimensionChange = viewModel::setDimension,
            )
            if (state.layersClipped) {
                Text(
                    text = "⚠ 请放大以显示所选结构",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FeatureChips(
                dimension = state.dimension,
                enabled = state.enabledStructures,
                onToggle = viewModel::toggleStructure,
            )
            SeedMapCanvas(
                modifier = Modifier.weight(1f),
                state = state,
                onViewportChanged = viewModel::onViewportChanged,
                onCameraChanged = viewModel::onCameraChanged,
                onTap = viewModel::selectPoint,
                onZoomIn = { viewModel.zoomBy(2f) },
                onZoomOut = { viewModel.zoomBy(0.5f) },
                onReset = { viewModel.onCameraChanged(MapCamera()) },
                onShowLegend = { viewModel.requestLegend(); showLegend = true },
                onShowStructureFilters = { showStructureFilters = true },
                onShowStructureList = { showStructureList = true },
                onJump = viewModel::jumpTo,
                onClearSelection = viewModel::clearSelection,
            )
            MapControls(
                state = state,
                onJump = viewModel::jumpTo,
                onTerrain = viewModel::setTerrainEnabled,
                onGrid = viewModel::setGridEnabled,
                onHighlight = { viewModel.requestLegend(); showLegend = true },
            )
        }
    }

    if (showLegend && state.legend.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showLegend = false }) {
            LegendSheet(
                legend = state.legend,
                renderMillis = state.renderMillis,
                rendering = state.rendering,
                highlightId = state.highlightBiomeId,
                onHighlight = viewModel::setHighlightBiome,
            )
        }
    }

    if (showStructureFilters) {
        ModalBottomSheet(onDismissRequest = { showStructureFilters = false }) {
            StructureFilterSheet(
                dimension = state.dimension,
                enabled = state.enabledStructures,
                onToggle = viewModel::toggleStructure,
            )
        }
    }

    if (showStructureList) {
        ModalBottomSheet(onDismissRequest = { showStructureList = false }) {
            StructureListSheet(
                state = state,
                onJump = { x, z ->
                    viewModel.jumpTo(x.toDouble(), z.toDouble())
                    showStructureList = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeedControls(
    state: MapUiState,
    onSeedChange: (String) -> Unit,
    onApply: () -> Unit,
    onVersionChange: (GameVersion) -> Unit,
    onDimensionChange: (Dimension) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.seedText,
                    onValueChange = onSeedChange,
                    label = { Text("种子") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApply) { Text("生成") }
            }

            var versionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = versionExpanded,
                onExpandedChange = { versionExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.gameVersion.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("版本") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(versionExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = versionExpanded,
                    onDismissRequest = { versionExpanded = false },
                ) {
                    GameVersions.all.forEach { version ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(version.label)
                                    if (version.isAlias) {
                                        Text(
                                            text = "群系算法同 ${version.engineLabel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onVersionChange(version)
                                versionExpanded = false
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

            Text(
                text = "解析后种子：${state.seed}" +
                    if (state.renderMillis > 0) " · 渲染 ${state.renderMillis} ms" else "",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SeedMapCanvas(
    modifier: Modifier = Modifier,
    state: MapUiState,
    onViewportChanged: (Int, Int) -> Unit,
    onCameraChanged: (MapCamera) -> Unit,
    onTap: (Int, Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onShowLegend: () -> Unit,
    onShowStructureFilters: () -> Unit,
    onShowStructureList: () -> Unit,
    onJump: (Double, Double) -> Unit,
    onClearSelection: () -> Unit,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var flingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val latestCamera = androidx.compose.runtime.rememberUpdatedState(state.camera)
    val latestVersion = androidx.compose.runtime.rememberUpdatedState(state.version)
    val cameraProvider = { latestCamera.value }
    val maxBpp = { latestVersion.value.maxBlocksPerPixel }

    fun blockAt(position: Offset, sizePx: IntSize): Pair<Int, Int> {
        val camera = cameraProvider()
        val x = camera.centerX + (position.x - sizePx.width / 2f) * camera.blocksPerPixel
        val z = camera.centerZ + (position.y - sizePx.height / 2f) * camera.blocksPerPixel
        return x.roundToInt() to z.roundToInt()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E1116)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { onViewportChanged(it.width, it.height) }
                .pointerInput(Unit) {
                    var lastTapTime = 0L
                    var lastTapPos = Offset.Zero
                    awaitEachGesture {
                        flingJob?.cancel()
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var lastPos = down.position
                        var totalDrag = 0f
                        var lastTime = down.uptimeMillis

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                lastPos = change.position
                                lastTime = change.uptimeMillis
                                break
                            }
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                val centroid = event.calculateCentroid()
                                val camera = cameraProvider()
                                val bpp = camera.blocksPerPixel
                                var centerX = camera.centerX - pan.x * bpp
                                var centerZ = camera.centerZ - pan.y * bpp
                                val focusX = centerX + (centroid.x - size.width / 2f) * bpp
                                val focusZ = centerZ + (centroid.y - size.height / 2f) * bpp
                                val nextBpp = (bpp / zoom).coerceIn(MapCamera.MIN_BLOCKS_PER_PIXEL, maxBpp())
                                centerX = focusX - (centroid.x - size.width / 2f) * nextBpp
                                centerZ = focusZ - (centroid.y - size.height / 2f) * nextBpp
                                onCameraChanged(MapCamera(centerX, centerZ, nextBpp))
                                event.changes.forEach { it.consume() }
                            }
                            totalDrag += (change.position - lastPos).getDistance()
                            lastPos = change.position
                            lastTime = change.uptimeMillis
                            tracker.addPosition(change.uptimeMillis, change.position)
                        }

                        val duration = lastTime - down.uptimeMillis
                        if (totalDrag < viewConfiguration.touchSlop) {
                            if (duration >= viewConfiguration.longPressTimeoutMillis) {
                                val (bx, bz) = blockAt(lastPos, size)
                                clipboard.setText(AnnotatedString("$bx $bz"))
                            } else {
                                val now = System.currentTimeMillis()
                                val isDoubleTap = now - lastTapTime < 300 &&
                                    (lastPos - lastTapPos).getDistance() < 80.dp.toPx()
                                if (isDoubleTap) {
                                    lastTapTime = 0L
                                    val camera = cameraProvider()
                                    val bpp = camera.blocksPerPixel
                                    val nextBpp = (bpp / 2f).coerceIn(MapCamera.MIN_BLOCKS_PER_PIXEL, maxBpp())
                                    val focusX = camera.centerX +
                                        (lastPos.x - size.width / 2f) * bpp
                                    val focusZ = camera.centerZ +
                                        (lastPos.y - size.height / 2f) * bpp
                                    onCameraChanged(
                                        MapCamera(
                                            centerX = focusX - (lastPos.x - size.width / 2f) * nextBpp,
                                            centerZ = focusZ - (lastPos.y - size.height / 2f) * nextBpp,
                                            blocksPerPixel = nextBpp,
                                        ),
                                    )
                                } else {
                                    lastTapTime = now
                                    lastTapPos = lastPos
                                    val (bx, bz) = blockAt(lastPos, size)
                                    onTap(bx, bz)
                                }
                            }
                        } else {
                            val velocity = tracker.calculateVelocity()
                            val speed = kotlin.math.hypot(velocity.x.toDouble(), velocity.y.toDouble())
                            if (speed > 80.0) {
                                flingJob?.cancel()
                                flingJob = scope.launch {
                                    val animatable = Animatable<Offset, AnimationVector2D>(
                                        Offset.Zero,
                                        Offset.VectorConverter,
                                    )
                                    var last = Offset.Zero
                                    animatable.animateDecay(
                                        Offset(velocity.x, velocity.y),
                                        splineBasedDecay<Offset>(density),
                                    ) {
                                        val delta = value - last
                                        last = value
                                        val camera = cameraProvider()
                                        val bpp = camera.blocksPerPixel
                                        onCameraChanged(
                                            camera.copy(
                                                centerX = camera.centerX - delta.x * bpp,
                                                centerZ = camera.centerZ - delta.y * bpp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
        ) {
            val map = state.map
            if (map != null) {
                val bpp = state.camera.blocksPerPixel
                val scale = map.blocksPerSample / bpp
                val left = ((map.originBlockX - state.camera.centerX) / bpp + size.width / 2.0)
                    .roundToInt()
                val top = ((map.originBlockZ - state.camera.centerZ) / bpp + size.height / 2.0)
                    .roundToInt()
                drawImage(
                    image = map.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(map.width, map.height),
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(
                        (map.width * scale).roundToInt().coerceAtLeast(1),
                        (map.height * scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.Low,
                )
            }
            drawStructureMarkers(state, size)
            drawRulers(state.camera, textMeasurer, state.gridEnabled)
            drawCrosshair(size)
        }

        // coordinate badge
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ) {
            Text(
                text = "X ${state.camera.centerX.roundToInt()}   Z ${state.camera.centerZ.roundToInt()}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        // zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(onClick = onZoomIn) { Icon(Icons.Filled.Add, "放大") }
            FilledIconButton(onClick = onZoomOut) { Text("−", style = MaterialTheme.typography.titleLarge) }
            FilledIconButton(onClick = onReset) { Icon(Icons.Filled.LocationOn, "回到原点") }
            FilledIconButton(onClick = onShowStructureFilters) { Icon(Icons.Filled.List, "结构图层") }
            FilledIconButton(onClick = onShowStructureList) { Icon(Icons.Filled.Search, "结构列表") }
            FilledIconButton(onClick = onShowLegend) { Icon(Icons.Filled.Info, "图例") }
        }

        // scale bar
        ScaleBar(
            blocksPerPixel = state.camera.blocksPerPixel,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )

        if (state.rendering) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("生成中…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        state.selected?.let { point ->
            val nearest = remember(state.structures, point) {
                state.structures.filter { it.type != StructureType.SLIME_CHUNK }.minByOrNull { marker ->
                    val dx = (marker.x - point.blockX).toDouble()
                    val dz = (marker.z - point.blockZ).toDouble()
                    dx * dx + dz * dz
                }
            }
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "X ${point.blockX}  Z ${point.blockZ}",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "${point.biomeName} (${point.biomeId})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }
                    nearest?.let { marker ->
                        val distance = kotlin.math.hypot(
                            (marker.x - point.blockX).toDouble(),
                            (marker.z - point.blockZ).toDouble(),
                        ).roundToInt()
                        Text(
                            text = "最近结构：${marker.type.label} (${marker.x}, ${marker.z}) · $distance 方块",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val targetDimension = when (state.dimension) {
                        Dimension.OVERWORLD -> Dimension.NETHER
                        Dimension.NETHER -> Dimension.OVERWORLD
                        Dimension.END -> null
                    }
                    if (targetDimension != null) {
                        val factor = if (state.dimension == Dimension.OVERWORLD) 8 else 8
                        val targetX = if (state.dimension == Dimension.OVERWORLD) {
                            point.blockX / factor
                        } else {
                            point.blockX * factor
                        }
                        val targetZ = if (state.dimension == Dimension.OVERWORLD) {
                            point.blockZ / factor
                        } else {
                            point.blockZ * factor
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { onJump(targetX.toDouble(), targetZ.toDouble()) },
                        ) {
                            Text("跳转到${targetDimension.label}坐标 ($targetX, $targetZ)")
                        }
                    }
                }
            }
        }

        // 1:xxx readout, bottom-right
        Text(
            text = "1:${state.camera.blocksPerPixel.roundToInt()}",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ScaleBar(blocksPerPixel: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val targetPx = with(density) { 96.dp.toPx() }
    val rawBlocks = blocksPerPixel * targetPx
    val magnitude = 10.0.pow(kotlin.math.floor(log10(rawBlocks.toDouble()))).toFloat()
    val normalized = rawBlocks / magnitude
    val nice = when {
        normalized >= 5f -> 5f
        normalized >= 2f -> 2f
        else -> 1f
    } * magnitude
    val barPx = nice / blocksPerPixel

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(with(density) { barPx.toDp() })
                .height(3.dp)
                .background(Color.White),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${nice.roundToInt()} 方块",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrosshair(size: androidx.compose.ui.geometry.Size) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val arm = 8.dp.toPx()
    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 1.dp.toPx())
    drawLine(Color.White.copy(alpha = 0.7f), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 1.dp.toPx())
}

@Composable
private fun LegendSheet(
    legend: List<LegendEntry>,
    renderMillis: Long,
    rendering: Boolean,
    highlightId: Int?,
    onHighlight: (Int?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("当前视野群系", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (rendering) "生成中…" else "渲染 $renderMillis ms · 点一项可单独高亮",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (highlightId != null) {
                androidx.compose.material3.TextButton(onClick = { onHighlight(null) }) {
                    Text("清除高亮")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        val total = legend.sumOf { it.cells }.coerceAtLeast(1)
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(legend) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onHighlight(if (highlightId == entry.id) null else entry.id)
                        }
                        .background(
                            if (highlightId == entry.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(entry.color, RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(entry.name, modifier = Modifier.weight(1f))
                    Text(
                        text = "%.0f%%".format(entry.cells * 100.0 / total),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRulers(
    camera: MapCamera,
    textMeasurer: TextMeasurer,
    showGrid: Boolean,
) {
    val bpp = camera.blocksPerPixel
    val halfW = size.width / 2f
    val halfH = size.height / 2f
    val step = niceStep(bpp * 96.dp.toPx())
    val line = Color.White.copy(alpha = 0.12f)
    val style = TextStyle(
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )

    var x = kotlin.math.floor((camera.centerX - halfW * bpp) / step) * step
    while (true) {
        val px = ((x - camera.centerX) / bpp + halfW).toFloat()
        if (px > size.width) break
        if (px >= 0f) {
            if (showGrid) {
                drawLine(line, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f)
            }
            val label = textMeasurer.measure(AnnotatedString(x.toLong().toString()), style)
            drawText(label, topLeft = Offset(px + 3f, 2f))
        }
        x += step
    }

    var z = kotlin.math.floor((camera.centerZ - halfH * bpp) / step) * step
    while (true) {
        val py = ((z - camera.centerZ) / bpp + halfH).toFloat()
        if (py > size.height) break
        if (py >= 0f) {
            if (showGrid) {
                drawLine(line, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f)
            }
            val label = textMeasurer.measure(AnnotatedString(z.toLong().toString()), style)
            drawText(label, topLeft = Offset(2f, py + 2f))
        }
        z += step
    }
}

private fun niceStep(targetBlocks: Float): Float {
    val magnitude = 10.0.pow(kotlin.math.floor(log10(targetBlocks.toDouble()))).toFloat()
    val normalized = targetBlocks / magnitude
    val factor = when {
        normalized >= 5f -> 5f
        normalized >= 2f -> 2f
        else -> 1f
    }
    return factor * magnitude
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStructureMarkers(
    state: MapUiState,
    size: androidx.compose.ui.geometry.Size,
) {
    if (state.structures.isEmpty()) return
    val bpp = state.camera.blocksPerPixel
    val radius = 4.5.dp.toPx()
    state.structures.forEach { marker ->
        val px = ((marker.x - state.camera.centerX) / bpp + size.width / 2f).toFloat()
        val py = ((marker.z - state.camera.centerZ) / bpp + size.height / 2f).toFloat()
        if (px < -12f || py < -12f || px > size.width + 12f || py > size.height + 12f) return@forEach
        val center = Offset(px, py)
        if (marker.type == StructureType.SLIME_CHUNK) {
            if (bpp <= 64f) {
                drawCircle(marker.type.color.copy(alpha = 0.55f), 2.5.dp.toPx(), center)
            }
        } else {
            drawCircle(Color.White.copy(alpha = 0.92f), radius + 1.8f, center)
            drawCircle(marker.type.color, radius, center)
        }
    }

    state.spawnPoint?.let { spawn ->
        val px = ((spawn.x - state.camera.centerX) / bpp + size.width / 2f).toFloat()
        val py = ((spawn.z - state.camera.centerZ) / bpp + size.height / 2f).toFloat()
        if (px >= -14f && py >= -14f && px <= size.width + 14f && py <= size.height + 14f) {
            val center = Offset(px, py)
            drawCircle(Color.White, 7.dp.toPx(), center)
            drawCircle(Color(0xFF1B6FE0), 5.dp.toPx(), center)
            drawCircle(Color.White, 1.5.dp.toPx(), center)
        }
    }
}

@Composable
private fun StructureFilterSheet(
    dimension: Dimension,
    enabled: Set<StructureType>,
    onToggle: (StructureType) -> Unit,
) {
    val types = StructureType.forDimension(dimension)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("结构图层", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "当前维度：${dimension.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(types) { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(type) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(type.color, RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(type.label, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Checkbox(
                        checked = type in enabled,
                        onCheckedChange = { onToggle(type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StructureListSheet(
    state: MapUiState,
    onJump: (Int, Int) -> Unit,
) {
    val sorted = remember(state.structures, state.camera) {
        state.structures
            .map { marker ->
                marker to kotlin.math.hypot(
                    (marker.x - state.camera.centerX),
                    (marker.z - state.camera.centerZ),
                )
            }
            .sortedBy { it.second }
            .take(300)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("结构列表", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (state.structuresLoading) "搜索中…" else "${state.structures.size} 个 · 按距离排序",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        if (sorted.isEmpty()) {
            Text(
                text = "当前范围没有找到结构。放大地图或打开更多图层试试。",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(sorted) { (marker, distance) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(marker.x, marker.z) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(marker.type.color, RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(marker.type.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "X ${marker.x}  Z ${marker.z}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            text = "${distance.roundToInt()} 方块",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureChips(
    dimension: Dimension,
    enabled: Set<StructureType>,
    onToggle: (StructureType) -> Unit,
) {
    val types = StructureType.forDimension(dimension)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(types) { type ->
            FilterChip(
                selected = type in enabled,
                onClick = { onToggle(type) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(type.color, RoundedCornerShape(50)),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(type.label, style = MaterialTheme.typography.labelMedium)
                    }
                },
            )
        }
    }
}

@Composable
private fun MapControls(
    state: MapUiState,
    onJump: (Double, Double) -> Unit,
    onTerrain: (Boolean) -> Unit,
    onGrid: (Boolean) -> Unit,
    onHighlight: () -> Unit,
) {
    var xText by rememberSaveable { mutableStateOf("") }
    var zText by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onHighlight,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = state.highlightBiomeId?.let { id ->
                            state.legend.firstOrNull { it.id == id }?.name?.let { "高亮：$it" }
                                ?: "已高亮"
                        } ?: "高亮群系",
                        maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.terrainEnabled,
                        onCheckedChange = onTerrain,
                    )
                    Text("地形", style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.gridEnabled,
                        onCheckedChange = onGrid,
                    )
                    Text("网格", style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = xText,
                    onValueChange = { xText = it },
                    label = { Text("X") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = zText,
                    onValueChange = { zText = it },
                    label = { Text("Z") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val x = xText.trim().toDoubleOrNull()
                        val z = zText.trim().toDoubleOrNull()
                        if (x != null && z != null) onJump(x, z)
                    },
                ) { Text("Go") }
            }
        }
    }
}
