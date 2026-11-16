package com.sarim.trinity.mms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Only the pure parts are tested: SharedPreferences needs a real Android context. */
class MmsDedupeTest {

    @Test
    fun normalise_ignoresNullAndBlank() {
        assertNull(MmsDedupe.normalise(null))
        assertNull(MmsDedupe.normalise(""))
        assertNull(MmsDedupe.normalise("   "))
        assertEquals("abc-123", MmsDedupe.normalise("  abc-123  "))
    }

    @Test
    fun parseIds_dropsBlanksAndCapsAt200() {
        assertEquals(emptyList<String>(), MmsDedupe.parseIds(null))
        assertEquals(emptyList<String>(), MmsDedupe.parseIds("  \n \n"))
        assertEquals(listOf("a", "b"), MmsDedupe.parseIds("a\n \nb\n"))
        val many = (1..250).map { "id$it" }.joinToString("\n")
        val parsed = MmsDedupe.parseIds(many)
        assertEquals(200, parsed.size)
        assertEquals("id250", parsed.last())
    }

    @Test
    fun rememberId_doesNotDuplicateAndKeepsNewest() {
        val once = MmsDedupe.rememberId(listOf("a", "b"), "c", 3)
        assertEquals(listOf("a", "b", "c"), once)
        assertEquals(once, MmsDedupe.rememberId(once, "c", 3))
        assertEquals(listOf("b", "c", "d"), MmsDedupe.rememberId(once, "d", 3))
    }

    @Test
    fun rememberId_acceptsSmallerCaps() {
        assertEquals(listOf("b"), MmsDedupe.rememberId(listOf("a", "b"), "b", 1))
        assertFalse(MmsDedupe.rememberId(emptyList(), "x", 0).isNotEmpty())
    }
}
