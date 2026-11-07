package com.liyue.mctoolbox.map.finder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liyue.mctoolbox.map.GameVersion
import com.liyue.mctoolbox.map.MapTarget
import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.OreKind
import com.liyue.mctoolbox.seedgen.SeedEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for the finder screen. All the actual searching lives in
 * [FinderEngine], which is testable off-device.
 */
@HiltViewModel
class FinderViewModel @Inject constructor(
    engine: SeedEngine,
) : ViewModel() {

    private val finder = FinderEngine(engine)

    private val _state = MutableStateFlow(FinderUiState())
    val state: StateFlow<FinderUiState> = _state.asStateFlow()

    private val _mapRequests = MutableSharedFlow<MapTarget>(extraBufferCapacity = 1)
    val mapRequests: SharedFlow<MapTarget> = _mapRequests.asSharedFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val options = withContext(Dispatchers.Default) {
                finder.biomeOptions(_state.value.version.engine)
            }
            _state.update { current ->
                current.copy(
                    biomeOptions = options,
                    biome = current.biome ?: BiomeCatalog
                        .forDimension(options, current.biomeDimension)
                        .firstOrNull(),
                )
            }
        }
    }

    fun onTabChange(tab: FinderTab) = _state.update {
        it.copy(tab = tab, hits = emptyList(), searched = false, error = null)
    }

    /**
     * Applies one Chunkbase app card: switches the tab and, for structure apps,
     * pre-selects the structure so the user only has to press 查找.
     */
    fun applyPreset(app: ChunkbaseApp) {
        _state.update { current ->
            val structure = app.structure ?: current.structure
            val options = BiomeCatalog.forDimension(current.biomeOptions, current.biomeDimension)
            current.copy(
                tab = app.tab,
                structure = structure,
                biome = current.biome ?: options.firstOrNull(),
                hits = emptyList(),
                searched = false,
                error = null,
            )
        }
    }

    fun onSeedTextChange(text: String) = _state.update { it.copy(seedText = text) }

    fun onVersionChange(version: GameVersion) {
        _state.update { it.copy(version = version, hits = emptyList(), searched = false) }
        viewModelScope.launch {
            val options = withContext(Dispatchers.Default) { finder.biomeOptions(version.engine) }
            _state.update { current ->
                val supported = StructureSupport.forVersion(version.engine)
                current.copy(
                    biomeOptions = options,
                    biome = current.biome?.takeIf { old -> options.any { it.id == old.id } },
                    structure = if (current.structure in supported) current.structure else supported.first(),
                )
            }
        }
    }

    fun onVeinKindChange(kind: OreKind) =
        _state.update { it.copy(veinKind = kind, hits = emptyList(), searched = false) }

    fun onStructureChange(type: StructureType) =
        _state.update { it.copy(structure = type, hits = emptyList(), searched = false) }

    fun onBiomeDimensionChange(dimension: Dimension) {
        _state.update { current ->
            current.copy(
                biomeDimension = dimension,
                biome = BiomeCatalog.forDimension(current.biomeOptions, dimension).firstOrNull(),
                hits = emptyList(),
                searched = false,
            )
        }
    }

    fun onBiomeChange(option: BiomeOption) =
        _state.update { it.copy(biome = option, hits = emptyList(), searched = false) }

    fun onCenterXChange(text: String) = _state.update { it.copy(centerXText = text) }

    fun onCenterZChange(text: String) = _state.update { it.copy(centerZText = text) }

    fun onRadiusChange(text: String) = _state.update { it.copy(radiusText = text) }

    fun onLimitChange(text: String) = _state.update { it.copy(limitText = text) }

    fun onSeedQueueChange(text: String) = _state.update { it.copy(seedQueueText = text) }

    fun onMinCountChange(text: String) = _state.update { it.copy(minCountText = text) }

    fun useSpawnAsCenter() {
        val snapshot = _state.value
        viewModelScope.launch {
            val hits = finder.searchSpawn(SeedEngine.parseJavaSeed(snapshot.seedText), snapshot.version.engine)
            val spawn = hits.firstOrNull() ?: return@launch
            _state.update { it.copy(centerXText = spawn.x.toString(), centerZText = spawn.z.toString()) }
        }
    }

    fun openInMap(hit: FinderHit) {
        val snapshot = _state.value
        val seed = hit.seed ?: SeedEngine.parseJavaSeed(snapshot.seedText)
        _mapRequests.tryEmit(
            MapTarget(
                seed = seed,
                seedText = (hit.seed ?: SeedEngine.parseJavaSeed(snapshot.seedText)).toString(),
                versionLabel = snapshot.version.label,
                dimension = hit.dimension,
                centerX = hit.x.toDouble(),
                centerZ = hit.z.toDouble(),
            ),
        )
    }

    fun search() {
        searchJob?.cancel()
        val snapshot = _state.value
        searchJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, hits = emptyList()) }
            val started = System.nanoTime()
            try {
                val hits = runSearch(snapshot)
                val millis = (System.nanoTime() - started) / 1_000_000
                _state.update {
                    it.copy(loading = false, hits = hits, elapsedMs = millis, searched = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.message ?: "搜索失败") }
            }
        }
    }

    private fun parseSeedQueue(text: String): List<Long> =
        text.split('\n', ',', ' ', '\t', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { SeedEngine.parseJavaSeed(it) }
            .distinct()
            .take(200)

    private suspend fun runSearch(state: FinderUiState): List<FinderHit> {
        val seed = SeedEngine.parseJavaSeed(state.seedText)
        val version = state.version.engine
        if (state.version.edition.isBedrock) {
            val bedrockTabs = setOf(
                FinderTab.SLIME, FinderTab.SPAWN, FinderTab.ORE_VEIN, FinderTab.GOLDEN_APPLE,
                FinderTab.LAVA_POOL, FinderTab.DUNGEON,
            )
            if (state.tab in bedrockTabs) {
                throw IllegalStateException("${state.tab.label} 只支持 Java 版算法")
            }
        }
        return when (state.tab) {
            FinderTab.STRUCTURE -> finder.searchStructures(
                seed = seed,
                version = version,
                edition = state.version.edition,
                type = state.structure,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
            )

            FinderTab.BIOME -> {
                val option = state.biome ?: throw IllegalStateException("请选择群系")
                finder.searchBiomes(
                    seed = seed,
                    version = version,
                    dimension = state.biomeDimension,
                    option = option,
                    centerX = state.centerX,
                    centerZ = state.centerZ,
                    radius = state.radius,
                    limit = state.limit,
                )
            }

            FinderTab.STRONGHOLD -> finder.searchStrongholds(
                seed = seed,
                version = version,
                edition = state.version.edition,
                centerX = state.centerX,
                centerZ = state.centerZ,
                limit = state.limit,
            )

            FinderTab.SLIME -> finder.searchSlimeChunks(
                seed = seed,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
            )

            FinderTab.ORE_VEIN -> finder.searchOreVeins(
                seed = seed,
                version = version,
                kind = state.veinKind,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
            )

            FinderTab.LAVA_POOL -> finder.searchLavaPools(
                seed = seed,
                version = version,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
            )

            FinderTab.QUAD_HUT -> finder.searchQuadHuts(
                seed = seed,
                version = version,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
                edition = state.version.edition,
            )

            FinderTab.SEED_QUEUE -> {
                val seeds = parseSeedQueue(state.seedQueueText)
                if (seeds.isEmpty()) {
                    throw IllegalStateException("先填几颗种子：一行一颗，或用逗号/空格分隔")
                }
                finder.runSeedQueue(
                    seeds = seeds,
                    version = version,
                    edition = state.version.edition,
                    type = state.structure,
                    centerX = state.centerX,
                    centerZ = state.centerZ,
                    radius = state.radius,
                    minCount = state.minCountText.trim().toIntOrNull()?.coerceIn(1, 64) ?: 2,
                    limit = state.limit,
                )
            }

            FinderTab.DUNGEON -> finder.searchDungeons(
                seed = seed,
                version = version,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                limit = state.limit,
            )

            FinderTab.GOLDEN_APPLE -> finder.searchChestLoot(
                seed = seed,
                version = version,
                type = state.structure,
                centerX = state.centerX,
                centerZ = state.centerZ,
                radius = state.radius,
                itemName = "minecraft:golden_apple",
                itemLabel = "金苹果",
                limit = state.limit,
            )

            FinderTab.SPAWN -> finder.searchSpawn(seed, version)
        }
    }
}
