package com.liyue.mctoolbox.map

import androidx.compose.ui.graphics.ImageBitmap
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion

/** Camera in block coordinates with a blocks-per-screen-pixel scale. */
data class MapCamera(
    val centerX: Double = 0.0,
    val centerZ: Double = 0.0,
    val blocksPerPixel: Float = 8f,
) {
    companion object {
        const val MIN_BLOCKS_PER_PIXEL = 4f
        const val MAX_BLOCKS_PER_PIXEL = 2048f
    }
}

data class RenderRequest(
    val seed: Long,
    val version: McVersion,
    val dimension: Dimension,
    val centerX: Double,
    val centerZ: Double,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val blocksPerPixel: Float,
    val terrain: Boolean = false,
    val contours: Boolean = false,
    val highlightMask: ByteArray? = null,
)

data class RenderedMap(
    val image: ImageBitmap,
    val originBlockX: Int,
    val originBlockZ: Int,
    /** Blocks covered by one bitmap pixel. */
    val blocksPerSample: Int,
    val width: Int,
    val height: Int,
)

data class PointInfo(
    val blockX: Int,
    val blockZ: Int,
    val biomeName: String,
    val biomeId: Int,
)

data class LegendEntry(val id: Int, val name: String, val color: androidx.compose.ui.graphics.Color, val cells: Int)

data class StructureMarker(val type: StructureType, val x: Int, val z: Int)

data class MapUiState(
    val seedText: String = "0",
    val seed: Long = 0L,
    val gameVersion: GameVersion = GameVersions.default,
    val dimension: Dimension = Dimension.OVERWORLD,
    val camera: MapCamera = MapCamera(),
    val map: RenderedMap? = null,
    val rendering: Boolean = false,
    val renderMillis: Long = 0,
    val selected: PointInfo? = null,
    val legend: List<LegendEntry> = emptyList(),
    val enabledStructures: Set<StructureType> = StructureType.defaultEnabled,
    val structures: List<StructureMarker> = emptyList(),
    val structuresLoading: Boolean = false,
    val spawnPoint: com.liyue.mctoolbox.seedgen.StructurePos? = null,
    val highlightBiomeId: Int? = null,
    val terrainEnabled: Boolean = false,
    val gridEnabled: Boolean = true,
    val layersClipped: Boolean = false,
    val error: String? = null,
) {
    val version: McVersion get() = gameVersion.engine
}
