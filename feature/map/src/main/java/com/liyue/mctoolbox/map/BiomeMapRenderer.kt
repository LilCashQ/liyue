package com.liyue.mctoolbox.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders world-anchored map tiles with the bundled Axolotl renderer.
 *
 * The map used to be one screen-sized bitmap, which meant every pan or zoom
 * paid for the whole viewport. Tiles are aligned to a fixed world grid instead,
 * so scrolling only renders the strips that came into view, and tiles that
 * scrolled out stay cached.
 *
 * Tiles carry one pixel of padding on every side: neighbouring tiles overlap by
 * that pixel so terrain shading has the west/north samples it needs and no seam
 * shows up on the boundary.
 */
class BiomeMapRenderer(private val engine: SeedEngine) {

    suspend fun renderTile(request: TileRequest): MapTile = withContext(Dispatchers.Default) {
        val cellBlocks = request.key.cellBlocks
        val firstPixelX = request.key.tx * TILE_PX - PAD
        val firstPixelZ = request.key.tz * TILE_PX - PAD
        val originBlockX = firstPixelX * cellBlocks
        val originBlockZ = firstPixelZ * cellBlocks
        val size = TILE_PX + PAD * 2

        val layer = MapLayer.entries.getOrElse(request.key.layer) { MapLayer.BIOMES }
        val rgb = if (layer.native == null) {
            engine.renderTileBlocking(
                seed = request.key.seed,
                version = request.version,
                dimension = request.dimension,
                scale = cellBlocks,
                x = Math.floorDiv(originBlockX, cellBlocks),
                z = Math.floorDiv(originBlockZ, cellBlocks),
                width = size,
                height = size,
                elevation = request.key.elevation,
                terrain = request.key.terrain,
                contours = request.key.contours,
                highlightMask = request.highlightMask,
            )
        } else {
            engine.renderLayerBlocking(
                seed = request.key.seed,
                version = request.version,
                layer = layer.native,
                scale = cellBlocks,
                x = Math.floorDiv(originBlockX, cellBlocks),
                z = Math.floorDiv(originBlockZ, cellBlocks),
                width = size,
                height = size,
            )
        }

        val pixels = IntArray(size * size)
        for (index in pixels.indices) {
            val base = index * 3
            val r = rgb[base].toInt() and 0xff
            val g = rgb[base + 1].toInt() and 0xff
            val b = rgb[base + 2].toInt() and 0xff
            pixels[index] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        MapTile(
            image = bitmap.asImageBitmap(),
            key = request.key,
            sizePx = size,
            originBlockX = originBlockX,
            originBlockZ = originBlockZ,
        )
    }

    companion object {
        /** Rendered pixels per tile, padding excluded. */
        const val TILE_PX = 256

        /** Overlap in pixels on each side. */
        const val PAD = 1

        /**
         * Coarsest sample grid. At the widest zoom a screen spans millions of
         * blocks, so the step has to be allowed to grow well past the viewport
         * before tiles stop multiplying.
         */
        const val MAX_CELL_BLOCKS = 16384


        /**
         * Blocks per tile pixel for a zoom level: the next power of two at or
         * above the current zoom, so a tile pixel is never stretched by more
         * than 2x. Coarser sampling is only used when a frame would need too
         * many tiles; [SeedMapViewModel] decides that by counting them.
         */
        fun cellBlocksFor(blocksPerPixel: Float): Int =
            pow2Ceil(blocksPerPixel.toDouble()).coerceIn(1, MAX_CELL_BLOCKS)

        /** Halves the detail of a tile grid [times] times over. */
        fun coarsen(cellBlocks: Int, times: Int): Int =
            (cellBlocks * times).coerceIn(1, MAX_CELL_BLOCKS)

        private fun pow2Ceil(value: Double): Int {
            if (value <= 4.0) return 4
            var result = 4
            while (result < value) result = result shl 1
            return result
        }
    }
}

/** Everything one tile render needs. */
data class TileRequest(
    val key: TileKey,
    val version: McVersion,
    val dimension: Dimension,
    val highlightMask: ByteArray?,
)
