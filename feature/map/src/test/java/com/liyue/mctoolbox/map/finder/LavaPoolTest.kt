package com.liyue.mctoolbox.map.finder

import com.liyue.mctoolbox.seedgen.McVersion
import com.liyue.mctoolbox.seedgen.SeedEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LavaPoolTest {

    private val finder = FinderEngine(SeedEngine())

    @Test
    fun lavaPoolsAreStableAndInsideTheSearchBox() = runBlocking {
        val first = finder.searchLavaPools(0L, McVersion.MC_1_21_3, 0, 0, 512, 8)
        val second = finder.searchLavaPools(0L, McVersion.MC_1_21_3, 0, 0, 512, 8)
        println("LAVA n=${first.size} $first")
        assertEquals("lava pools must be deterministic", first, second)
        first.forEach { hit ->
            assertTrue("pool outside the box: $hit", kotlin.math.abs(hit.x) <= 800)
            assertTrue("pool outside the box: $hit", kotlin.math.abs(hit.z) <= 800)
            assertTrue("pools sit at the lava line: $hit", hit.detail?.contains("Y -54") == true)
        }
    }

    @Test
    fun olderVersionsScanTheShallowLavaLine() = runBlocking {
        val hits = finder.searchLavaPools(0L, McVersion.MC_1_16_5, 0, 0, 256, 3)
        println("LAVA old n=${hits.size} $hits")
        hits.forEach { assertTrue("1.16 lava line is 10: $it", it.detail?.contains("Y 10") == true) }
    }
}
