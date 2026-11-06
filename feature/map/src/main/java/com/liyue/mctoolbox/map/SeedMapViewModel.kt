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
        _state.update { it.copy(seed = seed, map = null, legend = emptyList(), structures = emptyList()) }
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
            it.copy(gameVersion = gameVersion, camera = clamped, map = null, legend = emptyList(), structures = emptyList())
        }
        requestRender(immediate = true)
    }

    fun setDimension(dimension: Dimension) {
        if (dimension == _state.value.dimension) return
        _state.update { it.copy(dimension = dimension, map = null, legend = emptyList(), structures = emptyList()) }
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
            _state.update {
                it.copy(
                    selected = PointInfo(
                        blockX = blockX,
                        blockZ = blockZ,
                        biomeName = sample.name,
                        biomeId = sample.id,
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

    fun setGridEnabled(enabled: Boolean) = _state.update { it.copy(gridEnabled = enabled) }

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

    /** Loads the biome breakdown for the visible tile on demand. */
    fun requestLegend() {
        val snapshot = _state.value
        val map = snapshot.map ?: return
        legendJob?.cancel()
        legendJob = viewModelScope.launch {
            try {
                val step = 4
                val width = (map.width / step).coerceAtLeast(8)
                val height = (map.height / step).coerceAtLeast(8)
                val ids = withContext(Dispatchers.Default) {
                    engine.biomeGridBlocking(
                        seed = snapshot.seed,
                        version = snapshot.version,
                        dimension = snapshot.dimension,
                        originBlockX = map.originBlockX,
                        originBlockZ = map.originBlockZ,
                        width = width,
                        height = height,
                        cellBlocks = map.blocksPerSample * step,
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
                map = null,
                legend = emptyList(),
                structures = emptyList(),
                selected = null,
            )
        }
        requestRender(immediate = true)
    }

    private fun loadStructures() {
        structureJob?.cancel()
        val snapshot = _state.value
        val types = snapshot.enabledStructures
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

    private fun requestRender(immediate: Boolean) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        renderJob?.cancel()
        val request = _state.value
        renderJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            _state.update { it.copy(rendering = true, error = null) }
            try {
                val started = System.nanoTime()
                val rendered = renderer.render(
                    RenderRequest(
                        seed = request.seed,
                        version = request.version,
                        dimension = request.dimension,
                        centerX = request.camera.centerX,
                        centerZ = request.camera.centerZ,
                        viewportWidthPx = viewportWidth,
                        viewportHeightPx = viewportHeight,
                        blocksPerPixel = request.camera.blocksPerPixel,
                        terrain = request.terrainEnabled,
                        contours = request.terrainEnabled,
                        highlightMask = request.highlightBiomeIds.takeIf { it.isNotEmpty() }?.let { ids ->
                            ByteArray(256).also { mask ->
                                ids.forEach { id -> mask[id.coerceIn(0, 255)] = 1 }
                            }
                        },
                    ),
                )
                val millis = (System.nanoTime() - started) / 1_000_000
                _state.update {
                    it.copy(
                        map = rendered,
                        rendering = false,
                        renderMillis = millis,
                        selected = it.selected?.let { point ->
                            point.copy(
                                biomeName = engine.biomeName(request.version, point.biomeId),
                            )
                        },
                    )
                }
                loadStructures()
                if (_state.value.oreKind != null) loadOres()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                android.util.Log.e("LiyueSeedMap", "render failed", t)
                _state.update { it.copy(rendering = false, error = t.message ?: t.toString()) }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 140L

        /** Ore scan window, in blocks from the screen centre. */
        private const val ORE_MIN_HALF_BLOCKS = 128
        private const val ORE_MAX_HALF_BLOCKS = 320

        /** Chunks between intermediate marker flushes while scanning. */
        private const val ORE_PROGRESS_CHUNKS = 96

        /** Underground tiles are 64 px of 4-block samples, i.e. 256 blocks. */
        private const val UNDERGROUND_TILE_BLOCKS = 256
        private const val UNDERGROUND_TILE_SCALE = 4
        private val UNDERGROUND_EMPTY_PIXEL = 0xFF101B2B.toInt()
    }
}
