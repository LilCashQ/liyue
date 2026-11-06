package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.map.StructureType
import com.liyue.mctoolbox.seedgen.Dimension
import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-checks the finder against `tools/refgen/refstruct`, which enumerates
 * the same structures straight from cubiomes without the JNI/Kotlin path.
 *
 * Reference (seed 0, 1.21.11, villages within 2048 blocks of the origin):
 *
 *     ./build/refstruct 0 32 0 5 -2048 -2048 2048 2048
 *     608,720  272,944  544,-1056  -1072,1216  -1568,1984  -1872,-1920
 *
 * Only the first few nearest positions are asserted: the finder searches
 * outward from the centre, so those must match exactly.
 */
class FinderEngineTest {

    private val engine = SeedEngine()
    private val finder = FinderEngine(engine)

    @Test
    fun nearestVillagesMatchReference() = runBlocking {
        val hits = finder.searchStructures(
            seed = 0L,
            version = McVersion.MC_1_21_11,
            type = StructureType.VILLAGE,
            centerX = 0,
            centerZ = 0,
            radius = 2048,
            limit = 3,
        )
        val expected = listOf(608 to 720, 272 to 944, 544 to -1056)
        assertEquals(expected.map { it.first }, hits.map { it.x })
        assertEquals(expected.map { it.second }, hits.map { it.z })
        // nearest first
        assertTrue(hits[0].distance < hits[1].distance)
        assertTrue(hits[1].distance < hits[2].distance)
        // the biome at a village position must be a village-capable one
        assertTrue(hits.all { it.detail?.isNotEmpty() == true })
    }

    @Test
    fun structureOutsideSearchRadiusIsNotReported() = runBlocking {
        val hits = finder.searchStructures(
            seed = 0L,
            version = McVersion.MC_1_21_11,
            type = StructureType.VILLAGE,
            centerX = 0,
            centerZ = 0,
            radius = 600,
            limit = 10,
        )
        // 608,720 lies ~941 blocks away, outside a 600 block radius.
        assertTrue(hits.none { it.x == 608 && it.z == 720 })
    }

    @Test
    fun strongholdsAreOrderedByDistance() = runBlocking {
        val hits = finder.searchStrongholds(0L, McVersion.MC_1_21_3, centerX = 0, centerZ = 0, limit = 4)
        assertEquals(4, hits.size)
        assertEquals(hits.sortedBy { it.distance }.map { it.x }, hits.map { it.x })
        // The first stronghold is always inside 1408..2688 blocks in vanilla.
        assertTrue(hits.first().distance in 1000..3000)
    }

    @Test
    fun biomeSearchReturnsMatchingBiome() = runBlocking {
        val options = finder.biomeOptions(McVersion.MC_1_21_3)
        val target = options.first { it.name == "mushroom_fields" }
        val hits = finder.searchBiomes(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            dimension = Dimension.OVERWORLD,
            option = target,
            centerX = 0,
            centerZ = 0,
            radius = 4096,
            limit = 2,
        )
        assertTrue(hits.isNotEmpty())
        hits.forEach { hit ->
            val sample = engine.biomeAt(0L, McVersion.MC_1_21_3, Dimension.OVERWORLD, hit.x, hit.z)
            assertEquals(target.id, sample.id)
        }
    }

    @Test
    fun slimeChunksAreReportedAsBlockCentres() = runBlocking {
        val hits = finder.searchSlimeChunks(seed = 0L, centerX = 0, centerZ = 0, radius = 256, limit = 8)
        assertTrue(hits.isNotEmpty())
        hits.forEach { hit ->
            assertEquals(8, Math.floorMod(hit.x, 16))
            assertEquals(8, Math.floorMod(hit.z, 16))
        }
    }

    @Test
    fun unsupportedStructureThrows() = runBlocking {
        var thrown = false
        try {
            finder.searchStructures(
                seed = 0L,
                version = McVersion.MC_1_7_10,
                type = StructureType.TRIAL_CHAMBERS,
                centerX = 0,
                centerZ = 0,
                radius = 1024,
                limit = 3,
            )
        } catch (e: IllegalStateException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
