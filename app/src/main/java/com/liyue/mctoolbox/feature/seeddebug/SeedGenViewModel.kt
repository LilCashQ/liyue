package com.liyue.mctoolbox.feature.seeddebug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liyue.mctoolbox.seedgen.BiomeGrid
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VersionProbe(
    val version: McVersion,
    val supported: Boolean,
    val biomeId: Int,
    val biomeName: String,
    val millis: Long,
)

data class SeedGenUiState(
    val seedText: String = "0",
    val version: McVersion = McVersion.MC_1_21_3,
    val dimension: Dimension = Dimension.OVERWORLD,
    val running: Boolean = false,
    val parsedSeed: Long? = null,
    val probes: List<VersionProbe> = emptyList(),
    val grid: BiomeGrid? = null,
    val gridSummary: List<Pair<String, Int>> = emptyList(),
    val gridMillis: Long = 0,
    val error: String? = null,
)

@HiltViewModel
class SeedGenViewModel @Inject constructor(
    private val engine: SeedEngine,
) : ViewModel() {

    private val _state = MutableStateFlow(SeedGenUiState())
    val state: StateFlow<SeedGenUiState> = _state.asStateFlow()

    fun onSeedTextChange(value: String) = _state.update { it.copy(seedText = value) }

    fun onVersionChange(version: McVersion) = _state.update { it.copy(version = version) }

    fun onDimensionChange(dimension: Dimension) = _state.update { it.copy(dimension = dimension) }

    fun runSelfCheck() {
        val current = _state.value
        if (current.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, error = null) }
            try {
                val seed = SeedEngine.parseJavaSeed(current.seedText)
                val probes = McVersion.entries.map { version ->
                    val started = System.nanoTime()
                    val sample = engine.biomeAt(seed, version, current.dimension, 0, 0)
                    VersionProbe(
                        version = version,
                        supported = sample.isKnown,
                        biomeId = sample.id,
                        biomeName = sample.name,
                        millis = elapsedMillis(started),
                    )
                }
                val started = System.nanoTime()
                val grid = engine.biomeGrid(
                    seed = seed,
                    version = current.version,
                    dimension = current.dimension,
                    centerBlockX = 0,
                    centerBlockZ = 0,
                    width = GRID_SIZE,
                    height = GRID_SIZE,
                )
                val millis = elapsedMillis(started)
                val summary = grid.ids.toList()
                    .groupingBy { engine.biomeName(current.version, it) }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(6)
                    .map { it.key to it.value }
                _state.update {
                    it.copy(
                        running = false,
                        parsedSeed = seed,
                        probes = probes,
                        grid = grid,
                        gridSummary = summary,
                        gridMillis = millis,
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(running = false, error = t.message ?: "未知错误")
                }
            }
        }
    }

    private fun elapsedMillis(startNanos: Long): Long =
        (System.nanoTime() - startNanos) / 1_000_000

    companion object {
        const val GRID_SIZE = 64
    }
}
