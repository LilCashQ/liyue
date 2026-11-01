package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class MineshaftTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `mineshafts resolve in a chunk window`() {
        val engine = SeedEngine()
        val shafts = engine.mineshaftsBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            chunkX0 = -64,
            chunkZ0 = -64,
            chunkX1 = 63,
            chunkZ1 = 63,
            maxCount = 256,
        )
        println("mineshafts in 2048x2048 blocks: ${shafts.size}")
        assertTrue(shafts.isNotEmpty())
    }
}
