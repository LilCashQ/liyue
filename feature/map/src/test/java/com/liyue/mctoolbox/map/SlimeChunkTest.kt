package com.liyue.mctoolbox.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The in-map slime layer has its own Java `Random` port; these values come
 * straight from cubiomes' `isSlimeChunk`, so the layer cannot drift.
 */
class SlimeChunkTest {

    @Test
    fun matchesCubiomesForSeedZero() {
        val expected = listOf(1 to -3, 2 to -3, -2 to 0, 2 to 2, 4 to 2, 2 to 4)
        val found = buildList {
            for (cz in -4..4) {
                for (cx in -4..4) {
                    if (slimeChunkAt(0L, cx, cz)) add(cx to cz)
                }
            }
        }
        assertEquals(expected, found)
    }

    @Test
    fun roughlyOneInTenChunksIsSlimy() {
        var hits = 0
        for (cz in 0 until 40) {
            for (cx in 0 until 40) {
                if (slimeChunkAt(12345L, cx, cz)) hits++
            }
        }
        val share = hits / 1600.0
        assertEquals(0.1, share, 0.05)
    }
}
