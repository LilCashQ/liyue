package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class SpawnStrongholdTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `strongholds are deterministic and ordered`() {
        val engine = SeedEngine()
        val first = engine.strongholdsBlocking(0L, McVersion.MC_1_21_3, 8)
        val second = engine.strongholdsBlocking(0L, McVersion.MC_1_21_3, 8)
        println("strongholds: ${first.take(3)}")
        assertTrue(first.isNotEmpty())
        assertEquals(first, second)
    }

    @Test
    fun `slime chunks are deterministic`() {
        val engine = SeedEngine()
        val chunks = engine.slimeChunksBlocking(0L, 0, 0, 31, 31, 512)
        println("slime chunks in 512x512 blocks: ${chunks.size}")
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.x % 16 == 8 && it.z % 16 == 8 })
        assertEquals(chunks, engine.slimeChunksBlocking(0L, 0, 0, 31, 31, 512))
    }

    @Test
    fun `spawn point resolves`() {
        val engine = SeedEngine()
        val spawn = engine.spawnPointBlocking(0L, McVersion.MC_1_21_3)
        println("spawn: $spawn")
        assertNotNull(spawn)
    }
}
