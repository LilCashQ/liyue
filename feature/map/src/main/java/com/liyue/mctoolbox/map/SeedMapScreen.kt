package com.liyue.mctoolbox.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
    onOpenFinder: ((seedText: String, versionLabel: String) -> Unit)? = null,
    initialTarget: MapTarget? = null,
    onTargetConsumed: () -> Unit = {},
    viewModel: SeedMapViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(initialTarget) {
        if (initialTarget != null) {
            viewModel.openAt(initialTarget)
            onTargetConsumed()
        }
    }
    var showLegend by remember { mutableStateOf(false) }
    var showStructureFilters by remember { mutableStateOf(false) }
    var showStructureList by remember { mutableStateOf(false) }
    var showOrePicker by remember { mutableStateOf(false) }
    var showSeedSheet by remember { mutableStateOf(false) }
    var showUnderground by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showSeedSheet = true },
                    ) {
                        Text("种子地图", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${state.seedText} · ${state.gameVersion.label} · ${state.dimension.label}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSeedSheet = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "种子与版本")
                    }
                    if (onOpenFinder != null) {
                        IconButton(
                            onClick = { onOpenFinder(state.seedText, state.gameVersion.label) },
                        ) {
                            Icon(Icons.Filled.List, contentDescription = "查找器")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "地图设置")
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
            if (state.layersClipped) {
                Text(
                    text = "⚠ 请放大以显示所选结构",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
            ) {
                FeatureChips(
                    dimension = state.dimension,
                    enabled = state.enabledStructures,
                    edition = state.gameVersion.edition,
                    onToggle = viewModel::toggleStructure,
                )
            }
            SeedMapCanvas(
                modifier = Modifier.weight(1f),
                state = state,
                onViewportChanged = viewModel::onViewportChanged,
                onCameraChanged = viewModel::onCameraChanged,
                onTap = { x, z ->
                    // Measuring takes both taps, so it goes first.
                    if (state.measureMode) {
                        viewModel.measureAt(x, z)
                        return@SeedMapCanvas
                    }
                    if (state.clickMark) viewModel.dropPin(x, z)
                    // A tap that lands on a structure marker opens its layout
                    // instead of dropping a biome probe on top of it. The pick
                    // radius follows the zoom so it stays ~28 screen pixels.
                    val pick = (state.camera.blocksPerPixel * 28f).coerceAtLeast(32f)
                    val hit = state.structures
                        .filter { it.type != StructureType.SLIME_CHUNK }
                        .filter { state.camera.blocksPerPixel <= it.type.maxBlocksPerPixel }
                        .minByOrNull { marker ->
                            val dx = (marker.x - x).toDouble()
                            val dz = (marker.z - z).toDouble()
                            dx * dx + dz * dz
                        }
                        ?.takeIf { marker ->
                            val dx = (marker.x - x).toDouble()
                            val dz = (marker.z - z).toDouble()
                            dx * dx + dz * dz <= pick * pick
                        }
                    if (hit != null) {
                        viewModel.openStructureDetails(hit)
                        showDetails = true
                    } else {
                        viewModel.selectPoint(x, z)
                    }
                },
                onZoomIn = { viewModel.zoomBy(2f) },
                onZoomOut = { viewModel.zoomBy(0.5f) },
                onReset = { viewModel.onCameraChanged(MapCamera()) },
                onShowLegend = { viewModel.requestLegend(); showLegend = true },
                onShowStructureFilters = { showStructureFilters = true },
                onShowStructureList = { showStructureList = true },
                onShowOrePicker = { showOrePicker = true },
                onJump = viewModel::jumpTo,
                onClearSelection = viewModel::clearSelection,
            )
            MapActionBar(
                state = state,
                onOpenSeed = { showSeedSheet = true },
                onOpenLayers = { showStructureFilters = true },
                onOpenOres = { showOrePicker = true },
                onOpenList = { showStructureList = true },
                onOpenLegend = { viewModel.requestLegend(); showLegend = true },
                onOpenUnderground = { viewModel.loadUnderground(); showUnderground = true },
            )
        }
    }

    if (showSeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSeedSheet = false }) {
            SeedSheet(
                state = state,
                onSeedChange = viewModel::onSeedTextChange,
                onApply = {
                    viewModel.applySeed()
                    showSeedSheet = false
                },
                onVersionChange = viewModel::setVersion,
                onDimensionChange = viewModel::setDimension,
            )
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(
                state = state,
                onJump = { x, z ->
                    viewModel.jumpTo(x, z)
                    showSettings = false
                },
                onTerrain = viewModel::setTerrainEnabled,
                onContours = viewModel::setContoursEnabled,
                onGrid = viewModel::setGridEnabled,
                onSlime = viewModel::toggleSlimeChunks,
                onMeasure = viewModel::toggleMeasureMode,
                onChunkReadout = viewModel::setChunkReadout,
                onShowSpawn = viewModel::setShowSpawn,
                onClickMark = viewModel::setClickMark,
                onBiomeY = viewModel::setBiomeY,
                onReset = viewModel::resetSettings,
                onHighlight = { viewModel.requestLegend(); showLegend = true },
                onOpenDebug = onOpenDebug,
            )
        }
    }

    if (showDetails) {
        ModalBottomSheet(
            onDismissRequest = {
                showDetails = false
                viewModel.closeStructureDetails()
            },
        ) {
            StructureDetailsSheet(
                state = state,
                onJump = { x, z ->
                    viewModel.jumpTo(x.toDouble(), z.toDouble())
                    showDetails = false
                },
            )
        }
    }

    if (showUnderground) {
        ModalBottomSheet(onDismissRequest = { showUnderground = false }) {
            UndergroundSheet(
                state = state,
                onYChange = { y ->
                    viewModel.setUndergroundY(y)
                    viewModel.loadUnderground()
                },
                onLayersChange = { layers ->
                    viewModel.setUndergroundLayers(layers)
                    viewModel.loadUnderground()
                },
                onSpanChange = viewModel::setUndergroundSpan,
                onOverlayChange = viewModel::setOverlayMode,
                onRefresh = { viewModel.loadUnderground() },
            )
        }
    }

    if (showLegend && state.legend.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showLegend = false }) {
            LegendSheet(
                legend = state.legend,
                seedText = state.seedText,
                versionLabel = state.gameVersion.label,
                dimensionLabel = state.dimension.label,
                renderMillis = state.renderMillis,
                rendering = state.rendering,
                highlightIds = state.highlightBiomeIds,
                onToggle = viewModel::toggleHighlightBiome,
                onClear = viewModel::clearHighlights,
            )
        }
    }

    if (showStructureFilters) {
        ModalBottomSheet(onDismissRequest = { showStructureFilters = false }) {
            StructureFilterSheet(
                dimension = state.dimension,
                enabled = state.enabledStructures,
                edition = state.gameVersion.edition,
                onToggle = viewModel::toggleStructure,
            )
        }
    }

    if (showOrePicker) {
        ModalBottomSheet(onDismissRequest = { showOrePicker = false }) {
            OrePickerSheet(
                dimension = state.dimension,
                version = state.version,
                selected = state.oreKind,
                scanning = state.oreLoading,
                scannedChunks = state.oreScannedChunks,
                markerCount = state.oreMarkers.size,
                onSelect = { kind ->
                    viewModel.setOreKind(kind)
                    showOrePicker = false
                },
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
private fun SeedSheet(
    state: MapUiState,
    onSeedChange: (String) -> Unit,
    onApply: () -> Unit,
    onVersionChange: (GameVersion) -> Unit,
    onDimensionChange: (Dimension) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("种子与版本", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.seedText,
                onValueChange = onSeedChange,
                label = { Text("种子") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Text),
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
                modifier = Modifier.heightIn(max = 460.dp),
            ) {
                GameVersions.grouped.forEach { (groupLabel, groupVersions) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = groupLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    groupVersions.forEach { version ->
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
    onShowOrePicker: () -> Unit,
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
            .zIndex(0f)
            .clipToBounds()
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
            val bpp = state.camera.blocksPerPixel
            for (tile in state.staleTiles) {
                val scale = tile.key.cellBlocks / bpp
                val left = ((tile.originBlockX - state.camera.centerX) / bpp + size.width / 2.0)
                    .roundToInt()
                val top = ((tile.originBlockZ - state.camera.centerZ) / bpp + size.height / 2.0)
                    .roundToInt()
                drawImage(
                    image = tile.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(tile.sizePx, tile.sizePx),
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(
                        (tile.sizePx * scale).roundToInt().coerceAtLeast(1),
                        (tile.sizePx * scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.None,
                )
            }
            for (tile in state.tiles) {
                val scale = tile.key.cellBlocks / bpp
                val left = ((tile.originBlockX - state.camera.centerX) / bpp + size.width / 2.0)
                    .roundToInt()
                val top = ((tile.originBlockZ - state.camera.centerZ) / bpp + size.height / 2.0)
                    .roundToInt()
                drawImage(
                    image = tile.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(tile.sizePx, tile.sizePx),
                    dstOffset = IntOffset(left, top),
                    dstSize = IntSize(
                        (tile.sizePx * scale).roundToInt().coerceAtLeast(1),
                        (tile.sizePx * scale).roundToInt().coerceAtLeast(1),
                    ),
                    filterQuality = FilterQuality.None,
                )
            }
            state.overlayImage?.let { overlay ->
                if (state.overlayBlocks > 0) {
                    val bpp = state.camera.blocksPerPixel
                    val blockPerPixel = state.overlayBlocks.toDouble() / overlay.width
                    val left = (
                        (state.overlayOriginX - state.camera.centerX) / bpp +
                            size.width / 2.0
                        ).roundToInt()
                    val top = (
                        (state.overlayOriginZ - state.camera.centerZ) / bpp +
                            size.height / 2.0
                        ).roundToInt()
                    val edge = (overlay.width * blockPerPixel / bpp).roundToInt()
                        .coerceAtLeast(1)
                    drawImage(
                        image = overlay,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(overlay.width, overlay.height),
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(edge, edge),
                        filterQuality = FilterQuality.None,
                    )
                }
            }
            if (StructureType.SLIME_CHUNK in state.enabledStructures) {
                drawSlimeChunks(state, size)
            }
            drawMeasureLine(state, size, textMeasurer)
            drawPin(state, size)
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
            val centerBlockX = state.camera.centerX.roundToInt()
            val centerBlockZ = state.camera.centerZ.roundToInt()
            Text(
                text = if (state.chunkReadout) {
                    "X $centerBlockX   Z $centerBlockZ\n区块 " +
                        "${Math.floorDiv(centerBlockX, 16)} " +
                        "${Math.floorDiv(centerBlockZ, 16)}"
                } else {
                    "X $centerBlockX   Z $centerBlockZ"
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        // zoom controls: only the two thumbs need while panning; everything
        // else lives in the bottom action bar so the map keeps the height.
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(onClick = onZoomIn) { Icon(Icons.Filled.Add, "放大") }
            FilledIconButton(onClick = onZoomOut) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            FilledIconButton(onClick = onReset) {
                Icon(Icons.Filled.LocationOn, "回到原点")
            }
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
                                text = buildString {
                                    append(point.biomeName)
                                    append(" (")
                                    append(point.biomeId)
                                    append(')')
                                    point.surfaceY?.let { y ->
                                        append(" · 地表 Y ")
                                        append(y)
                                    }
                                },
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
    seedText: String,
    versionLabel: String,
    dimensionLabel: String,
    renderMillis: Long,
    rendering: Boolean,
    highlightIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var legendQuery by remember { mutableStateOf("") }
    val legendContext = LocalContext.current
    val legendScope = rememberCoroutineScope()
    val legendLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            legendScope.launch {
                MapExport.writeText(
                    legendContext,
                    uri,
                    MapExport.biomesCsv(legend, seedText, versionLabel, dimensionLabel),
                )
            }
        }
    }
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
                    text = if (rendering) {
                        "生成中…"
                    } else {
                        "渲染 $renderMillis ms · 可多选，选中 ${highlightIds.size} 个群系"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { legendLauncher.launch("里悦-群系-$seedText.csv") },
            ) {
                Text("导出")
            }
            if (highlightIds.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = onClear) {
                    Text("清除高亮")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = legendQuery,
            onValueChange = { legendQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            singleLine = true,
            placeholder = { Text("搜索群系名", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (legendQuery.isNotEmpty()) {
                    IconButton(onClick = { legendQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        val shown = legend.filter {
            legendQuery.isBlank() || it.name.contains(legendQuery.trim(), ignoreCase = true)
        }
        val total = legend.sumOf { it.cells }.coerceAtLeast(1)
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(shown) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(entry.id) }
                        .background(
                            if (entry.id in highlightIds) {
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
                    Text(
                        text = entry.name,
                        modifier = Modifier.weight(1f),
                        color = if (entry.id in highlightIds) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
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
            val label = textMeasurer.measure(AnnotatedString(z.toLong().toString()), style)
            drawText(label, topLeft = Offset(2f, py + 2f))
        }
        z += step
    }

    // Chunk borders, like Chunkbase's "chunk grid" switch: 16 blocks apart and
    // only once they are far enough apart to read as a grid.
    val chunkPx = 16f / bpp
    if (showGrid && chunkPx >= 2.5f) {
        val grid = Color.White.copy(alpha = 0.30f)
        var block = kotlin.math.floor((camera.centerX - halfW * bpp) / 16.0) * 16.0
        while (true) {
            val px = ((block - camera.centerX) / bpp + halfW).toFloat()
            if (px > size.width) break
            if (px >= 0f) drawLine(grid, Offset(px, 0f), Offset(px, size.height), strokeWidth = 1f)
            block += 16.0
        }
        block = kotlin.math.floor((camera.centerZ - halfH * bpp) / 16.0) * 16.0
        while (true) {
            val py = ((block - camera.centerZ) / bpp + halfH).toFloat()
            if (py > size.height) break
            if (py >= 0f) drawLine(grid, Offset(0f, py), Offset(size.width, py), strokeWidth = 1f)
            block += 16.0
        }
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
    if (state.structures.isEmpty() && state.oreMarkers.isEmpty()) return
    val bpp = state.camera.blocksPerPixel
    // Ores sit under the structure pins so the pins stay readable.
    state.oreMarkers.forEach { ore ->
        val px = ((ore.x - state.camera.centerX) / bpp + size.width / 2f).toFloat()
        val py = ((ore.z - state.camera.centerZ) / bpp + size.height / 2f).toFloat()
        if (px < -4f || py < -4f || px > size.width + 4f || py > size.height + 4f) return@forEach
        drawCircle(oreColor(ore.kind).copy(alpha = 0.85f), 1.6.dp.toPx(), Offset(px, py))
    }
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

    if (!state.showSpawn) return
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

/** Stable per-ore colour so the same ore looks the same on every screen. */
internal fun oreColor(kind: com.liyue.mctoolbox.seedgen.OreKind): Color = when (kind) {
    com.liyue.mctoolbox.seedgen.OreKind.DIAMOND -> Color(0xFF4DD0E1)
    com.liyue.mctoolbox.seedgen.OreKind.IRON -> Color(0xFFD7CCC8)
    com.liyue.mctoolbox.seedgen.OreKind.IRON_VEIN -> Color(0xFFFFAB91)
    com.liyue.mctoolbox.seedgen.OreKind.COPPER -> Color(0xFFFF8A65)
    com.liyue.mctoolbox.seedgen.OreKind.COPPER_VEIN -> Color(0xFFBCAAA4)
    com.liyue.mctoolbox.seedgen.OreKind.GOLD -> Color(0xFFFFD54F)
    com.liyue.mctoolbox.seedgen.OreKind.REDSTONE -> Color(0xFFE53935)
    com.liyue.mctoolbox.seedgen.OreKind.LAPIS -> Color(0xFF3949AB)
    com.liyue.mctoolbox.seedgen.OreKind.COAL -> Color(0xFF546E7A)
    com.liyue.mctoolbox.seedgen.OreKind.DEBRIS -> Color(0xFF8D6E63)
    com.liyue.mctoolbox.seedgen.OreKind.EMERALD -> Color(0xFF2E7D32)
    com.liyue.mctoolbox.seedgen.OreKind.QUARTZ -> Color(0xFFF5F5F5)
    com.liyue.mctoolbox.seedgen.OreKind.NETHER_GOLD -> Color(0xFFFFB300)
}

/**
 * Slime chunks as Chunkbase draws them: one translucent 16x16 square per slimy
 * chunk, computed straight from the seed so it never waits on the structure
 * pass. Below a couple of pixels per chunk the layer is skipped instead of
 * painting the screen into a solid wash.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSlimeChunks(
    state: MapUiState,
    size: androidx.compose.ui.geometry.Size,
) {
    val bpp = state.camera.blocksPerPixel
    val chunkPx = 16f / bpp
    if (chunkPx < 1.2f) return
    val halfW = size.width / 2f * bpp
    val halfH = size.height / 2f * bpp
    val cx0 = kotlin.math.floor((state.camera.centerX - halfW) / 16.0).toInt()
    val cx1 = kotlin.math.floor((state.camera.centerX + halfW) / 16.0).toInt()
    val cz0 = kotlin.math.floor((state.camera.centerZ - halfH) / 16.0).toInt()
    val cz1 = kotlin.math.floor((state.camera.centerZ + halfH) / 16.0).toInt()
    val cells = (cx1 - cx0 + 1).toLong() * (cz1 - cz0 + 1)
    if (cells > 400000) return
    val fill = StructureType.SLIME_CHUNK.color.copy(alpha = 0.42f)
    val edge = StructureType.SLIME_CHUNK.color.copy(alpha = 0.75f)
    for (cz in cz0..cz1) {
        for (cx in cx0..cx1) {
            if (!slimeChunkAt(state.seed, cx, cz)) continue
            val left = ((cx * 16 - state.camera.centerX) / bpp + size.width / 2f).toFloat()
            val top = ((cz * 16 - state.camera.centerZ) / bpp + size.height / 2f).toFloat()
            val side = chunkPx
            drawRect(fill, topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(side, side))
            if (chunkPx >= 5f) {
                drawRect(edge, topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(side, side),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
            }
        }
    }
}

/** Dashed ruler between the two measurement taps, with the distance label. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMeasureLine(
    state: MapUiState,
    size: androidx.compose.ui.geometry.Size,
    textMeasurer: TextMeasurer,
) {
    val sx = state.measureStartX ?: return
    val sz = state.measureStartZ ?: return
    val ex = state.measureEndX ?: return
    val ez = state.measureEndZ ?: return
    val bpp = state.camera.blocksPerPixel
    val start = androidx.compose.ui.geometry.Offset(
        ((sx - state.camera.centerX) / bpp + size.width / 2f).toFloat(),
        ((sz - state.camera.centerZ) / bpp + size.height / 2f).toFloat(),
    )
    val end = androidx.compose.ui.geometry.Offset(
        ((ex - state.camera.centerX) / bpp + size.width / 2f).toFloat(),
        ((ez - state.camera.centerZ) / bpp + size.height / 2f).toFloat(),
    )
    val accent = Color(0xFFFFC857)
    drawLine(
        color = accent,
        start = start,
        end = end,
        strokeWidth = 2.dp.toPx(),
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
            floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
        ),
    )
    drawCircle(accent, 4.dp.toPx(), start)
    drawCircle(accent, 4.dp.toPx(), end)
    val blocks = kotlin.math.hypot(ex - sx, ez - sz)
    val label = "${blocks.roundToInt()} 方块 · ${(blocks / 16.0).roundToInt()} 区块"
    val layout = textMeasurer.measure(
        text = androidx.compose.ui.text.AnnotatedString(label),
        style = androidx.compose.ui.text.TextStyle(
            color = Color.Black,
            fontSize = 11.sp,
        ),
    )
    val mid = androidx.compose.ui.geometry.Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
    val boxTopLeft = androidx.compose.ui.geometry.Offset(
        mid.x - layout.size.width / 2f - 6.dp.toPx(),
        mid.y - layout.size.height / 2f - 4.dp.toPx(),
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.9f),
        topLeft = boxTopLeft,
        size = androidx.compose.ui.geometry.Size(
            layout.size.width + 12.dp.toPx(),
            layout.size.height + 8.dp.toPx(),
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = androidx.compose.ui.geometry.Offset(
            boxTopLeft.x + 6.dp.toPx(),
            boxTopLeft.y + 4.dp.toPx(),
        ),
    )
}

/** Pin dropped by tapping the map, so a spot stays marked while panning. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPin(
    state: MapUiState,
    size: androidx.compose.ui.geometry.Size,
) {
    val px = state.pinX ?: return
    val pz = state.pinZ ?: return
    val bpp = state.camera.blocksPerPixel
    val center = androidx.compose.ui.geometry.Offset(
        ((px - state.camera.centerX) / bpp + size.width / 2f).toFloat(),
        ((pz - state.camera.centerZ) / bpp + size.height / 2f).toFloat(),
    )
    val accent = Color(0xFFFF4D6D)
    drawLine(
        accent,
        center - androidx.compose.ui.geometry.Offset(9.dp.toPx(), 0f),
        center + androidx.compose.ui.geometry.Offset(9.dp.toPx(), 0f),
        strokeWidth = 2.dp.toPx(),
    )
    drawLine(
        accent,
        center - androidx.compose.ui.geometry.Offset(0f, 9.dp.toPx()),
        center + androidx.compose.ui.geometry.Offset(0f, 9.dp.toPx()),
        strokeWidth = 2.dp.toPx(),
    )
    drawCircle(accent, 3.dp.toPx(), center)
}

@Composable
private fun OrePickerSheet(
    dimension: Dimension,
    version: McVersion,
    selected: com.liyue.mctoolbox.seedgen.OreKind?,
    scanning: Boolean,
    scannedChunks: Int,
    markerCount: Int,
    onSelect: (com.liyue.mctoolbox.seedgen.OreKind?) -> Unit,
) {
    val kinds = com.liyue.mctoolbox.seedgen.OreKind.entries.filter { kind ->
        if (kind.dimension != dimension) return@filter false
        val min = kind.minVersion
        min == null || version >= min
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("矿石图层", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (selected == null) {
                "选一种矿石，在当前视野内标出矿脉位置（逐区块精确计算）。"
            } else if (scanning) {
                "扫描中…"
            } else {
                "已扫描 $scannedChunks 个区块 · $markerCount 个矿石方块"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (kinds.isEmpty()) {
            Text("当前维度没有可扫描的矿石。", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("不显示矿石", modifier = Modifier.weight(1f))
                    if (selected == null) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
            items(kinds) { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(kind) }
                        .background(
                            if (selected == kind) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(oreColor(kind), RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(kind.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "最佳 ${kind.bestY} · ${kind.rangeLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StructureFilterSheet(
    dimension: Dimension,
    enabled: Set<StructureType>,
    edition: Edition,
    onToggle: (StructureType) -> Unit,
) {
    val types = StructureType.forDimension(dimension)
        .filter { !edition.isBedrock || it in StructureType.BEDROCK_SUPPORTED }
    var filterQuery by remember { mutableStateOf("") }
    val shownTypes = types.filter {
        filterQuery.isBlank() || it.label.contains(filterQuery.trim(), ignoreCase = true)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text("结构图层", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "当前维度：${dimension.label} · 已开 ${types.count { it in enabled }} / ${types.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            singleLine = true,
            placeholder = { Text("搜索结构名", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { filterQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
            items(shownTypes) { type ->
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
    // Which structure types the list is showing. Everything is visible until
    // the user starts narrowing it down.
    var hiddenTypes by remember(state.structures) { mutableStateOf(emptySet<StructureType>()) }
    var listQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                MapExport.writeText(context, uri, MapExport.structuresCsv(state))
            }
        }
    }
    val types = remember(state.structures) {
        state.structures
            .map { it.type }
            .distinct()
            .sortedBy { it.ordinal }
    }
    val query = listQuery.trim()
    val visible = state.structures.filter { marker ->
        marker.type !in hiddenTypes && (
            query.isEmpty() ||
                marker.type.label.contains(query, ignoreCase = true) ||
                "${marker.x} ${marker.z}".contains(query) ||
                "${marker.x},${marker.z}".contains(query)
            )
    }
    val sorted = remember(visible, state.camera) {
        visible
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("结构列表", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (state.structuresLoading) {
                        "搜索中…"
                    } else {
                        "${visible.size} / ${state.structures.size} 个 · 按距离排序"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { csvLauncher.launch("里悦-结构-${state.seedText}.csv") },
            ) {
                Text("导出")
            }
            if (hiddenTypes.isNotEmpty()) {
                androidx.compose.material3.TextButton(onClick = { hiddenTypes = emptySet() }) {
                    Text("全部显示")
                }
            }
        }
        if (types.size > 1) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(types) { type ->
                    val on = type !in hiddenTypes
                    FilterChip(
                        selected = on,
                        onClick = {
                            hiddenTypes = if (on) hiddenTypes + type else hiddenTypes - type
                        },
                        label = {
                            Text("${type.label} ${state.structures.count { it.type == type }}")
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (on) type.color else type.color.copy(alpha = 0.3f),
                                        RoundedCornerShape(50),
                                    ),
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = listQuery,
            onValueChange = { listQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            singleLine = true,
            placeholder = { Text("搜索结构名或坐标", style = MaterialTheme.typography.bodySmall) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (listQuery.isNotEmpty()) {
                    IconButton(onClick = { listQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "清空")
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        if (sorted.isEmpty()) {
            Text(
                text = if (query.isEmpty()) {
                    "当前范围没有找到结构。放大地图或打开更多图层试试。"
                } else {
                    "没有匹配「$query」的结构。"
                },
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
    edition: Edition,
    onToggle: (StructureType) -> Unit,
) {
    val types = StructureType.forDimension(dimension)
        .filter { !edition.isBedrock || it in StructureType.BEDROCK_SUPPORTED }
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
private fun MapActionBar(
    state: MapUiState,
    onOpenSeed: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenOres: () -> Unit,
    onOpenList: () -> Unit,
    onOpenLegend: () -> Unit,
    onOpenUnderground: () -> Unit,
) {
    Surface(tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionBarItem("种子", enabledHighlight = false, onClick = onOpenSeed) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            ActionBarItem(
                label = "图层",
                enabledHighlight = state.enabledStructures.isNotEmpty(),
                onClick = onOpenLayers,
            ) {
                Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            ActionBarItem(
                label = "矿石",
                enabledHighlight = state.oreKind != null,
                onClick = onOpenOres,
                enabled = !state.gameVersion.edition.isBedrock,
            ) {
                Text("◆", style = MaterialTheme.typography.titleMedium)
            }
            ActionBarItem(
                label = "地下",
                enabledHighlight = false,
                onClick = onOpenUnderground,
                enabled = !state.gameVersion.edition.isBedrock,
            ) {
                Text("▦", style = MaterialTheme.typography.titleMedium)
            }
            ActionBarItem("列表", enabledHighlight = false, onClick = onOpenList) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            ActionBarItem("图例", enabledHighlight = state.highlightBiomeIds.isNotEmpty(), onClick = onOpenLegend) {
                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ActionBarItem(
    label: String,
    enabledHighlight: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        enabledHighlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint,
        ) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun SettingsSheet(
    state: MapUiState,
    onJump: (Double, Double) -> Unit,
    onTerrain: (Boolean) -> Unit,
    onContours: (Boolean) -> Unit,
    onGrid: (Boolean) -> Unit,
    onSlime: () -> Unit,
    onMeasure: () -> Unit,
    onChunkReadout: (Boolean) -> Unit,
    onShowSpawn: (Boolean) -> Unit,
    onClickMark: (Boolean) -> Unit,
    onBiomeY: (Int) -> Unit,
    onReset: () -> Unit,
    onHighlight: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    var xText by rememberSaveable { mutableStateOf("") }
    var zText by rememberSaveable { mutableStateOf("") }
    var customY by rememberSaveable { mutableStateOf(state.biomeY) }
    var exporting by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val exportContext = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val pngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val image = pendingImage
        if (uri != null && image != null) {
            exportScope.launch {
                MapExport.writeImage(exportContext, uri, image)
            }
        }
        pendingImage = null
        exporting = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "地图设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) { Text("恢复默认") }
        }

        Text("地图上", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsToggle("区块网格", state.gridEnabled) { onGrid(!state.gridEnabled) }
            SettingsToggle("史莱姆区块", StructureType.SLIME_CHUNK in state.enabledStructures, onClick = onSlime)
            SettingsToggle("地形阴影", state.terrainEnabled) { onTerrain(!state.terrainEnabled) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsToggle("等高线", state.contoursEnabled) { onContours(!state.contoursEnabled) }
            SettingsToggle("测量距离", state.measureMode, onClick = onMeasure)
        }

        HorizontalDivider()
        Text("读懂地图", style = MaterialTheme.typography.labelLarge)
        SettingsSwitch("读数中的区块", state.chunkReadout, onChunkReadout)
        SettingsSwitch("点击时标记位置", state.clickMark, onClickMark)
        SettingsSwitch("出生点", state.showSpawn, onShowSpawn)

        HorizontalDivider()
        Text("该高度的生物群系", style = MaterialTheme.typography.labelLarge)
        Text(
            "洞穴会改变地表以下的群系",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsToggle("地表 Y 63", state.biomeY == 63) { onBiomeY(63) }
            SettingsToggle("地下 Y 0", state.biomeY == 0) { onBiomeY(0) }
            SettingsToggle("底部 Y -52", state.biomeY == -52) { onBiomeY(-52) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自定义 Y ${state.biomeY}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(96.dp),
            )
            Slider(
                value = state.biomeY.toFloat(),
                onValueChange = { customY = it.roundToInt() },
                onValueChangeFinished = { onBiomeY(customY) },
                valueRange = -64f..320f,
                modifier = Modifier.weight(1f),
            )
        }

        HorizontalDivider()
        OutlinedButton(onClick = onHighlight, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = when {
                    state.highlightBiomeIds.isEmpty() -> "高亮群系"
                    state.highlightBiomeIds.size == 1 -> state.legend
                        .firstOrNull { it.id == state.highlightBiomeIds.first() }
                        ?.name?.let { "高亮：$it" } ?: "已高亮 1 个"
                    else -> "已高亮 ${state.highlightBiomeIds.size} 个群系"
                },
                maxLines = 1,
            )
        }

        Text("导出", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = {
                if (exporting) return@OutlinedButton
                exporting = true
                exportScope.launch {
                    pendingImage = MapExport.renderImage(
                        state = state,
                        viewportWidth = state.viewportWidthPx,
                        viewportHeight = state.viewportHeightPx,
                        targetWidth = (state.viewportWidthPx * 2).coerceIn(720, 2048),
                    )
                    val image = pendingImage
                    if (image == null) {
                        exporting = false
                    } else {
                        pngLauncher.launch("里悦-地图-${state.seedText}.png")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (exporting) "正在渲染…" else "导出当前地图 PNG（含结构点）")
        }
        Text(
            "按当前视野和缩放导出，结构点、等高线、阴影都在图里；存到你选的位置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("坐标跳转", style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = xText,
                onValueChange = { xText = it },
                label = { Text("X") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = zText,
                onValueChange = { zText = it },
                label = { Text("Z") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                ),
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

        TextButton(onClick = onOpenDebug) { Text("打开自检面板") }
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Layout of one structure start: the variant fields cubiomes can replay, the
 * generated piece list and the loot tables attached to it. Anything the
 * bridge marked in `limits` is spelled out instead of silently dropped.
 */
@Composable
private fun StructureDetailsSheet(
    state: MapUiState,
    onJump: (Int, Int) -> Unit,
) {
    val target = state.detailsTarget
    val details = state.details
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (target == null) {
            Text("没有选中的结构", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(
            text = target.type.label,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "X ${target.x}   Z ${target.z}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onJump(target.x, target.z) }) { Text("居中") }
        }

        if (state.detailsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@Column
        }

        if (details == null || !details.supported) {
            Text(
                text = "这个结构在当前版本下没有可用的布局数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val variant = details.variant
        if (variant != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DetailRow("朝向", "${variant.rotation}°")
                    state.detailsBiomeName?.let { DetailRow("群系", it) }
                    if (variant.size > 0) DetailRow("规模", "${variant.size}")
                    if (variant.sizeX > 0) {
                        DetailRow("包围盒", "${variant.sizeX} × ${variant.sizeY} × ${variant.sizeZ}")
                    }
                    if (variant.offsetX != 0 || variant.offsetY != 0 || variant.offsetZ != 0) {
                        DetailRow(
                            "起始偏移",
                            "${variant.offsetX}, ${variant.offsetY}, ${variant.offsetZ}",
                        )
                    }
                    val flags = buildList {
                        if (variant.abandoned) add("废弃")
                        if (variant.giant) add("巨型")
                        if (variant.underground) add("地下")
                        if (variant.airPocket) add("气穴")
                        if (variant.basement) add("有地下室")
                        if (variant.cracked) add("开裂")
                    }
                    if (flags.isNotEmpty()) DetailRow("特征", flags.joinToString("、"))
                }
            }
        }

        if (details.pieces.isNotEmpty()) {
            Text(
                text = "结构片段（${details.pieces.size}）",
                style = MaterialTheme.typography.labelLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details.pieces.take(24).forEach { piece ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = piece.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = buildString {
                                        append("${piece.x}, ${piece.y}, ${piece.z}")
                                        append(" · ${piece.rotation}°")
                                        if (piece.hasBounds) {
                                            append(" · ")
                                            append("${piece.maxX - piece.minX + 1}×")
                                            append("${piece.maxZ - piece.minZ + 1}")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (piece.endPortalRoom) {
                                Text(
                                    text = "末地门 · ${piece.portalEyes}/12",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                if (details.pieces.size > 24) {
                    Text(
                        text = "还有 ${details.pieces.size - 24} 个片段未列出",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (details.containers.isNotEmpty()) {
            Text(
                text = "战利品箱（${details.containers.size}）",
                style = MaterialTheme.typography.labelLarge,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                details.containers.take(16).forEach { container ->
                    Text(
                        text = "(${container.x}, ${container.z}) · ${container.table}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (details.limits.isNotEmpty()) {
            Text(
                text = "数据限制",
                style = MaterialTheme.typography.labelLarge,
            )
            details.limits.forEach { limit ->
                Text(
                    text = "· ${limitLabel(limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun limitLabel(limit: String): String = when (limit) {
    "layout_unavailable" -> "该结构类型没有可重放的布局生成器"
    "layout_truncated" -> "布局过长，片段列表已截断"
    "containers_unavailable" -> "当前版本无法枚举战利品箱"
    "positions_estimated" -> "部分片段位置为估算值"
    else -> limit
}

/** Cross-section of the world at the chosen Y level, around the camera centre. */
@Composable
private fun UndergroundSheet(
    state: MapUiState,
    onYChange: (Int) -> Unit,
    onLayersChange: (Int) -> Unit,
    onSpanChange: (Int) -> Unit,
    onRefresh: () -> Unit,
    onOverlayChange: (MapOverlay) -> Unit,
) {
    val range = when (state.dimension) {
        Dimension.NETHER -> -32..96
        Dimension.END -> 0..128
        Dimension.OVERWORLD -> if (state.version.ordinal >= McVersion.MC_1_18_2.ordinal) {
            -64..160
        } else {
            0..160
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("地下剖面", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Y = ${state.undergroundY} · 覆盖 ${state.undergroundSpan}×${state.undergroundSpan} 方块" +
                " · 中心 X ${state.undergroundOriginX} Z ${state.undergroundOriginZ}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("叠加到地图", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapOverlay.entries.forEach { mode ->
                FilterChip(
                    selected = state.overlayMode == mode,
                    onClick = { onOverlayChange(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        if (state.overlayMode != MapOverlay.NONE) {
            Text(
                text = if (state.overlayLoading) {
                    "正在渲染 ${state.overlayBlocks}×${state.overlayBlocks} 方块…"
                } else {
                    "已叠加 ${state.overlayBlocks}×${state.overlayBlocks} 方块，" +
                        "中心 X ${state.overlayOriginX + state.overlayBlocks / 2} " +
                        "Z ${state.overlayOriginZ + state.overlayBlocks / 2}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 0.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1116)),
                contentAlignment = Alignment.Center,
            ) {
                val image = state.underground
                if (image != null) {
                    androidx.compose.foundation.Image(
                        bitmap = image,
                        contentDescription = "地下剖面图",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (state.undergroundLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "生成中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                } else if (image == null) {
                    Text(
                        text = "点下方按钮生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("范围", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            listOf(256, 512, 1024).forEach { span ->
                FilterChip(
                    selected = state.undergroundSpan == span,
                    onClick = { onSpanChange(span) },
                    label = { Text("$span") },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Y", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${state.undergroundY}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(48.dp),
            )
            Slider(
                value = state.undergroundY.toFloat(),
                onValueChange = { onYChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f),
            )
        }

        Text("显示的图层", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "实心方块", 2 to "洞穴", 4 to "峡谷").forEach { (bit, label) ->
                FilterChip(
                    selected = state.undergroundLayers and bit != 0,
                    onClick = { onLayersChange(state.undergroundLayers xor bit) },
                    label = { Text(label) },
                )
            }
        }

        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("重新生成")
        }
    }
}
