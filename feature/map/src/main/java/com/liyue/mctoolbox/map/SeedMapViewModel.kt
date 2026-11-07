package com.liyue.mctoolbox.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.SeedEngine.FeatureHit
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.floor
import androidx.compose.ui.graphics.asImageBitmap

@HiltViewModel
class SeedMapViewModel @Inject constructor(
    private val engine: SeedEngine,
) : ViewModel() {

    private val renderer = BiomeMapRenderer(engine)
    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var viewportWidth = 0
    private var viewportHeight = 0
    private var renderJob: Job? = null
    private var structureJob: Job? = null
    private var legendJob: Job? = null
    private var oreJob: Job? = null
    private var detailsJob: Job? = null
    private var undergroundJob: Job? = null
    private var overlayJob: Job? = null
    private var tileJob: Job? = null

    /** LRU of rendered tiles, keyed by world grid position and render options. */
    private val tileCache = object : LinkedHashMap<TileKey, MapTile>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, MapTile>): Boolean =
            size > MAX_CACHED_TILES
    }

    fun onViewportChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == viewportWidth && height == viewportHeight) return
        viewportWidth = width
        viewportHeight = height
        requestRender(immediate = true)
    }

    fun onSeedTextChange(text: String) = _state.update { it.copy(seedText = text) }

    fun applySeed() {
        val seed = SeedEngine.parseJavaSeed(_state.value.seedText)
        _state.update { it.copy(seed = seed, tiles = emptyList(), legend = emptyList(), structures = emptyList()) }
        requestRender(immediate = true)
    }

    fun setVersion(gameVersion: GameVersion) {
        if (gameVersion == _state.value.gameVersion) return
        val engine = gameVersion.engine
        val camera = _state.value.camera
        val clamped = if (camera.blocksPerPixel > engine.maxBlocksPerPixel) {
            camera.copy(blocksPerPixel = engine.maxBlocksPerPixel)
        } else {
            camera
        }
        _state.update {
            val climateOk = engine.ordinal >= McVersion.MC_1_18_2.ordinal
            val layer = if (!climateOk && it.mapLayer.isClimate) MapLayer.BIOMES else it.mapLayer
            it.copy(
                gameVersion = gameVersion,
                camera = clamped,
                tiles = emptyList(),
                legend = emptyList(),
                structures = emptyList(),
                mapLayer = layer,
            )
        }
        requestRender(immediate = true)
    }

    fun setDimension(dimension: Dimension) {
        if (dimension == _state.value.dimension) return
        _state.update {
            // The climate and height layers are overworld-only.
            val layer = if (dimension != Dimension.OVERWORLD && it.mapLayer.native != null) {
                MapLayer.BIOMES
            } else {
                it.mapLayer
            }
            it.copy(
                dimension = dimension,
                tiles = emptyList(),
                legend = emptyList(),
                structures = emptyList(),
                mapLayer = layer,
            )
        }
        requestRender(immediate = true)
    }

    fun onCameraChanged(camera: MapCamera) {
        _state.update { it.copy(camera = camera) }
        requestRender(immediate = false)
    }

    fun zoomBy(factor: Float) {
        val current = _state.value
        val camera = current.camera
        val next = camera.copy(
            blocksPerPixel = (camera.blocksPerPixel / factor)
                .coerceIn(MapCamera.MIN_BLOCKS_PER_PIXEL, current.version.maxBlocksPerPixel),
        )
        if (next == camera) return
        onCameraChanged(next)
    }

    fun selectPoint(blockX: Int, blockZ: Int) {
        val current = _state.value
        viewModelScope.launch {
            val sample = engine.biomeAt(
                seed = current.seed,
                version = current.version,
                dimension = current.dimension,
                blockX = blockX,
                blockZ = blockZ,
            )
            val surfaceY = if (current.dimension == Dimension.OVERWORLD) {
                engine.surfaceHeightBlocking(current.seed, current.version, blockX, blockZ)
            } else {
                null
            }
            _state.update {
                it.copy(
                    selected = PointInfo(
                        blockX = blockX,
                        blockZ = blockZ,
                        biomeName = sample.name,
                        biomeId = sample.id,
                        surfaceY = surfaceY,
                    ),
                )
            }
        }
    }

    fun clearSelection() = _state.update { it.copy(selected = null) }

    fun setTerrainEnabled(enabled: Boolean) {
        if (enabled == _state.value.terrainEnabled) return
        _state.update { it.copy(terrainEnabled = enabled) }
        requestRender(immediate = true)
    }

    /** Switches the tile layer; climate layers fall back for pre-1.18 worlds. */
    fun setMapLayer(layer: MapLayer) {
        val snapshot = _state.value
        if (layer == snapshot.mapLayer) return
        val supported = layer.native == null ||
            (snapshot.dimension == Dimension.OVERWORLD &&
                (layer.native == 5 || snapshot.version.ordinal >= McVersion.MC_1_18_2.ordinal))
        if (!supported) return
        _state.update { it.copy(mapLayer = layer) }
        requestRender(immediate = true)
    }

    fun setGridEnabled(enabled: Boolean) = _state.update { it.copy(gridEnabled = enabled) }

    fun setContoursEnabled(enabled: Boolean) {
        if (enabled == _state.value.contoursEnabled) return
        _state.update { it.copy(contoursEnabled = enabled) }
        requestRender(immediate = true)
    }

    /** Re-samples the biome layer at another height (surface, caves, bedrock). */
    fun setBiomeY(y: Int) {
        val clamped = y.coerceIn(-64, 320)
        if (clamped == _state.value.biomeY) return
        _state.update { it.copy(biomeY = clamped) }
        requestRender(immediate = true)
    }

    fun setShowSpawn(enabled: Boolean) = _state.update { it.copy(showSpawn = enabled) }

    fun setChunkReadout(enabled: Boolean) = _state.update { it.copy(chunkReadout = enabled) }

    fun setClickMark(enabled: Boolean) = _state.update {
        it.copy(clickMark = enabled, pinX = if (enabled) it.pinX else null, pinZ = if (enabled) it.pinZ else null)
    }

    fun toggleMeasureMode() {
        val next = !_state.value.measureMode
        _state.update {
            it.copy(
                measureMode = next,
                measureStartX = null, measureStartZ = null,
                measureEndX = null, measureEndZ = null,
            )
        }
    }

    /** Extra tap target while measuring: first sets the anchor, then the end. */
    fun measureAt(blockX: Int, blockZ: Int): Boolean {
        val snapshot = _state.value
        if (!snapshot.measureMode) return false
        if (snapshot.measureStartX == null) {
            _state.update { it.copy(measureStartX = blockX.toDouble(), measureStartZ = blockZ.toDouble()) }
        } else {
            _state.update { it.copy(measureEndX = blockX.toDouble(), measureEndZ = blockZ.toDouble()) }
        }
        return true
    }

    fun dropPin(blockX: Int, blockZ: Int) {
        _state.update { it.copy(pinX = blockX.toDouble(), pinZ = blockZ.toDouble()) }
    }

    fun toggleSlimeChunks() {
        val current = _state.value.enabledStructures
        val next = if (StructureType.SLIME_CHUNK in current) {
            current - StructureType.SLIME_CHUNK
        } else {
            current + StructureType.SLIME_CHUNK
        }
        _state.update { it.copy(enabledStructures = next) }
        loadStructures()
    }

    /** Back to the stock look, like Chunkbase's restore-defaults button. */
    fun resetSettings() {
        _state.update {
            it.copy(
                terrainEnabled = true,
                contoursEnabled = false,
                gridEnabled = true,
                biomeY = 63,
                showSpawn = true,
                chunkReadout = false,
                clickMark = true,
                measureMode = false,
                measureStartX = null, measureStartZ = null,
                measureEndX = null, measureEndZ = null,
                pinX = null, pinZ = null,
                highlightBiomeIds = emptySet(),
                enabledStructures = StructureType.defaultEnabled,
            )
        }
        requestRender(immediate = true)
        loadStructures()
    }

    fun toggleHighlightBiome(biomeId: Int) {
        val current = _state.value.highlightBiomeIds
        val next = if (biomeId in current) current - biomeId else current + biomeId
        _state.update { it.copy(highlightBiomeIds = next) }
        requestRender(immediate = true)
    }

    fun clearHighlights() {
        if (_state.value.highlightBiomeIds.isEmpty()) return
        _state.update { it.copy(highlightBiomeIds = emptySet()) }
        requestRender(immediate = true)
    }

    /** Loads the biome breakdown for the visible area on demand. */
    fun requestLegend() {
        val snapshot = _state.value
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        legendJob?.cancel()
        legendJob = viewModelScope.launch {
            try {
                val cellBlocks = snapshot.tileCellBlocks * 4
                val halfW = viewportWidth * snapshot.camera.blocksPerPixel / 2.0
                val halfH = viewportHeight * snapshot.camera.blocksPerPixel / 2.0
                val originX = Math.floorDiv(
                    floor(snapshot.camera.centerX - halfW).toLong(),
                    cellBlocks.toLong(),
                ) * cellBlocks
                val originZ = Math.floorDiv(
                    floor(snapshot.camera.centerZ - halfH).toLong(),
                    cellBlocks.toLong(),
                ) * cellBlocks
                val width = (halfW * 2 / cellBlocks).toInt().coerceIn(16, 128)
                val height = (halfH * 2 / cellBlocks).toInt().coerceIn(16, 128)
                val ids = withContext(Dispatchers.Default) {
                    engine.biomeGridBlocking(
                        seed = snapshot.seed,
                        version = snapshot.version,
                        dimension = snapshot.dimension,
                        originBlockX = originX.toInt(),
                        originBlockZ = originZ.toInt(),
                        width = width,
                        height = height,
                        cellBlocks = cellBlocks,
                    )
                }
                val counts = HashMap<Int, Int>()
                ids.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                val legend = counts.entries
                    .sortedByDescending { it.value }
                    .take(12)
                    .map { (id, count) ->
                        val name = engine.biomeName(snapshot.version, id)
                        LegendEntry(id, name, BiomePalette.colorForName(name), count)
                    }
                _state.update { it.copy(legend = legend) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun toggleStructure(type: StructureType) {
        val current = _state.value.enabledStructures
        val next = if (type in current) current - type else current + type
        _state.update { it.copy(enabledStructures = next, structures = emptyList()) }
        loadStructures()
    }

    /**
     * Selects the ore layer (or clears it with null) and scans the chunks in
     * view. Ore generation is per chunk, so the work scales with the viewport.
     */
    fun setOreKind(kind: com.liyue.mctoolbox.seedgen.OreKind?) {
        _state.update { it.copy(oreKind = kind, oreMarkers = emptyList(), oreScannedChunks = 0) }
        if (kind != null) loadOres()
    }

    private fun loadOres() {
        oreJob?.cancel()
        val snapshot = _state.value
        val kind = snapshot.oreKind ?: return
        if (snapshot.dimension != kind.dimension) return
        if (viewportWidth <= 0) return
        val centerX = snapshot.camera.centerX.toInt()
        val centerZ = snapshot.camera.centerZ.toInt()
        // One ore pass per chunk is the expensive unit, so the window follows
        // the viewport but is clamped instead of skipped: 640 blocks across
        // is about 1.6k chunks, which stays well under a second on device.
        val halfBlocks = (snapshot.camera.blocksPerPixel * viewportWidth / 2).toInt()
            .coerceIn(ORE_MIN_HALF_BLOCKS, ORE_MAX_HALF_BLOCKS)
        val chunkX0 = Math.floorDiv(centerX - halfBlocks, 16)
        val chunkX1 = Math.floorDiv(centerX + halfBlocks, 16)
        val chunkZ0 = Math.floorDiv(centerZ - halfBlocks, 16)
        val chunkZ1 = Math.floorDiv(centerZ + halfBlocks, 16)
        oreJob = viewModelScope.launch {
            _state.update { it.copy(oreLoading = true, oreMarkers = emptyList(), oreScannedChunks = 0) }
            try {
                withContext(Dispatchers.Default) {
                    val markers = ArrayList<OreMarker>()
                    var scanned = 0
                    for (cz in chunkZ0..chunkZ1) {
                        for (cx in chunkX0..chunkX1) {
                            ensureActive()
                            engine.oreScanBlocking(
                                seed = snapshot.seed,
                                version = snapshot.version,
                                dimension = kind.dimension,
                                chunkX = cx,
                                chunkZ = cz,
                                kind = kind,
                            ).forEach { block ->
                                markers.add(OreMarker(kind, block.x, block.y, block.z))
                            }
                            scanned++
                            if (scanned % ORE_PROGRESS_CHUNKS == 0) {
                                val partial = ArrayList(markers)
                                val done = scanned
                                _state.update {
                                    it.copy(oreMarkers = partial, oreScannedChunks = done)
                                }
                            }
                        }
                    }
                    val all = ArrayList(markers)
                    val done = scanned
                    _state.update {
                        it.copy(oreMarkers = all, oreLoading = false, oreScannedChunks = done)
                    }
                }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(oreLoading = false) }
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("LiyueSeedMap", "ore scan failed", t)
                _state.update { it.copy(oreLoading = false) }
            }
        }
    }

    fun jumpTo(blockX: Double, blockZ: Double) {
        onCameraChanged(_state.value.camera.copy(centerX = blockX, centerZ = blockZ))
    }

    /**
     * Opens the layout sheet for a structure marker and asks the native bridge
     * to reconstruct it. Pieces are only available for types whose generator
     * cubiomes can replay; the rest report `layout_unavailable` in `limits`.
     */
    fun openStructureDetails(marker: StructureMarker) {
        detailsJob?.cancel()
        _state.update { it.copy(detailsTarget = marker, details = null, detailsLoading = true, detailsBiomeName = null) }
        val snapshot = _state.value
        detailsJob = viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.Default) {
                    engine.structureDetailsBlocking(
                        seed = snapshot.seed,
                        version = snapshot.version,
                        dimension = marker.type.dimension,
                        x = marker.x,
                        z = marker.z,
                        featureBit = marker.type.featureBit,
                    )
                }
                val parsed = json?.let(StructureDetails::parse)
                val biomeName = parsed?.variant?.biomeId
                    ?.takeIf { it in 0..255 }
                    ?.let { id -> engine.biomeName(snapshot.version, id) }
                _state.update {
                    it.copy(details = parsed, detailsLoading = false, detailsBiomeName = biomeName)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update { it.copy(details = null, detailsLoading = false) }
            }
        }
    }

    fun closeStructureDetails() {
        detailsJob?.cancel()
        _state.update { it.copy(detailsTarget = null, details = null, detailsLoading = false, detailsBiomeName = null) }
    }

    fun setUndergroundY(y: Int) {
        if (y == _state.value.undergroundY) return
        _state.update { it.copy(undergroundY = y) }
        if (_state.value.overlayMode != MapOverlay.NONE) loadOverlay()
    }

    fun setUndergroundLayers(layers: Int) {
        if (layers == _state.value.undergroundLayers) return
        _state.update { it.copy(undergroundLayers = layers, underground = null) }
    }

    /**
     * Renders the underground slice around the camera centre. The native call
     * wants a 16-aligned origin and at most 64x64 blocks, so the window is
     * snapped to the tile grid and grown to a multiple of 16.
     */
    fun loadUnderground(spanBlocks: Int = _state.value.undergroundSpan) {
        undergroundJob?.cancel()
        val snapshot = _state.value
        val span = spanBlocks.coerceIn(256, 1024)
        val tileWorld = UNDERGROUND_TILE_BLOCKS
        val tilesPerSide = span / tileWorld
        val tilePx = 64
        val totalPx = tilesPerSide * tilePx
        val originX = Math.floorDiv(snapshot.camera.centerX.toInt() - span / 2, 16) * 16
        val originZ = Math.floorDiv(snapshot.camera.centerZ.toInt() - span / 2, 16) * 16
        val y = snapshot.undergroundY
        val layers = snapshot.undergroundLayers
        undergroundJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    undergroundLoading = true,
                    underground = null,
                    undergroundSpan = span,
                    undergroundOriginX = originX,
                    undergroundOriginZ = originZ,
                )
            }
            try {
                val pixels = IntArray(totalPx * totalPx) { UNDERGROUND_EMPTY_PIXEL }
                val lock = Any()
                coroutineScope {
                    for (ty in 0 until tilesPerSide) {
                        for (tx in 0 until tilesPerSide) {
                            launch(Dispatchers.Default) {
                                val bytes = engine.undergroundTileBlocking(
                                    seed = snapshot.seed,
                                    version = snapshot.version,
                                    dimension = snapshot.dimension,
                                    scale = UNDERGROUND_TILE_SCALE,
                                    x = originX + tx * tileWorld,
                                    z = originZ + ty * tileWorld,
                                    width = tilePx,
                                    height = tilePx,
                                    y = y,
                                    layers = layers,
                                ) ?: return@launch
                                synchronized(lock) {
                                    for (py in 0 until tilePx) {
                                        for (px in 0 until tilePx) {
                                            val src = (py * tilePx + px) * 4
                                            val dx = tx * tilePx + px
                                            val dy = ty * tilePx + py
                                            pixels[dy * totalPx + dx] = argb(
                                                bytes[src], bytes[src + 1], bytes[src + 2], bytes[src + 3],
                                            )
                                        }
                                    }
                                    val frame = android.graphics.Bitmap.createBitmap(
                                        pixels.copyOf(), totalPx, totalPx,
                                        android.graphics.Bitmap.Config.ARGB_8888,
                                    ).asImageBitmap()
                                    _state.update { it.copy(underground = frame) }
                                }
                            }
                        }
                    }
                }
                _state.update { it.copy(undergroundLoading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("LiyueSeedMap", "underground failed", t)
                _state.update { it.copy(underground = null, undergroundLoading = false) }
            }
        }
    }

    fun setOverlayMode(mode: MapOverlay) {
        _state.update { it.copy(overlayMode = mode, overlayImage = null) }
        if (mode == MapOverlay.NONE) {
            overlayJob?.cancel()
            _state.update { it.copy(overlayLoading = false) }
        } else {
            loadOverlay()
        }
    }

    fun setOverlayY(y: Int) {
        val clamped = y.coerceIn(-64, 320)
        if (clamped == _state.value.overlayY) return
        _state.update { it.copy(overlayY = clamped) }
        if (_state.value.overlayMode != MapOverlay.NONE) loadOverlay()
    }

    /**
     * Renders the cave or ravine slice that covers the current view and keeps it
     * as a translucent layer over the biome map. Tiles are 256 blocks at a 4:1
     * sample ratio, so one call paints 64x64 pixels; they are stitched in
     * parallel because carving dominates the cost.
     */
    fun loadOverlay(blocks: Int = OVERLAY_BLOCKS) {
        val snapshot = _state.value
        val mode = snapshot.overlayMode
        if (mode == MapOverlay.NONE) return
        overlayJob?.cancel()
        val span = blocks.coerceIn(512, 2048)
        val tilesPerSide = span / UNDERGROUND_TILE_BLOCKS
        val tilePx = 64
        val totalPx = tilesPerSide * tilePx
        val originX = Math.floorDiv(snapshot.camera.centerX.toInt() - span / 2, 16) * 16
        val originZ = Math.floorDiv(snapshot.camera.centerZ.toInt() - span / 2, 16) * 16
        val y = snapshot.overlayY
        val layers = mode.layers
        overlayJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    overlayLoading = true,
                    overlayImage = null,
                    overlayBlocks = span,
                    overlayOriginX = originX,
                    overlayOriginZ = originZ,
                )
            }
            try {
                val pixels = IntArray(totalPx * totalPx)
                val lock = Any()
                var done = 0
                coroutineScope {
                    for (ty in 0 until tilesPerSide) {
                        for (tx in 0 until tilesPerSide) {
                            launch(Dispatchers.Default) {
                                val bytes = engine.undergroundTileBlocking(
                                    seed = snapshot.seed,
                                    version = snapshot.version,
                                    dimension = snapshot.dimension,
                                    scale = UNDERGROUND_TILE_SCALE,
                                    x = originX + tx * UNDERGROUND_TILE_BLOCKS,
                                    z = originZ + ty * UNDERGROUND_TILE_BLOCKS,
                                    width = tilePx,
                                    height = tilePx,
                                    y = y,
                                    layers = layers,
                                ) ?: return@launch
                                synchronized(lock) {
                                    for (py in 0 until tilePx) {
                                        for (px in 0 until tilePx) {
                                            val src = (py * tilePx + px) * 4
                                            val alpha = bytes[src + 3].toInt() and 0xFF
                                            val dx = tx * tilePx + px
                                            val dy = ty * tilePx + py
                                            pixels[dy * totalPx + dx] = if (alpha == 0) {
                                                0
                                            } else {
                                                argb(
                                                    bytes[src],
                                                    bytes[src + 1],
                                                    bytes[src + 2],
                                                    0xFF.toByte(),
                                                )
                                            }
                                        }
                                    }
                                    done++
                                    // Publish once the first tiles land so the
                                    // layer grows in while the rest render.
                                    if (done == 1 || done == tilesPerSide) {
                                        val frame = android.graphics.Bitmap.createBitmap(
                                            pixels.copyOf(), totalPx, totalPx,
                                            android.graphics.Bitmap.Config.ARGB_8888,
                                        ).asImageBitmap()
                                        _state.update { it.copy(overlayImage = frame) }
                                    }
                                }
                            }
                        }
                    }
                }
                val frame = android.graphics.Bitmap.createBitmap(
                    pixels.copyOf(), totalPx, totalPx,
                    android.graphics.Bitmap.Config.ARGB_8888,
                ).asImageBitmap()
                _state.update { it.copy(overlayImage = frame, overlayLoading = false) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                android.util.Log.e("LiyueSeedMap", "overlay render failed", t)
                _state.update { it.copy(overlayLoading = false, error = t.message ?: t.toString()) }
            }
        }
    }

    fun setUndergroundSpan(span: Int) {
        val next = span.coerceIn(256, 1024)
        _state.update { it.copy(undergroundSpan = next) }
        loadUnderground(next)
    }

    private fun argb(r: Byte, g: Byte, b: Byte, a: Byte): Int =
        ((a.toInt() and 0xFF) shl 24) or
            ((r.toInt() and 0xFF) shl 16) or
            ((g.toInt() and 0xFF) shl 8) or
            (b.toInt() and 0xFF)

    fun setHelpVisible(visible: Boolean) = _state.update { it.copy(helpVisible = visible) }

    /**
     * Applies a jump request from another screen (the structure finder): seed,
     * version, dimension and camera are replaced in one go.
     */
    fun openAt(target: MapTarget) {
        val gameVersion = GameVersions.all.firstOrNull { it.label == target.versionLabel }
            ?: GameVersions.default
        _state.update { current ->
            current.copy(
                seedText = target.seedText,
                seed = target.seed,
                gameVersion = gameVersion,
                dimension = target.dimension,
                camera = MapCamera(
                    centerX = target.centerX,
                    centerZ = target.centerZ,
                    blocksPerPixel = current.camera.blocksPerPixel.coerceIn(
                        MapCamera.MIN_BLOCKS_PER_PIXEL,
                        gameVersion.engine.maxBlocksPerPixel,
                    ),
                ),
                tiles = emptyList(),
                legend = emptyList(),
                structures = emptyList(),
                selected = null,
                overlayMode = target.overlay,
                overlayImage = null,
            )
        }
        requestRender(immediate = true)
        if (target.overlay != MapOverlay.NONE) loadOverlay()
    }

    private fun loadStructures() {
        structureJob?.cancel()
        val snapshot = _state.value
        val types = snapshot.enabledStructures
            // Slime chunks are drawn straight from the seed, one 16x16 square
            // per chunk; asking the structure enumerator for them is both slow
            // and lossy once a screen holds thousands of chunks.
            .filter { it != StructureType.SLIME_CHUNK }
            .filter { it.dimension == snapshot.dimension }
            .filter { snapshot.camera.blocksPerPixel <= it.maxBlocksPerPixel }
        if (viewportWidth <= 0) {
            _state.update { it.copy(structures = emptyList(), structuresLoading = false) }
            return
        }
        if (types.isEmpty()) {
            _state.update {
                it.copy(structures = emptyList(), structuresLoading = false, layersClipped = true)
            }
            return
        }
        _state.update { it.copy(layersClipped = false) }
        val centerX = snapshot.camera.centerX.toInt()
        val centerZ = snapshot.camera.centerZ.toInt()
        val radiusX = (snapshot.camera.blocksPerPixel * viewportWidth / 2).toInt().coerceIn(1024, 8192)
        val radiusZ = (snapshot.camera.blocksPerPixel * viewportHeight / 2).toInt().coerceIn(1024, 8192)
        structureJob = viewModelScope.launch {
            _state.update { it.copy(structuresLoading = true) }
            try {
                val bedrock = snapshot.gameVersion.edition.isBedrock
                val mask = StructureType.maskOf(types)
                val found = withContext(Dispatchers.Default) {
                    if (bedrock) {
                        val hits = engine.bedrockFeaturesBlocking(
                            seed = snapshot.seed,
                            version = snapshot.version,
                            dimension = snapshot.dimension,
                            minBlockX = centerX - radiusX,
                            minBlockZ = centerZ - radiusZ,
                            maxBlockX = centerX + radiusX,
                            maxBlockZ = centerZ + radiusZ,
                            featureMask = mask,
                            maxCount = 4096,
                        )
                        val strongholds = if (
                            snapshot.dimension == Dimension.OVERWORLD &&
                            types.contains(StructureType.STRONGHOLD)
                        ) {
                            engine.bedrockStrongholdsBlocking(snapshot.seed).map {
                                FeatureHit(x = it.first, z = it.second, kind = StructureType.STRONGHOLD.featureBit, endShip = false)
                            }
                        } else {
                            emptyList()
                        }
                        hits + strongholds
                    } else {
                        engine.findFeaturesBlocking(
                            seed = snapshot.seed,
                            version = snapshot.version,
                            dimension = snapshot.dimension,
                            minBlockX = centerX - radiusX,
                            minBlockZ = centerZ - radiusZ,
                            maxBlockX = centerX + radiusX,
                            maxBlockZ = centerZ + radiusZ,
                            featureMask = mask,
                            maxCount = 4096,
                        )
                    }
                }
                val markers = found.mapNotNull { hit ->
                    StructureType.fromFeatureBit(hit.kind)?.let { type ->
                        StructureMarker(type, hit.x, hit.z)
                    }
                }
                val spawn = if (
                    snapshot.dimension == Dimension.OVERWORLD && !bedrock
                ) {
                    engine.spawnPointBlocking(snapshot.seed, snapshot.version)
                } else {
                    null
                }
                _state.update {
                    it.copy(structures = markers, structuresLoading = false, spawnPoint = spawn)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update { it.copy(structuresLoading = false) }
            }
        }
    }

    /**
     * Schedules map tiles for the current camera. Cached tiles are published
     * synchronously so panning inside already-rendered ground is instant; the
     * missing ones render in parallel and stream in one by one.
     */
    private fun requestRender(immediate: Boolean) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val snapshot = _state.value
        var baseCells = BiomeMapRenderer.cellBlocksFor(snapshot.camera.blocksPerPixel)
        // A 1:1 tile grid at a 6" screen would need two and a half million
        // biome samples; halve the resolution until the frame is affordable.
        var guard = 0
        while (guard++ < 6 && tileCount(snapshot, baseCells) > MAX_TILES_PER_FRAME) {
            baseCells = BiomeMapRenderer.coarsen(baseCells, 2)
        }
        // While the camera is moving, render at half detail first: a quarter of
        // the samples lands quickly and the full pass only runs once it settles.
        val cellBlocks = if (immediate) baseCells else BiomeMapRenderer.coarsen(baseCells, 2)
        val terrain = snapshot.terrainEnabled
        val contours = snapshot.contoursEnabled
        val highlightMask = snapshot.highlightBiomeIds.takeIf { it.isNotEmpty() }?.let { ids ->
            ByteArray(256).also { mask -> ids.forEach { id -> mask[id.coerceIn(0, 255)] = 1 } }
        }
        val highlightHash = snapshot.highlightBiomeIds.hashCode()
        val keys = tileKeys(snapshot, cellBlocks, terrain, contours, highlightHash)
        val cached = synchronized(tileCache) { keys.mapNotNull { tileCache[it] } }
        val stale = if (cellBlocks != snapshot.tileCellBlocks) snapshot.tiles else snapshot.staleTiles
        _state.update {
            it.copy(
                tiles = cached,
                staleTiles = stale,
                tileCellBlocks = cellBlocks,
                rendering = keys.size > cached.size,
                error = null,
            )
        }
        tileJob?.cancel()
        val missing = synchronized(tileCache) { keys.filterNot { tileCache.containsKey(it) } }
        if (missing.isEmpty()) {
            loadStructures()
            if (_state.value.oreKind != null) loadOres()
            return
        }
        tileJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            _state.update { it.copy(rendering = true) }
            val started = System.nanoTime()
            try {
                val permits = Semaphore(TILE_PARALLELISM)
                coroutineScope {
                    missing.forEach { key ->
                        launch(Dispatchers.Default) {
                            permits.withPermit {
                                if (synchronized(tileCache) { tileCache.containsKey(key) }) {
                                    return@withPermit
                                }
                                val tile = renderer.renderTile(
                                    TileRequest(
                                        key = key,
                                        version = snapshot.version,
                                        dimension = snapshot.dimension,
                                        highlightMask = highlightMask,
                                    ),
                                )
                                synchronized(tileCache) { tileCache[key] = tile }
                                publishTiles(keys)
                            }
                        }
                    }
                }
                val millis = (System.nanoTime() - started) / 1_000_000
                _state.update { it.copy(rendering = false, renderMillis = millis, staleTiles = emptyList()) }
                loadStructures()
                if (_state.value.oreKind != null) loadOres()
                if (_state.value.legend.isEmpty()) requestLegend()
                if (!immediate && _state.value.tileCellBlocks > baseCells) {
                    requestRender(immediate = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("LiyueSeedMap", "render failed", t)
                _state.update { it.copy(rendering = false, error = t.message ?: t.toString()) }
            }
        }
    }

    /** Tiles that cover the viewport for [snapshot], plus one tile of margin. */
    private fun tileKeys(
        snapshot: MapUiState,
        cellBlocks: Int,
        terrain: Boolean,
        contours: Boolean,
        highlightHash: Int,
    ): List<TileKey> {
        val spanBlocks = BiomeMapRenderer.TILE_PX.toDouble() * cellBlocks
        val halfW = viewportWidth * snapshot.camera.blocksPerPixel / 2.0
        val halfH = viewportHeight * snapshot.camera.blocksPerPixel / 2.0
        val cx = snapshot.camera.centerX
        val cz = snapshot.camera.centerZ
        // Keep a fifth of a screen of margin so a short pan does not expose
        // blank edges, without rendering a whole extra ring of tiles.
        val marginX = halfW * TILE_MARGIN
        val marginZ = halfH * TILE_MARGIN
        val tx0 = floor((cx - halfW - marginX) / spanBlocks).toInt()
        val tx1 = floor((cx + halfW + marginX) / spanBlocks).toInt()
        val tz0 = floor((cz - halfH - marginZ) / spanBlocks).toInt()
        val tz1 = floor((cz + halfH + marginZ) / spanBlocks).toInt()
        val keys = ArrayList<TileKey>((tx1 - tx0 + 1) * (tz1 - tz0 + 1))
        for (tz in tz0..tz1) {
            for (tx in tx0..tx1) {
                keys += TileKey(
                    seed = snapshot.seed,
                    versionId = snapshot.version.id,
                    dimensionId = snapshot.dimension.id,
                    cellBlocks = cellBlocks,
                    tx = tx,
                    tz = tz,
                    terrain = terrain,
                    contours = contours,
                    elevation = snapshot.biomeY,
                    highlightHash = highlightHash,
                    layer = snapshot.mapLayer.ordinal,
                )
            }
        }
        return keys
    }

    /** How many tiles a viewport at [cellBlocks] would need, margin included. */
    private fun tileCount(snapshot: MapUiState, cellBlocks: Int): Int {
        val spanBlocks = BiomeMapRenderer.TILE_PX.toDouble() * cellBlocks
        val widthBlocks = viewportWidth * snapshot.camera.blocksPerPixel * (1 + 2 * TILE_MARGIN)
        val heightBlocks = viewportHeight * snapshot.camera.blocksPerPixel * (1 + 2 * TILE_MARGIN)
        val columns = kotlin.math.ceil(widthBlocks / spanBlocks).toInt().coerceAtLeast(1)
        val rows = kotlin.math.ceil(heightBlocks / spanBlocks).toInt().coerceAtLeast(1)
        return columns * rows
    }

    private fun publishTiles(keys: List<TileKey>) {
        val cached = synchronized(tileCache) { keys.mapNotNull { tileCache[it] } }
        _state.update { it.copy(tiles = cached) }
    }

    companion object {
        private const val DEBOUNCE_MS = 140L

        /** Extra ground rendered around the viewport, as a share of its size. */
        private const val TILE_MARGIN = 0.12

        /** Tiles kept in memory; each is about 262 kB, so this is ~25 MB. */
        private const val MAX_CACHED_TILES = 96

        /** Upper bound on tiles rendered for one frame; keeps a pan responsive. */
        private const val MAX_TILES_PER_FRAME = 72

        /** Tiles rendered at once; the native renderer is happy on a few cores. */
        private val TILE_PARALLELISM =
            Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

        /** Ore scan window, in blocks from the screen centre. */
        private const val ORE_MIN_HALF_BLOCKS = 128
        private const val ORE_MAX_HALF_BLOCKS = 320

        /** Chunks between intermediate marker flushes while scanning. */
        private const val ORE_PROGRESS_CHUNKS = 96

        /** Underground tiles are 64 px of 4-block samples, i.e. 256 blocks. */
        private const val UNDERGROUND_TILE_BLOCKS = 256
        private const val UNDERGROUND_TILE_SCALE = 4
        private const val OVERLAY_BLOCKS = 1024
        private val UNDERGROUND_EMPTY_PIXEL = 0xFF101B2B.toInt()
    }
}
