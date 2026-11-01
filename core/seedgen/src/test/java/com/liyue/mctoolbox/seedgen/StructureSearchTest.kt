package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class StructureSearchTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `finds villages deterministically`() {
        val engine = SeedEngine()
        val first = engine.structurePositionsBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            structureType = 5,
            blockX0 = -2048,
            blockZ0 = -2048,
            blockX1 = 2048,
            blockZ1 = 2048,
            maxCount = 128,
        )
        val second = engine.structurePositionsBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            structureType = 5,
            blockX0 = -2048,
            blockZ0 = -2048,
            blockX1 = 2048,
            blockZ1 = 2048,
            maxCount = 128,
        )
        println("villages: ${first.size}")
        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
        assertTrue(first.all { it.x in -2048..2048 && it.z in -2048..2048 })
    }

    @Test
    fun `nether fortress uses nether dimension`() {
        val engine = SeedEngine()
        val fortresses = engine.structurePositionsBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.NETHER,
            structureType = 18,
            blockX0 = -2048,
            blockZ0 = -2048,
            blockX1 = 2048,
            blockZ1 = 2048,
            maxCount = 64,
        )
        println("fortresses: ${fortresses.size}")
        assertTrue(fortresses.isNotEmpty())
        val wrongDim = engine.structurePositionsBlocking(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            structureType = 18,
            blockX0 = -2048,
            blockZ0 = -2048,
            blockX1 = 2048,
            blockZ1 = 2048,
            maxCount = 64,
        )
        assertTrue(wrongDim.isEmpty())
    }
}
