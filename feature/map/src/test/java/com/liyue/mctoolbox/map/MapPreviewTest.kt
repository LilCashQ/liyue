package com.liyue.mctoolbox.map

import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Renders the exact grid the map screen asks for and writes a PNG. This keeps
 * palette and sampling regressions visible without a device.
 */
class MapPreviewTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `default viewport renders to a png`() {
        val engine = SeedEngine()
        val width = 270
        val height = 600
        val started = System.nanoTime()
        val rgb = engine.renderTileBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            scale = 64,
            x = -135,
            z = -300,
            width = width,
            height = height,
            elevation = 63,
            terrain = false,
            contours = false,
        )
        println("renderTile 270x600 @64: ${(System.nanoTime() - started) / 1_000_000} ms")
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val base = i * 3
            val r = rgb[base].toInt() and 0xff
            val g = rgb[base + 1].toInt() and 0xff
            val b = rgb[base + 2].toInt() and 0xff
            pixels[i] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
        }
        val out = File("build/map-preview.png")
        out.parentFile?.mkdirs()
        writePng(out, pixels, width, height)
        assertTrue(out.length() > 1024L)
    }

    @Test
    fun `detailed viewport renders to a png`() {
        val engine = SeedEngine()
        val width = 270
        val height = 270
        val ids = engine.biomeGridBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            originBlockX = -540,
            originBlockZ = -540,
            width = width,
            height = height,
            cellBlocks = 4,
        )
        val lut = BiomePalette.lut(McVersion.MC_1_21_3, engine)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val id = ids[i]
            pixels[i] = lut[if (id in 0..255) id else 256]
        }
        val out = File("build/map-preview-detail.png")
        out.parentFile?.mkdirs()
        writePng(out, pixels, width, height)
        assertTrue(out.length() > 1024L)
    }

    private fun writePng(file: File, pixels: IntArray, width: Int, height: Int) {
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { data ->
            for (y in 0 until height) {
                data.writeByte(0)
                for (x in 0 until width) {
                    val rgb = pixels[y * width + x]
                    data.writeByte((rgb shr 16) and 0xff)
                    data.writeByte((rgb shr 8) and 0xff)
                    data.writeByte(rgb and 0xff)
                }
            }
        }
        val compressed = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val buffer = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            compressed.write(buffer, 0, n)
        }
        deflater.end()

        DataOutputStream(file.outputStream()).use { out ->
            out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            val ihdr = ByteArrayOutputStream().also { b ->
                DataOutputStream(b).use { d ->
                    d.writeInt(width)
                    d.writeInt(height)
                    d.writeByte(8)
                    d.writeByte(2)
                    d.writeByte(0)
                    d.writeByte(0)
                    d.writeByte(0)
                }
            }.toByteArray()
            writeChunk(out, "IHDR", ihdr)
            writeChunk(out, "IDAT", compressed.toByteArray())
            writeChunk(out, "IEND", ByteArray(0))
        }
    }

    private fun writeChunk(out: DataOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(data.size)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.writeInt(crc.value.toInt())
    }


    @Test
    fun `unshaded viewport renders to a png`() {
        val engine = SeedEngine()
        val width = 270
        val height = 600
        val rgb = engine.renderTileBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            scale = 64,
            x = -135,
            z = -300,
            width = width,
            height = height,
            elevation = 63,
            terrain = false,
            contours = false,
        )
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val base = i * 3
            pixels[i] = (0xff shl 24) or
                ((rgb[base].toInt() and 0xff) shl 16) or
                ((rgb[base + 1].toInt() and 0xff) shl 8) or
                (rgb[base + 2].toInt() and 0xff)
        }
        val out = File("build/map-preview-noshade.png")
        out.parentFile?.mkdirs()
        writePng(out, pixels, width, height)
        assertTrue(out.length() > 1024L)
    }
}
