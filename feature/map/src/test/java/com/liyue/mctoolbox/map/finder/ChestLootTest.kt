package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden apples only show up in a fraction of desert temple chests, and desert
 * temples themselves are rare: seed 0 has none within 2048 blocks, the nearest
 * sits at (352, -2336), so the test searches 4096.
 */
class ChestLootTest {

    private val engine = SeedEngine()
    private val finder = FinderEngine(engine)

    @Test
    fun desertTemplesSomewhereHoldGoldenApples() = runBlocking {
        val hits = finder.searchChestLoot(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            type = StructureType.DESERT_PYRAMID,
            centerX = 0,
            centerZ = 0,
            radius = 4096,
            itemName = "minecraft:golden_apple",
            itemLabel = "金苹果",
            limit = 12,
        )
        assertFalse("seed 0 has golden apples in desert temple chests", hits.isEmpty())
        hits.forEach { hit ->
            assertTrue("apple chest outside the search box: $hit", kotlin.math.abs(hit.x) <= 5000)
            assertTrue("apple chest outside the search box: $hit", kotlin.math.abs(hit.z) <= 5000)
            assertTrue("titles must name the item: $hit", hit.title.contains("金苹果"))
        }
    }

    @Test
    fun buriedTreasureNeverHoldsGoldenApples() = runBlocking {
        val hits = finder.searchChestLoot(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            type = StructureType.TREASURE,
            centerX = 0,
            centerZ = 0,
            radius = 1024,
            itemName = "minecraft:golden_apple",
            itemLabel = "金苹果",
            limit = 5,
        )
        assertTrue("buried treasure has no golden apples, got $hits", hits.isEmpty())
    }
}
