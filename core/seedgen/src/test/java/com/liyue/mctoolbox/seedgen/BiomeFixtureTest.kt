package com.liyue.mctoolbox.seedgen

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Golden-value tests for the whole Kotlin -> JNI -> cubiomes path.
 *
 * The expected hashes were produced by `tools/refgen`, which calls cubiomes
 * directly without the JNI layer, over the same 64x64 grid of 4-block cells
 * centered on block (0, 0) at y = 63. See docs/capability-matrix.md for how the
 * fixtures were produced.
 */
class BiomeFixtureTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    private data class Fixture(
        val version: McVersion,
        val dimension: Dimension,
        val seed: Long,
        val expectedHash: String,
        val size: Int = 64,
    )

    private val fixtures = listOf(
        Fixture(McVersion.MC_1_16_5, Dimension.OVERWORLD, 0L, "e4104c1ae74f051c"),
        Fixture(McVersion.MC_1_18_2, Dimension.OVERWORLD, 0L, "4122c7647fb60863"),
        Fixture(McVersion.MC_1_20_6, Dimension.OVERWORLD, 0L, "30db1fce324ca386"),
        Fixture(McVersion.MC_1_21_3, Dimension.OVERWORLD, 0L, "30db1fce324ca386"),
        Fixture(McVersion.MC_1_20_6, Dimension.NETHER, 0L, "24975a39563d4365"),
        Fixture(McVersion.MC_1_20_6, Dimension.END, 0L, "0d21a6c797df8383"),
        Fixture(McVersion.MC_1_21_3, Dimension.OVERWORLD, 12345L, "00c1d5ca562324dc"),
        Fixture(McVersion.MC_1_21_3, Dimension.OVERWORLD, 0L, "4a67f6db382ad668", size = 256),
        Fixture(
            McVersion.MC_1_20_6,
            Dimension.OVERWORLD,
            -4172144997902289642L,
            "551efd310c26d039",
        ),
    )

    @Test
    fun `grids match reference fixtures`() {
        val engine = SeedEngine()
        fixtures.forEach { fixture ->
            val grid = runBlocking {
                engine.biomeGrid(
                    seed = fixture.seed,
                    version = fixture.version,
                    dimension = fixture.dimension,
                    centerBlockX = 0,
                    centerBlockZ = 0,
                    width = fixture.size,
                    height = fixture.size,
                    y = 63,
                )
            }
            assertEquals(
                "fixture ${fixture.version.label}/${fixture.dimension.label}",
                fixture.expectedHash,
                fnv1a(grid.ids),
            )
        }
    }

    @Test
    fun `seed zero at origin is a river in 1_20_6`() {
        val engine = SeedEngine()
        val sample = runBlocking {
            engine.biomeAt(0L, McVersion.MC_1_20_6, Dimension.OVERWORLD, 0, 0)
        }
        assertEquals(7, sample.id)
        assertEquals("river", sample.name)
    }

    @Test
    fun `grid queries are deterministic`() {
        val engine = SeedEngine()
        val first = runBlocking {
            engine.biomeGrid(42L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 0, 0, 32, 32)
        }
        val second = runBlocking {
            engine.biomeGrid(42L, McVersion.MC_1_21_3, Dimension.OVERWORLD, 0, 0, 32, 32)
        }
        assertEquals(fnv1a(first.ids), fnv1a(second.ids))
    }

    private fun fnv1a(ids: IntArray): String {
        var hash = 1469598103934665603L
        ids.forEach { id ->
            var value = id
            repeat(4) {
                hash = hash xor (value and 0xff).toLong()
                hash *= 1099511628211L
                value = value shr 8
            }
        }
        return java.lang.Long.toHexString(hash).padStart(16, '0')
    }
}
