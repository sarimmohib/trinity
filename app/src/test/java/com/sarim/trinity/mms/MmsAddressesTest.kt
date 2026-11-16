package com.sarim.trinity.mms

import org.junit.Assert.assertEquals
import org.junit.Test

class MmsAddressesTest {

    @Test
    fun parse_handlesNullBlankAndEmpty() {
        assertEquals(emptyList<String>(), MmsAddresses.parse(null))
        assertEquals(emptyList<String>(), MmsAddresses.parse(""))
        assertEquals(emptyList<String>(), MmsAddresses.parse("   "))
    }

    @Test
    fun parse_splitsOnCommaAndSemicolon() {
        assertEquals(
            listOf("03007712008", "03001112233"),
            MmsAddresses.parse("03007712008, 03001112233"),
        )
        assertEquals(
            listOf("03007712008", "03001112233"),
            MmsAddresses.parse("03007712008;03001112233"),
        )
        assertEquals(
            listOf("a", "b", "c"),
            MmsAddresses.parse(" a , b ; c "),
        )
    }

    @Test
    fun parse_trimsDropsBlanksAndDuplicates() {
        assertEquals(
            listOf("111", "222"),
            MmsAddresses.parse(" 111 , , 222 ; 111 ;  "),
        )
    }

    @Test
    fun format_emptySingleAndGroup() {
        assertEquals("", MmsAddresses.format(emptyList()))
        assertEquals("03007712008", MmsAddresses.format(listOf("03007712008")))
        assertEquals("3 PEOPLE", MmsAddresses.format(listOf("a", "b", "c")))
    }

    @Test
    fun parse_deduplicatesLocalAndInternationalFormsOfOneNumber() {
        assertEquals(
            listOf("0300 771-2008", "03001112233"),
            MmsAddresses.parse("0300 771-2008, +923007712008, 03001112233"),
        )
    }
}
