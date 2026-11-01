package com.liyue.mctoolbox.map

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a screen-sized biome tile with the bundled Axolotl renderer, which
 * adds terrain shading and contour lines. The bitmap is generated at roughly
 * half the screen resolution and upscaled by Compose; the cell size follows the
 * zoom so the bitmap always covers the whole viewport.
 */
class BiomeMapRenderer(private val engine: SeedEngine) {

    suspend fun render(request: RenderRequest): RenderedMap = withContext(Dispatchers.Default) {
        val rawCell = request.blocksPerPixel * request.viewportWidthPx / TARGET_SAMPLES
        val cellBlocks = SAMPLE_STEPS.firstOrNull { it >= rawCell } ?: pow2Ceil(rawCell.toDouble())
        val width = ceil(request.viewportWidthPx * request.blocksPerPixel / cellBlocks)
            .toInt()
            .coerceIn(32, MAX_WIDTH)
        val height = ceil(request.viewportHeightPx * request.blocksPerPixel / cellBlocks)
            .toInt()
            .coerceIn(32, MAX_HEIGHT)

        val spanX = width.toLong() * cellBlocks
        val spanZ = height.toLong() * cellBlocks
        val originX = align(request.centerX - spanX / 2.0, cellBlocks)
        val originZ = align(request.centerZ - spanZ / 2.0, cellBlocks)

        val rgb = engine.renderTileBlocking(
            seed = request.seed,
            version = request.version,
            dimension = request.dimension,
            scale = cellBlocks,
            x = Math.floorDiv(originX, cellBlocks),
            z = Math.floorDiv(originZ, cellBlocks),
            width = width,
            height = height,
            elevation = ELEVATION,
            terrain = request.terrain && cellBlocks == 4,
            contours = request.contours && cellBlocks == 4,
            highlightMask = request.highlightMask,
        )

        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val base = index * 3
            val r = rgb[base].toInt() and 0xff
            val g = rgb[base + 1].toInt() and 0xff
            val b = rgb[base + 2].toInt() and 0xff
            pixels[index] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        RenderedMap(
            image = bitmap.asImageBitmap(),
            originBlockX = originX,
            originBlockZ = originZ,
            blocksPerSample = cellBlocks,
            width = width,
            height = height,
        )
    }

    private fun pow2Ceil(value: Double): Int {
        if (value <= 4.0) return 4
        var result = 4
        while (result < value) result = result shl 1
        return result
    }

    private fun align(value: Double, step: Int): Int {
        val floored = floor(value).toLong()
        val aligned = Math.floorDiv(floored, step.toLong()) * step
        return aligned.coerceIn(Int.MIN_VALUE.toLong() + step, Int.MAX_VALUE.toLong() - step).toInt()
    }

    companion object {
        private const val TARGET_SAMPLES = 540f
        private const val MAX_WIDTH = 720
        private const val MAX_HEIGHT = 1440
        private const val ELEVATION = 63
        private val SAMPLE_STEPS = intArrayOf(4, 16, 64, 256, 1024, 4096, 16384)
    }
}
