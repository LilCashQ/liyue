package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Fixtures for the coarse overview path used by the map renderer (cell-centre
 * point sampling). Values come from tools/refpoints, an independent path.
 */
class CoarseGridTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `modern coarse grid matches reference`() {
        val ids = SeedEngine().biomeGridBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            originBlockX = -512,
            originBlockZ = -512,
            width = 64,
            height = 64,
            cellBlocks = 64,
        )
        assertEquals("9e3909423d3addae", fnv1a(ids))
    }

    @Test
    fun `nether coarse grid matches reference`() {
        val ids = SeedEngine().biomeGridBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.NETHER,
            originBlockX = -512,
            originBlockZ = -512,
            width = 64,
            height = 64,
            cellBlocks = 64,
        )
        assertEquals("f3546b59aff8fdf1", fnv1a(ids))
    }

    private fun fnv1a(ids: IntArray): String {
        var hash = 1469598103934665603L
        ids.forEach { id ->
            var value = id
            repeat(4) {
                hash = hash xor (value and 0xff).toLong()
                hash *= 1099511628211L
                value = value shr 8
            }
        }
        return java.lang.Long.toHexString(hash).padStart(16, '0')
    }
}
