package com.liyue.mctoolbox.seedgen

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedParsingTest {

    @Test
    fun `decimal seed is used verbatim`() {
        assertEquals(0L, SeedEngine.parseJavaSeed("0"))
        assertEquals(-4172144997902289642L, SeedEngine.parseJavaSeed("-4172144997902289642"))
        assertEquals(12345L, SeedEngine.parseJavaSeed("  12345  "))
    }

    @Test
    fun `text seed is folded through java string hashCode`() {
        val text = "里悦"
        assertEquals(text.hashCode().toLong(), SeedEngine.parseJavaSeed(text))
        assertEquals(0L, SeedEngine.parseJavaSeed(""))
        assertEquals(0L, SeedEngine.parseJavaSeed("   "))
    }

    @Test
    fun `numeric overflow falls back to hashCode`() {
        val tooLarge = "99999999999999999999"
        assertEquals(tooLarge.hashCode().toLong(), SeedEngine.parseJavaSeed(tooLarge))
    }
}
