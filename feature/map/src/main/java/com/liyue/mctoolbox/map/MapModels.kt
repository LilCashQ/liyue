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
        /**
         * One sixteenth of a block per pixel: far enough in that a village
         * house fills the screen. The biome grid bottoms out at one block per
         * sample, so past this point the tile is just upscaled.
         */
        const val MIN_BLOCKS_PER_PIXEL = 0.0625f
        const val MAX_BLOCKS_PER_PIXEL = 2048f
    }
}

/**
 * One cached map tile. Tiles are anchored to the world grid, so panning only
 * renders the strips that scrolled into view.
 */
data class MapTile(
    val image: ImageBitmap,
    val key: TileKey,
    /** Side length in pixels, padding included. */
    val sizePx: Int,
    /** World block coordinate of the tile's first pixel. */
    val originBlockX: Int,
    val originBlockZ: Int,
)

/** Identity of a tile: everything that changes its pixels. */
/**
 * What a tile paints: the biome colours, or one of the underlying noise
 * parameters (cubiomes-viewer calls these the map layers). Climate layers need
 * 1.18 or newer, where the overworld is driven by the climate noise.
 */
enum class MapLayer(
    val label: String,
    /** 0..4 = climate parameter, 5 = height, null = biome colours. */
    val native: Int?,
) {
    BIOMES("群系", null),
    HEIGHT("高度", 5),
    TEMPERATURE("温度", 0),
    HUMIDITY("湿度", 1),
    CONTINENTALNESS("大陆性", 2),
    EROSION("侵蚀", 3),
    WEIRDNESS("怪异度", 4),
    ;

    val isClimate: Boolean get() = native != null && native != 5
}

data class TileKey(
    val seed: Long,
    val versionId: Int,
    val dimensionId: Int,
    val cellBlocks: Int,
    val tx: Int,
    val tz: Int,
    val terrain: Boolean,
    val contours: Boolean,
    /** Y level the biomes are sampled at; caves change the layer below ground. */
    val elevation: Int,
    val highlightHash: Int,
    val layer: Int = 0,
)

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
    /** Approximate surface height in blocks, when the version supports one. */
    val surfaceY: Int? = null,
)

data class LegendEntry(val id: Int, val name: String, val color: androidx.compose.ui.graphics.Color, val cells: Int)

data class StructureMarker(val type: StructureType, val x: Int, val z: Int)

/** One ore block drawn on the map, tagged with the ore it belongs to. */
data class OreMarker(val kind: com.liyue.mctoolbox.seedgen.OreKind, val x: Int, val y: Int, val z: Int)

/**
 * Chunkbase's Cave and Ravine apps are the same sliced view as our underground
 * preview, but drawn straight onto the map. [layers] bit meanings follow the
 * native bridge: 1 = solid, 2 = cave (carver), 4 = ravine (canyon).
 */
enum class MapOverlay(val label: String, val layers: Int) {
    NONE("关闭", 0),
    CAVE("洞穴", 2),
    RAVINE("峡谷", 4),
}

