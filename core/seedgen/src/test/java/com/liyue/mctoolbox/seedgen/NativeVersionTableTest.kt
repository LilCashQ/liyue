package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Cross-checks the Kotlin version table against the native one, so the two
 * lists cannot drift apart silently.
 */
class NativeVersionTableTest {

    @Before
    fun requireNativeLibrary() {
        assumeTrue("host native library not provided", System.getProperty("liyue.nativeLib") != null)
    }

    @Test
    fun `kotlin and native version counts match`() {
        assertEquals(McVersion.entries.size, NativeSeedgen.versionCount())
    }

    @Test
    fun `every version maps to a distinct cubiomes enum value`() {
        val nativeIds = McVersion.entries.map { NativeSeedgen.cubiomesVersion(it.id) }
        assertTrue(nativeIds.all { it >= 0 })
        assertEquals(nativeIds.size, nativeIds.distinct().size)
    }
}
