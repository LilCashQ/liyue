package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class BridgeFeatureTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `unified search finds villages strongholds and mineshafts`() {
        val engine = SeedEngine()
        val mask = (1 shl 0) or (1 shl 9) or (1 shl 17)
        val hits = engine.findFeaturesBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            minBlockX = -2048,
            minBlockZ = -2048,
            maxBlockX = 2048,
            maxBlockZ = 2048,
            featureMask = mask,
            maxCount = 2048,
        )
        val kinds = hits.map { it.kind }.toSet()
        println("features: total=${hits.size} kinds=$kinds")
        assertTrue(hits.isNotEmpty())
        assertTrue(kinds.all { it in setOf(1 shl 0, 1 shl 9, 1 shl 17) })
        assertTrue(kinds.contains(1 shl 0))
        assertTrue(kinds.contains(1 shl 9))
    }

    @Test
    fun `nether fossils and end cities are reported`() {
        val engine = SeedEngine()
        val fossils = engine.findFeaturesBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.NETHER,
            minBlockX = -4096,
            minBlockZ = -4096,
            maxBlockX = 4096,
            maxBlockZ = 4096,
            featureMask = 1 shl 25,
            maxCount = 512,
        )
        println("nether fossils: ${fossils.size}")
        assertTrue(fossils.isNotEmpty())

        val endCities = engine.findFeaturesBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.END,
            minBlockX = -8192,
            minBlockZ = -8192,
            maxBlockX = 8192,
            maxBlockZ = 8192,
            featureMask = 1 shl 22,
            maxCount = 128,
        )
        println("end cities: ${endCities.size} ships=${endCities.count { it.endShip }}")
        assertTrue(endCities.isNotEmpty())
    }
}
