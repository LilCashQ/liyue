package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bedrock structure placement. The expected positions come from
 * bedrock-dev/MCBEStructureFinder (MIT), built on the host and run with
 * `mytest 0 0 2000`; our port has to reproduce them exactly. Positions the
 * reference never enumerated (its search box is one area smaller) may appear
 * as extras, so the assertion is a superset check in that direction only.
 */
class BedrockFinderTest {

    private val engine = SeedEngine()

    private fun villages(seed: Long, radius: Int): Set<Pair<Int, Int>> =
        engine.bedrockFeaturesBlocking(
            seed = seed,
            version = McVersion.MC_1_18_2,
            dimension = Dimension.OVERWORLD,
            minBlockX = -radius,
            minBlockZ = -radius,
            maxBlockX = radius,
            maxBlockZ = radius,
            featureMask = 1 shl 0,
        ).map { it.x to it.z }.toSet()

    @Test
    fun villagesMatchTheReferenceImplementation() {
        val expected = setOf(
            -776 to -1640,
            -760 to 152,
            -760 to 1016,
            -264 to 584,
            -344 to 1336,
            152 to 168,
            104 to 664,
            216 to 1496,
            504 to 168,
            536 to 1000,
            552 to 1416,
            1112 to 1320,
        )
        val found = villages(0L, 2000)
        val missing = expected - found
        assertTrue("missing bedrock villages: $missing", missing.isEmpty())
    }

    @Test
    fun structureTypesStayInsideTheirDimension() {
        val nether = engine.bedrockFeaturesBlocking(
            seed = 0L,
            version = McVersion.MC_1_18_2,
            dimension = Dimension.NETHER,
            minBlockX = -1000,
            minBlockZ = -1000,
            maxBlockX = 1000,
            maxBlockZ = 1000,
            featureMask = 1 shl 0,
        )
        assertTrue("village in nether: $nether", nether.isEmpty())

        val endCity = engine.bedrockFeaturesBlocking(
            seed = 0L,
            version = McVersion.MC_1_18_2,
            dimension = Dimension.END,
            minBlockX = -2000,
            minBlockZ = -2000,
            maxBlockX = 2000,
            maxBlockZ = 2000,
            featureMask = 1 shl 22,
        )
        assertTrue("no end city in the end", endCity.isNotEmpty())
    }

    @Test
    fun strongholdsFormARingAroundTheOrigin() {
        val ring = engine.bedrockStrongholdsBlocking(0L)
        assertEquals("expected three strongholds", 3, ring.size)
        ring.forEach { (x, z) ->
            val distance = kotlin.math.hypot(x.toDouble(), z.toDouble())
            assertTrue("stronghold at $x,$z is $distance blocks out", distance in 300.0..4000.0)
        }
    }
}
