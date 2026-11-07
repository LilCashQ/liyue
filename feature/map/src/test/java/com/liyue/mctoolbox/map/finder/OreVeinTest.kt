package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.OreKind
import com.liyue.mctoolbox.seedgen.SeedEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ore veins are noise driven, so the test pins a seed that is known to have them. */
class OreVeinTest {

    private val engine = SeedEngine()
    private val finder = FinderEngine(engine)

    @Test
    fun ironVeinsAreFoundAndClustered() {
        val hits = finder.searchOreVeins(
            seed = 0L,
            version = McVersion.MC_1_21_3,
            kind = OreKind.IRON_VEIN,
            centerX = 0,
            centerZ = 0,
            radius = 512,
            limit = 8,
        )
        assertFalse("expected at least one iron vein near the origin", hits.isEmpty())
        hits.forEach { hit ->
            assertTrue("hit outside the search box: $hit", kotlin.math.abs(hit.x) <= 512)
            assertTrue("hit outside the search box: $hit", kotlin.math.abs(hit.z) <= 512)
            assertTrue("distance must not shrink with index", hit.distance >= 0)
        }
        assertTrue("results must be sorted by distance", hits == hits.sortedBy { it.distance })
    }

    @Test
    fun veinsNeed118OrNewer() {
        val message = runCatching {
            finder.searchOreVeins(
                seed = 0L,
                version = McVersion.MC_1_16_5,
                kind = OreKind.COPPER_VEIN,
                centerX = 0,
                centerZ = 0,
                radius = 256,
                limit = 4,
            )
        }.exceptionOrNull()?.message
        assertTrue("expected a version error, got $message", message == "矿脉需要 1.18 及以上")
    }
}
