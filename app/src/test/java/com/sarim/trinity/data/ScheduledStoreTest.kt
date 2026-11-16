package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-helper tests: SharedPreferences and Android's JSON are not needed here. */
class ScheduledStoreTest {

    @Test
    fun roundTrip_preservesEveryFieldAndEscapesBody() {
        val body = "line one\n\"quoted\" \\ backslash \u00e9 \u0627 \u0001"
        val messages = listOf(
            ScheduledMessage(
                id = "a",
                address = "+92 300 7712008",
                body = body,
                sendAt = 1_700_000_000_000L,
                subscriptionId = 2,
                lastError = "NO SERVICE",
            ),
            ScheduledMessage(id = "b", address = "123", body = "plain", sendAt = 42L),
        )
        assertEquals(messages, parseScheduled(scheduledJson(messages)))
    }

    @Test
    fun malformedJson_neverThrowsAndSkipsBadEntries() {
        assertTrue(parseScheduled(null).isEmpty())
        assertTrue(parseScheduled("").isEmpty())
        assertTrue(parseScheduled("not json").isEmpty())
        assertTrue(parseScheduled("{}").isEmpty())
        assertTrue(parseScheduled("[]").isEmpty())

        val mixed = """
            [
              {"id":"ok","address":"111","body":"hi","sendAt":5},
              {"id":"bad","address":"","body":"x","sendAt":6},
              {"id":"worse","address":"222","body":"x","sendAt":"yesterday"},
              17
            ]
        """.trimIndent()
        val parsed = parseScheduled(mixed)
        assertEquals(1, parsed.size)
        assertEquals("ok", parsed.first().id)
        assertEquals(5L, parsed.first().sendAt)
    }

    @Test
    fun cap_keepsOnlyTheNewest200() {
        // Newest additions are written first, so the cap keeps the head of the list.
        val messages = (250 downTo 1).map {
            ScheduledMessage(id = "$it", address = "a$it", body = "b", sendAt = it.toLong())
        }
        val capped = capScheduled(messages)
        assertEquals(200, capped.size)
        assertEquals("250", capped.first().id)
        assertEquals("51", capped.last().id)
    }

    @Test
    fun due_returnsOnlyEntriesAtOrBeforeNow() {
        val messages = listOf(
            ScheduledMessage(id = "a", address = "a", body = "a", sendAt = 100L),
            ScheduledMessage(id = "b", address = "b", body = "b", sendAt = 200L),
            ScheduledMessage(id = "c", address = "c", body = "c", sendAt = 300L),
        )
        assertTrue(scheduledDue(messages, 50L).isEmpty())
        assertEquals(listOf(messages[0], messages[1]), scheduledDue(messages, 250L))
        assertEquals(messages, scheduledDue(messages, 300L))
    }

    @Test
    fun withId_assignsAnIdOnlyWhenBlank() {
        val assigned = withId(ScheduledMessage("", "a", "b", 1L))
        assertTrue(assigned.id.isNotBlank())
        assertNotEquals("", assigned.id)
        val fixed = ScheduledMessage("mine", "a", "b", 1L)
        assertEquals("mine", withId(fixed).id)
    }
}
