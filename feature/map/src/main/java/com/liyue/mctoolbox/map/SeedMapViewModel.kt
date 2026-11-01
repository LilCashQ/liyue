package com.liyue.mctoolbox.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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

    fun setHighlightBiome(biomeId: Int?) {
        if (biomeId == _state.value.highlightBiomeId) return
        _state.update { it.copy(highlightBiomeId = biomeId) }
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

    fun jumpTo(blockX: Double, blockZ: Double) {
        onCameraChanged(_state.value.camera.copy(centerX = blockX, centerZ = blockZ))
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
                val found = withContext(Dispatchers.Default) {
                    engine.findFeaturesBlocking(
                        seed = snapshot.seed,
                        version = snapshot.version,
                        dimension = snapshot.dimension,
                        minBlockX = centerX - radiusX,
                        minBlockZ = centerZ - radiusZ,
                        maxBlockX = centerX + radiusX,
                        maxBlockZ = centerZ + radiusZ,
                        featureMask = StructureType.maskOf(types),
                        maxCount = 4096,
                    )
                }
                val markers = found.mapNotNull { hit ->
                    StructureType.fromFeatureBit(hit.kind)?.let { type ->
                        StructureMarker(type, hit.x, hit.z)
                    }
                }
                val spawn = if (snapshot.dimension == Dimension.OVERWORLD) {
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
                        highlightMask = request.highlightBiomeId?.let { id ->
                            ByteArray(256).also { mask -> mask[id.coerceIn(0, 255)] = 1 }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update { it.copy(rendering = false, error = t.message ?: "渲染失败") }
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 140L
    }
}
