package com.sarim.trinity.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two negative id bands must never be confused: unsent bubbles vs stored MMS rows. */
class MmsIdTest {

    @Test
    fun pendingIdsAreNotTreatedAsMms() {
        listOf(-1L, -2L, -999L, -1_000_000L).forEach { id ->
            assertFalse("id=$id", isMmsMessageId(id))
        }
    }

    @Test
    fun shiftedMmsIdsAreRecognised() {
        listOf(1L, 2L, 1000L).forEach { providerId ->
            assertTrue("provider=$providerId", isMmsMessageId(MMS_ID_OFFSET + providerId))
        }
    }
}
