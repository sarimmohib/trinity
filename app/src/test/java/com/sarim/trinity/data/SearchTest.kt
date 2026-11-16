package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure-JVM coverage for the ranking and in-memory filters; no Android APIs are used. */
class SearchTest {

    @Test
    fun rank_exactNormalisedNumberIsTopTier() {
        assertEquals(4, Search.rank("03007712008", "+92 300 7712008"))
        assertEquals(4, Search.rank("7712008", "0300-771-2008"))
        // The number tier beats a prefix on the same text.
        assertEquals(4, Search.rank("03007712008", "03007712008 Ali"))
    }

    @Test
    fun rank_namePrefixBeatsBoundaryAndSubstring() {
        assertEquals(3, Search.rank("ali", "Ali Khan"))
        assertEquals(2, Search.rank("khan", "Ali Khan"))
        // Two-character queries are rejected by policy; a longer substring still scores 1.
        assertEquals(0, Search.rank("li", "Ali Khan"))
        assertEquals(1, Search.rank("li K", "Ali Khan"))
    }

    @Test
    fun rank_wordBoundaryNeedsANonAlphaNumericBeforeTheMatch() {
        // "otp" starts a word in the first text but sits inside "slotp" in the second.
        assertEquals(2, Search.rank("otp", "your otp is 1234"))
        assertEquals(1, Search.rank("otp", "slotp"))
    }

    @Test
    fun rank_noMatchScoresZero() {
        assertEquals(0, Search.rank("zzz", "Ali Khan"))
        assertEquals(0, Search.rank("03007712008", "Ali Khan"))
    }

    @Test
    fun rank_rejectsQueriesShorterThanThreeCharacters() {
        assertEquals(0, Search.rank("", "Ali Khan"))
        assertEquals(0, Search.rank("a", "Ali Khan"))
        assertEquals(0, Search.rank("ab", "Ali Khan"))
    }

    @Test
    fun rank_allDigitFivePlusQueryIsAllowed() {
        // Five digits is sameNumber's floor; shorter digit runs never reach the top tier.
        assertEquals(4, Search.rank("12008", "03007712008"))
        assertEquals(1, Search.rank("1200", "03007712008"))
    }

    @Test
    fun rank_numberNormalisationRejectsDifferentNumbers() {
        assertEquals(0, Search.rank("03007712008", "03007712009"))
        assertEquals(0, Search.rank("12345", "03007712008"))
    }

    @Test
    fun escapeLike_escapesAllThreeWildcards() {
        assertEquals("plain", Search.escapeLike("plain"))
        assertEquals("50\\%\\_off\\\\x", Search.escapeLike("50%_off\\x"))
        assertEquals("\\%", Search.escapeLike("%"))
    }

    @Test
    fun calls_sortedByRankThenNewestFirst() {
        val calls = listOf(
            call(1L, "Ali", "03007712008", 100L),
            call(2L, "Alicia", "03007712009", 200L),
            call(3L, "Bob Ali", "03007712010", 300L),
        )
        // Both "Ali" and "Alicia" are name prefixes (tier 3); the newer of the two
        // comes first. "Bob Ali" is only a word-boundary match (tier 2).
        assertEquals(listOf(2L, 1L, 3L), Search.calls("ali", calls).map { it.id })
    }

    @Test
    fun calls_blankQueryReturnsNothing() {
        assertEquals(emptyList<CallEntry>(), Search.calls("", listOf(call(1L, "Ali", "03007712008", 100L))))
    }

    @Test
    fun contacts_sortedByRankThenName() {
        val people = listOf(
            Person(id = 1L, name = "Zaid", number = "03007712008"),
            Person(id = 2L, name = "Ali", number = "03007712009"),
            Person(id = 3L, name = "Alicia", number = "03007712010"),
        )
        assertEquals(listOf(2L, 3L), Search.contacts("ali", people).map { it.id })
    }

    @Test
    fun contacts_numberQueryFindsOnlyThatContact() {
        val people = listOf(
            Person(id = 1L, name = "Zaid", number = "03007712008"),
            Person(id = 2L, name = "Ali", number = "03007712009"),
        )
        assertEquals(listOf(1L), Search.contacts("03007712008", people).map { it.id })
    }

    @Test
    fun contacts_matchesEveryStoredNumber() {
        val person = Person(
            id = 1L,
            name = "Nobody",
            number = "0000000000",
            numbers = listOf(PhoneEntry("03007712008"), PhoneEntry("03009998888")),
        )
        assertEquals(listOf(1L), Search.contacts("7712008", listOf(person)).map { it.id })
    }

    private fun call(id: Long, name: String, number: String, date: Long) = CallEntry(
        id = id,
        number = number,
        name = name,
        outgoing = false,
        missed = false,
        date = date,
        durationSeconds = 0L,
        rule = null,
    )
}
