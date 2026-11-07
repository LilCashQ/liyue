package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DungeonTest {
    private val engine = SeedEngine()

    @Test
    fun dungeonPlacementIsDeterministic() {
        val a = engine.dungeonsBlocking(0L, McVersion.MC_1_21_3, -256, -256, 256, 256)
        val b = engine.dungeonsBlocking(0L, McVersion.MC_1_21_3, -256, -256, 256, 256)
        assertTrue("expected some dungeons, got ${a.size}", a.isNotEmpty())
        assertEquals(a, b)
    }

    @Test
    fun dungeonPositionsAreStableAcrossBuilds() {
        val hits = engine.dungeonsBlocking(0L, McVersion.MC_1_21_3, -256, -256, 256, 256)
        val first = hits.first()
        assertEquals(112, first.x)
        assertEquals(8, first.y)
        assertEquals(-232, first.z)
        assertEquals(SeedEngine.DungeonKind.ZOMBIE, first.kind)
        assertTrue(
            "every dungeon needs a room above the floor",
            hits.all { it.y in -58..319 },
        )
    }

    @Test
    fun dungeonsNeedANewEnoughVersion() {
        val old = engine.dungeonsBlocking(0L, McVersion.MC_1_16_5, -128, -128, 128, 128)
        assertTrue("1.16 has no supported dungeon finder", old.isEmpty())
    }
}
