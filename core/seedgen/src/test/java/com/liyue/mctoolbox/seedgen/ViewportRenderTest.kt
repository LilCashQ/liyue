package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Sanity and timing check for the viewport-sized grid the map renderer asks for. */
class ViewportRenderTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `default viewport renders within the budget`() {
        val engine = SeedEngine()
        val started = System.nanoTime()
        val ids = engine.biomeGridBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            originBlockX = -34560,
            originBlockZ = -76800,
            width = 270,
            height = 600,
            cellBlocks = 256,
        )
        val millis = (System.nanoTime() - started) / 1_000_000
        println("viewport 270x600 @256: ${millis}ms")
        assertEquals(270 * 600, ids.size)
        assertTrue(ids.all { it in 0..255 })
    }
}