data class MapUiState(
    val seedText: String = "0",
    val seed: Long = 0L,
    val gameVersion: GameVersion = GameVersions.default,
    val dimension: Dimension = Dimension.OVERWORLD,
    val camera: MapCamera = MapCamera(),
    /** Tiles covering the viewport, already positioned in world coordinates. */
    val tiles: List<MapTile> = emptyList(),
    /** Blocks per tile pixel for the current zoom step. */
    val tileCellBlocks: Int = 4,
    /** Tiles from the previous zoom step, shown until the new ones land. */
    val staleTiles: List<MapTile> = emptyList(),
    val mapLayer: MapLayer = MapLayer.BIOMES,
    val rendering: Boolean = false,
    val renderMillis: Long = 0,
    val selected: PointInfo? = null,
    val legend: List<LegendEntry> = emptyList(),
    val enabledStructures: Set<StructureType> = StructureType.defaultEnabled,
    val structures: List<StructureMarker> = emptyList(),
    val structuresLoading: Boolean = false,
    val oreKind: com.liyue.mctoolbox.seedgen.OreKind? = null,
    val oreMarkers: List<OreMarker> = emptyList(),
    val oreLoading: Boolean = false,
    val oreScannedChunks: Int = 0,
    val spawnPoint: com.liyue.mctoolbox.seedgen.StructurePos? = null,
    /** Structure whose detail sheet is open, if any. */
    val detailsTarget: StructureMarker? = null,
    val details: StructureDetails? = null,
    val detailsLoading: Boolean = false,
    /** Resolved display name for the biome the structure spawned in. */
    val detailsBiomeName: String? = null,
    /** Y level of the underground preview slice. */
    val undergroundY: Int = 15,
    /** Bit mask: 1 = solid blocks, 2 = caves, 4 = ravines. */
    val undergroundLayers: Int = 7,
    val underground: ImageBitmap? = null,
    val undergroundLoading: Boolean = false,
    /** Side length, in blocks, of the rendered underground slice. */
    val undergroundSpan: Int = 512,
    val undergroundOriginX: Int = 0,
    val undergroundOriginZ: Int = 0,
    val helpVisible: Boolean = false,
    val overlayMode: MapOverlay = MapOverlay.NONE,
    val overlayY: Int = 15,
    val overlayImage: ImageBitmap? = null,
    val overlayOriginX: Int = 0,
    val overlayOriginZ: Int = 0,
    val overlayBlocks: Int = 0,
    val overlayLoading: Boolean = false,
    /** Biomes highlighted on the map; any number can be active at once. */
    val highlightBiomeIds: Set<Int> = emptySet(),
    val terrainEnabled: Boolean = true,
    val contoursEnabled: Boolean = false,
    val gridEnabled: Boolean = true,
    /** Biomes are sampled at this Y; see [MapUiState.biomeYLabel]. */
    val biomeY: Int = 63,
    val showSpawn: Boolean = true,
    /** Show chunk coordinates next to the world coordinates. */
    val chunkReadout: Boolean = false,
    /** Drop a pin where the map was tapped. */
    val clickMark: Boolean = true,
    val measureMode: Boolean = false,
    val measureStartX: Double? = null,
    val measureStartZ: Double? = null,
    val measureEndX: Double? = null,
    val measureEndZ: Double? = null,
    /** Pin dropped by a tap, kept separately from the biome readout. */
    val pinX: Double? = null,
    val pinZ: Double? = null,
    val layersClipped: Boolean = false,
    val error: String? = null,
) {
    val version: McVersion get() = gameVersion.engine

    val biomeYLabel: String get() = when (biomeY) {
        63 -> "地表 Y 63"
        0 -> "地下 Y 0"
        -52 -> "底部 Y -52"
        else -> "自定义 Y $biomeY"
    }
}

/**
 * Slime chunks follow the vanilla Java rule: a per-chunk `Random` seeded from
 * the world seed plus the chunk coordinates, and every tenth chunk is slimy.
 * Computing it here keeps it independent of the structure pass.
 */
fun slimeChunkAt(seed: Long, chunkX: Int, chunkZ: Int): Boolean {
    val scrambled = (seed +
        chunkX.toLong() * chunkX * 4987142L +
        chunkX.toLong() * 5947611L +
        chunkZ.toLong() * chunkZ * 4392871L +
        chunkZ.toLong() * 389711L) xor 987234911L
    var state = (scrambled xor 0x5DEECE66DL) and ((1L shl 48) - 1)
    // Java's Random.nextInt(10): next(31) then the rejection loop, which for 10
    // practically never repeats because only values near 2^31 overflow.
    while (true) {
        state = (state * 0x5DEECE66DL + 0xBL) and ((1L shl 48) - 1)
        val bits = (state ushr 17).toInt()
        val value = bits % 10
        if (bits - value + 9 >= 0) return value == 0
    }
}
