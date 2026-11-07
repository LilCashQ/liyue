package com.liyue.mctoolbox.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/**
 * CSV and image export, the Android take on cubiomes-viewer's export dialog.
 *
 * The viewer writes the current map view to an image and the analysis tabs to
 * CSV; here the user picks the destination through the system file picker, so
 * no storage permission is needed.
 */
internal object MapExport {

    fun structuresCsv(state: MapUiState): String = buildString {
        appendLine("seed,${state.seed}")
        appendLine("version,${state.gameVersion.label}")
        appendLine("dimension,${state.dimension.label}")
        appendLine("type,x,z,distance")
        val saved = state.structures.sortedBy { marker ->
            hypot(marker.x - state.camera.centerX, marker.z - state.camera.centerZ)
        }
        for (marker in saved) {
            val distance = hypot(marker.x - state.camera.centerX, marker.z - state.camera.centerZ)
            appendLine(
                "${marker.type.label},${marker.x},${marker.z},${distance.toInt()}",
            )
        }
    }

    fun biomesCsv(
        legend: List<LegendEntry>,
        seedText: String,
        versionLabel: String,
        dimensionLabel: String,
    ): String = buildString {
        appendLine("seed,$seedText")
        appendLine("version,$versionLabel")
        appendLine("dimension,$dimensionLabel")
        appendLine("biome,id,cells,percent")
        val total = legend.sumOf { it.cells }.coerceAtLeast(1)
        for (entry in legend.sortedByDescending { it.cells }) {
            val percent = entry.cells * 100.0 / total
            appendLine(
                "${entry.name},${entry.id},${entry.cells},${"%.2f".format(percent)}",
            )
        }
    }

    suspend fun writeText(context: Context, uri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }

    /**
     * Renders what is on screen, tile by tile, into a PNG.
     *
     * [targetWidth] is the output width in pixels; the height follows the
     * current camera aspect ratio.
     */
    suspend fun renderImage(state: MapUiState, viewportWidth: Int, viewportHeight: Int, targetWidth: Int): Bitmap? =
        withContext(Dispatchers.Default) {
            if (viewportWidth <= 0 || viewportHeight <= 0 || state.tiles.isEmpty()) return@withContext null
            val scale = targetWidth.toFloat() / viewportWidth
            val width = targetWidth
            val height = (viewportHeight * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFF0E1116.toInt())

            val camera = state.camera
            val bpp = camera.blocksPerPixel
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val centerX = width / 2f
            val centerY = height / 2f
            for (tile in state.tiles + state.staleTiles) {
                val bitmapTile = tile.image.asAndroidBitmap()
                val size = (tile.sizePx * tile.key.cellBlocks / bpp * scale).toFloat()
                val left = (((tile.originBlockX - camera.centerX) / bpp + viewportWidth / 2f) * scale).toFloat()
                val top = (((tile.originBlockZ - camera.centerZ) / bpp + viewportHeight / 2f) * scale).toFloat()
                canvas.drawBitmap(
                    bitmapTile,
                    null,
                    android.graphics.RectF(left, top, left + size, top + size),
                    paint,
                )
            }

            // The rulers are drawn by the compose canvas, so stamp the centre
            // coordinates into the PNG to keep the export self-describing.
            val label = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = (width / 40f).coerceAtLeast(18f)
                isAntiAlias = true
            }
            val caption = "seed ${state.seedText} · ${state.gameVersion.label} · X ${centerX.toInt()} Z ${centerY.toInt()}"
            canvas.drawText(caption, width / 60f, height - width / 60f, label)
            bitmap
        }

    suspend fun writeImage(context: Context, uri: Uri, bitmap: Bitmap) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }
}
