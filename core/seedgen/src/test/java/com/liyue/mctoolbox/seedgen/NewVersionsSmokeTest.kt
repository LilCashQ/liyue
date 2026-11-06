package com.liyue.mctoolbox.seedgen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class NewVersionsSmokeTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `new engine versions resolve biomes and structures`() {
        val engine = SeedEngine()
        val versions = listOf(
            McVersion.MC_1_21_4,
            McVersion.MC_1_21_5,
            McVersion.MC_1_21_6,
            McVersion.MC_1_21_9,
            McVersion.MC_1_21_11,
            McVersion.MC_26_1,
            McVersion.MC_26_2,
            McVersion.MC_26_3,
        )
        versions.forEach { version ->
            val sample = runBlocking {
                engine.biomeAt(0L, version, Dimension.OVERWORLD, 0, 0)
            }
            println("${version.label}: ${sample.name} (${sample.id})")
            assertTrue("${version.label} biome", sample.id >= 0)
            val villages = engine.structurePositionsBlocking(
                seed = 0L,
                version = version,
                dimension = Dimension.OVERWORLD,
                structureType = 5,
                blockX0 = -1024,
                blockZ0 = -1024,
                blockX1 = 1024,
                blockZ1 = 1024,
                maxCount = 16,
            )
            assertTrue("${version.label} structures", villages.isNotEmpty())
        }
    }
}
