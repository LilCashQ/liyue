package com.liyue.mctoolbox.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Wireframe 3D view of a structure layout. Every piece contributes its
 * bounding box; the portal room and chests are picked out so the shape of a
 * stronghold or a nether fortress reads at a glance. Drag to rotate, pinch to
 * zoom.
 */
@Composable
internal fun StructurePreview3D(
    details: StructureDetails,
    modifier: Modifier = Modifier,
) {
    val boxes = remember(details) { layoutBoxes(details) }
    if (boxes.isEmpty()) {
        Text(
            text = "这个结构没有可用的布局数据，无法画 3D 预览。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val bounds = remember(boxes) { boundsOf(boxes) }
    var yaw by remember(details) { mutableFloatStateOf(-0.7f) }
    var pitch by remember(details) { mutableFloatStateOf(0.45f) }
    var zoom by remember(details) { mutableFloatStateOf(1f) }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .pointerInput(details) {
                    detectTransformGestures { _, pan, gestureZoom, gestureRotate ->
                        yaw += pan.x * 0.01f + gestureRotate * 0.01f
                        pitch = (pitch + pan.y * 0.01f).coerceIn(-1.4f, 1.4f)
                        zoom = (zoom * gestureZoom).coerceIn(0.35f, 4f)
                    }
                },
        ) {
            val ink = MaterialTheme.colorScheme.onSurface
            val accent = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                val radius = bounds.radius.coerceAtLeast(1f)
                val scale = min(size.width, size.height) * 0.44f / radius * zoom
                val originX = size.width / 2f
                val originY = size.height / 2f
                val cy = cos(yaw)
                val sy = sin(yaw)
                val cp = cos(pitch)
                val sp = sin(pitch)

                fun project(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
                    val dx = x - bounds.centerX
                    val dy = y - bounds.centerY
                    val dz = z - bounds.centerZ
                    val x1 = dx * cy - dz * sy
                    val z1 = dx * sy + dz * cy
                    val y1 = dy * cp - z1 * sp
                    val z2 = dy * sp + z1 * cp
                    val perspective = 1f / (1f + z2 / (radius * 3.2f))
                    return Triple(
                        originX + x1 * scale * perspective,
                        originY - y1 * scale * perspective,
                        z2,
                    )
                }

                val sorted = boxes.sortedByDescending { box ->
                    val p = project(
                        (box.minX + box.maxX) / 2f,
                        (box.minY + box.maxY) / 2f,
                        (box.minZ + box.maxZ) / 2f,
                    )
                    p.third
                }

                sorted.forEach { box ->
                    val color = when {
                        box.endPortalRoom -> accent
                        box.chest -> Color(0xFFC98A2B)
                        else -> ink.copy(alpha = 0.55f)
                    }
                    val corners = arrayOf(
                        project(box.minX, box.minY, box.minZ),
                        project(box.maxX, box.minY, box.minZ),
                        project(box.maxX, box.minY, box.maxZ),
                        project(box.minX, box.minY, box.maxZ),
                        project(box.minX, box.maxY, box.minZ),
                        project(box.maxX, box.maxY, box.minZ),
                        project(box.maxX, box.maxY, box.maxZ),
                        project(box.minX, box.maxY, box.maxZ),
                    )
                    val edges = intArrayOf(
                        0, 1, 1, 2, 2, 3, 3, 0,
                        4, 5, 5, 6, 6, 7, 7, 4,
                        0, 4, 1, 5, 2, 6, 3, 7,
                    )
                    val path = Path()
                    var i = 0
                    while (i < edges.size) {
                        val a = corners[edges[i]]
                        val b = corners[edges[i + 1]]
                        path.moveTo(a.first, a.second)
                        path.lineTo(b.first, b.second)
                        i += 2
                    }
                    drawPath(path, color, style = Stroke(width = 1.2f))
                    if (box.endPortalRoom) {
                        val top = Path().apply {
                            moveTo(corners[4].first, corners[4].second)
                            lineTo(corners[5].first, corners[5].second)
                            lineTo(corners[6].first, corners[6].second)
                            lineTo(corners[7].first, corners[7].second)
                            close()
                        }
                        drawPath(top, accent.copy(alpha = 0.35f))
                    }
                }
            }
        }
        Text(
            text = "拖动旋转 · 双指缩放 · ${boxes.size} 个片段" +
                if (boxes.any { it.endPortalRoom }) " · 蓝框=末地门房" else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private data class PreviewBox(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
    val endPortalRoom: Boolean,
    val chest: Boolean,
)

private data class PreviewBounds(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val radius: Float,
)

private fun layoutBoxes(details: StructureDetails): List<PreviewBox> {
    val boxes = ArrayList<PreviewBox>()
    details.pieces.forEach { piece ->
        if (!piece.positionKnown) return@forEach
        if (piece.hasBounds) {
            boxes.add(
                PreviewBox(
                    minX = piece.minX.toFloat(),
                    minY = piece.minY.toFloat(),
                    minZ = piece.minZ.toFloat(),
                    maxX = piece.maxX.toFloat(),
                    maxY = piece.maxY.toFloat(),
                    maxZ = piece.maxZ.toFloat(),
                    endPortalRoom = piece.endPortalRoom,
                    chest = false,
                ),
            )
        } else {
            val x = piece.x.toFloat()
            val y = piece.y.toFloat()
            val z = piece.z.toFloat()
            boxes.add(
                PreviewBox(
                    minX = x - 3f,
                    minY = y,
                    minZ = z - 3f,
                    maxX = x + 3f,
                    maxY = y + 6f,
                    maxZ = z + 3f,
                    endPortalRoom = piece.endPortalRoom,
                    chest = false,
                ),
            )
        }
    }
    details.containers.forEach { container ->
        val hostPiece = details.pieces.firstOrNull { it.id == container.piece }
        val y = hostPiece?.y?.toFloat() ?: 0f
        val x = container.x.toFloat()
        val z = container.z.toFloat()
        boxes.add(
            PreviewBox(
                minX = x - 0.8f,
                minY = y + 0.4f,
                minZ = z - 0.8f,
                maxX = x + 0.8f,
                maxY = y + 1.6f,
                maxZ = z + 0.8f,
                endPortalRoom = false,
                chest = true,
            ),
        )
    }
    return boxes
}

private fun boundsOf(boxes: List<PreviewBox>): PreviewBounds {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var maxZ = -Float.MAX_VALUE
    boxes.forEach { box ->
        minX = min(minX, box.minX)
        minY = min(minY, box.minY)
        minZ = min(minZ, box.minZ)
        maxX = maxOf(maxX, box.maxX)
        maxY = maxOf(maxY, box.maxY)
        maxZ = maxOf(maxZ, box.maxZ)
    }
    val centerX = (minX + maxX) / 2f
    val centerY = (minY + maxY) / 2f
    val centerZ = (minZ + maxZ) / 2f
    val extentX = (maxX - minX) / 2f
    val extentY = (maxY - minY) / 2f
    val extentZ = (maxZ - minZ) / 2f
    return PreviewBounds(
        centerX = centerX,
        centerY = centerY,
        centerZ = centerZ,
        radius = maxOf(extentX, maxOf(extentY, extentZ)).coerceAtLeast(4f),
    )
}
