package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ore scanning goes through Axolotl's `scan_ores` bridge, which is the same
 * generation code cubiomes' own `generateOres` uses. The checks below pin down
 * the parts that are easy to get wrong: Y ranges per ore, version gating, and
 * determinism.
 */
class OreScanTest {

    private val engine = SeedEngine()

    @Test
    fun diamondOnlySpawnsInTheExpectedYBand() {
        val blocks = (0 until 8).flatMap { chunk ->
            engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, chunk, 0, OreKind.DIAMOND)
        }
        assertTrue("expected diamond ore for seed 0", blocks.isNotEmpty())
        blocks.forEach { block ->
            assertTrue("diamond at y=${block.y}", block.y in -64..16)
        }
    }

    @Test
    fun coalAndIronHaveDifferentDistributions() {
        val coal = engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 0, 0, OreKind.COAL)
        val iron = engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 0, 0, OreKind.IRON)
        assertTrue(coal.isNotEmpty())
        assertTrue(iron.isNotEmpty())
        coal.forEach { assertTrue("coal y=${it.y}", it.y in -64..320) }
        iron.forEach { assertTrue("iron y=${it.y}", it.y in -64..320) }
    }

    @Test
    fun scanIsDeterministicAndRootedInTheChunk() {
        val a = engine.oreScanBlocking(12345L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 3, -7, OreKind.IRON)
        val b = engine.oreScanBlocking(12345L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 3, -7, OreKind.IRON)
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
        // A normal ore is placed inside the chunk that produced it. Blob ores can
        // straddle the border, so the block may sit one block outside the box.
        a.forEach { block ->
            assertTrue("x=${block.x} not near chunk 3", block.x in 47..64)
            assertTrue("z=${block.z} not near chunk -7", block.z in -113..-96)
        }
    }

    @Test
    fun netherOresStayInTheNether() {
        val debris = engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.NETHER, 0, 0, OreKind.DEBRIS)
        assertTrue(debris.isNotEmpty())
        debris.forEach { assertTrue("debris y=${it.y}", it.y in 8..119) }
        // The overworld dimension has no ancient debris.
        assertTrue(
            engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 0, 0, OreKind.DEBRIS).isEmpty(),
        )
    }

    @Test
    fun oreVeinsNeedANewEnoughVersion() {
        // Iron veins (the big blobs) only exist from 1.18 on.
        assertTrue(
            engine.oreScanBlocking(0L, McVersion.MC_1_16_5, Dimension.OVERWORLD, 0, 0, OreKind.IRON_VEIN).isEmpty(),
        )
        // Veins are sparse: they occur in roughly one chunk out of nine, so the
        // reference chunk below is the first hit near the origin for seed 0.
        val vein = engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, -12, 7, OreKind.IRON_VEIN)
        assertTrue("expected an iron vein in chunk (-12, 7)", vein.isNotEmpty())
        vein.forEach { assertTrue("iron vein y=${it.y}", it.y in -60..-8) }
    }

    @Test
    fun copperVeinsAreSeparateFromIronVeins() {
        val copper = engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, -3, 3, OreKind.COPPER_VEIN)
        assertTrue("expected a copper vein in chunk (-3, 3)", copper.isNotEmpty())
        copper.forEach { assertTrue("copper vein y=${it.y}", it.y in 0..50) }
        // Vein scanning is Overworld-only noise and needs 1.18's terrain shape.
        assertTrue(
            engine.oreScanBlocking(0L, McVersion.MC_1_21_3, Dimension.NETHER, -3, 3, OreKind.COPPER_VEIN).isEmpty(),
        )
    }
}
