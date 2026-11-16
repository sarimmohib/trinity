package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test
    fun formatDisplayNumber_groupsLocalNumber() {
        assertEquals("0300 771 2008", formatDisplayNumber("03007712008"))
    }

    @Test
    fun formatDisplayNumber_groupsInternationalNumber() {
        assertEquals("+92300 771 2008", formatDisplayNumber("+923007712008"))
    }

    @Test
    fun formatDisplayNumber_ignoresSeparators() {
        assertEquals("0300 771 2008", formatDisplayNumber("0300 771 2008"))
        assertEquals("0300 771 2008", formatDisplayNumber("0300-771-2008"))
        assertEquals("0300 771 2008", formatDisplayNumber("(0300) 771-2008"))
    }

    @Test
    fun formatDisplayNumber_trimsSurroundingWhitespace() {
        assertEquals("0300 771 2008", formatDisplayNumber("  03007712008  "))
    }

    @Test
    fun formatDisplayNumber_keepsShortCodesUnchanged() {
        assertEquals("911", formatDisplayNumber("911"))
        assertEquals("+911", formatDisplayNumber("+911"))
    }

    @Test
    fun formatDisplayNumber_groupsMediumLengthNumbers() {
        assertEquals("123 45", formatDisplayNumber("12345"))
        assertEquals("123 4567", formatDisplayNumber("1234567"))
    }

    @Test
    fun formatDisplayNumber_returnsBlankForBlankInput() {
        assertEquals("", formatDisplayNumber(""))
        assertEquals("", formatDisplayNumber("   "))
    }

    @Test
    fun formatDisplayNumber_returnsNonDigitInputUnchanged() {
        assertEquals("abc", formatDisplayNumber("abc"))
    }
}
