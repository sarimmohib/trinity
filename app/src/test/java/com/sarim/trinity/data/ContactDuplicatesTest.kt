package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests — no Android provider is touched. */
class ContactDuplicatesTest {

    private fun person(id: Long, name: String, vararg numbers: String): Person = Person(
        id = id,
        name = name,
        number = numbers.firstOrNull().orEmpty(),
        numbers = numbers.map { PhoneEntry(it) },
    )

    @Test
    fun identicalNumbersAreStrong() {
        val a = person(1, "Alice", "03007712008")
        val b = person(2, "Alicia", "03007712008")
        assertTrue(ContactDuplicates.isStrong(a, b))
        assertEquals(listOf(a to b), ContactDuplicates.candidates(listOf(a, b)))
    }

    @Test
    fun formattingAndCountryCodeDifferencesAreStrong() {
        val a = person(1, "Alice", "+92 300 7712008")
        val b = person(2, "Ali", "0300-771-2008")
        assertTrue(ContactDuplicates.isStrong(a, b))
        assertEquals(1, ContactDuplicates.candidates(listOf(a, b)).size)
    }

    @Test
    fun anySharedNumberAmongManyIsStrong() {
        val a = person(1, "Alice", "03007710001", "03007712008")
        val b = person(2, "Alicia", "03007712008", "03007710002")
        assertTrue(ContactDuplicates.isStrong(a, b))
    }

    @Test
    fun fallbackNumberFieldCountsWhenNumbersAreEmpty() {
        val a = Person(id = 1, name = "A", number = "03007712008")
        val b = Person(id = 2, name = "B", number = "0300 771 2008")
        assertTrue(ContactDuplicates.isStrong(a, b))
    }

    @Test
    fun sameNameDifferentNumberIsWeak() {
        val a = person(1, "  Alice  ", "03007712008")
        val b = person(2, "alice", "03007719999")
        assertFalse(ContactDuplicates.isStrong(a, b))
        val pairs = ContactDuplicates.candidates(listOf(a, b))
        assertEquals(1, pairs.size)
        assertEquals(a to b, pairs.single())
        assertFalse(ContactDuplicates.isStrong(pairs[0].first, pairs[0].second))
    }

    @Test
    fun differentNameAndDifferentNumberProducesNoPair() {
        val a = person(1, "Alice", "03007712008")
        val b = person(2, "Bob", "03007719999")
        assertTrue(ContactDuplicates.candidates(listOf(a, b)).isEmpty())
    }

    @Test
    fun blankNamesNeverProduceAWeakPair() {
        val a = person(1, "   ", "03007712008")
        val b = person(2, "", "03007719999")
        assertTrue(ContactDuplicates.candidates(listOf(a, b)).isEmpty())
    }

    @Test
    fun noSelfPairs() {
        val a = person(1, "Alice", "03007712008")
        assertTrue(ContactDuplicates.candidates(listOf(a)).isEmpty())
        assertTrue(ContactDuplicates.candidates(emptyList()).isEmpty())
    }

    @Test
    fun eachUnorderedPairAppearsOnce() {
        val a = person(1, "Same", "03007712008")
        val b = person(2, "Same", "03007712008")
        val c = person(3, "Same", "03007712008")
        val pairs = ContactDuplicates.candidates(listOf(a, b, c))
        assertEquals(3, pairs.size)
        assertEquals(
            setOf(setOf(1L, 2L), setOf(1L, 3L), setOf(2L, 3L)),
            pairs.map { setOf(it.first.id, it.second.id) }.toSet(),
        )
        assertTrue(pairs.all { it.first.id < it.second.id })
    }

    @Test
    fun capsAtOneHundredPairs() {
        val people = (1L..30L).map { person(it, "Twin", "03007712008") }
        val pairs = ContactDuplicates.candidates(people)
        assertEquals(100, pairs.size)
        assertEquals(100, pairs.map { it.first.id to it.second.id }.toSet().size)
        assertTrue(pairs.all { it.first.id < it.second.id })
    }
}
