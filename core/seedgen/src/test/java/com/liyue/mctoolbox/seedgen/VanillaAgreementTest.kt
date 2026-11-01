package com.liyue.mctoolbox.seedgen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Ground-truth check against a vanilla Minecraft 1.20.1 server: a 64x64 grid of
 * 4-block cells centered on (0, 0) at y = 63, dumped from the region files by
 * tools/mcserver/parse_biomes.py (seed 0, default world type).
 *
 * 1.20.x shares the same world generation; the fixture is compared against
 * MC_1_20_6. The acceptance threshold is 99%.
 */
class VanillaAgreementTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `overworld grid agrees with vanilla 1_20_1 at 99 percent`() {
        val expected = readFixture()
        val grid = runBlocking {
            SeedEngine().biomeGrid(
                seed = 0L,
                version = McVersion.MC_1_20_6,
                dimension = Dimension.OVERWORLD,
                centerBlockX = 0,
                centerBlockZ = 0,
                width = expected.size,
                height = expected.size,
                y = 63,
            )
        }

        var matches = 0
        var total = 0
        for (z in expected.indices) {
            for (x in expected.indices) {
                total++
                if (grid[x, z] == expected[z][x]) matches++
            }
        }
        val rate = matches * 100.0 / total
        println("vanilla agreement: $matches/$total = $rate%")
        assertTrue("agreement $rate% below 99%", rate >= 99.0)
    }

    private fun readFixture(): Array<IntArray> =
        javaClass.classLoader!!.getResourceAsStream("vanilla/overworld_1_20_1_seed0_64x64.csv")!!
            .bufferedReader()
            .useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .map { line -> line.split(',').map(String::toInt).toIntArray() }
                    .toList()
                    .toTypedArray()
            }
}
