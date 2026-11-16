package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageBackupTest {

    private fun sample(): MessageBackup.Backup = MessageBackup.Backup(
        schema = MessageBackup.SCHEMA,
        exportedAt = 1_700_000_000_000L,
        threads = listOf(
            MessageBackup.Thread(
                address = "+92 300 7712008",
                messages = listOf(
                    MessageBackup.Entry(10L, 1, "hello \"world\"\n🙂", 1),
                    MessageBackup.Entry(20L, 2, "sent \\ backslash", 0),
                ),
            ),
            MessageBackup.Thread(
                address = "123",
                messages = listOf(MessageBackup.Entry(30L, 1, "third", 1)),
            ),
        ),
    )

    @Test
    fun serializeParse_roundTripAndCount() {
        val backup = sample()
        assertEquals(backup, MessageBackup.parse(MessageBackup.serialize(backup)))
        assertEquals(3, MessageBackup.count(backup))
    }

    @Test
    fun parse_rejectsBadSchemaAndMalformedInput() {
        assertNull(MessageBackup.parse("not json"))
        assertNull(MessageBackup.parse("{}"))
        assertNull(MessageBackup.parse("[]"))
        val wrongSchema = MessageBackup.serialize(sample()).replace("\"schema\":1", "\"schema\":2")
        assertNull(MessageBackup.parse(wrongSchema))
        assertNull(MessageBackup.parse("{\"schema\":1.0,\"exportedAt\":1,\"threads\":[]}"))
    }

    @Test
    fun parse_validatesEveryFieldBeforeReturning() {
        // A late malformed entry must reject the whole file, not apply part of it.
        val broken = """
            {"schema":1,"exportedAt":1,"threads":[
              {"address":"a","messages":[{"date":1,"type":1,"body":"ok","read":1}]},
              {"address":"b","messages":[{"date":2,"type":1,"body":"broken"}]}
            ]}
        """.trimIndent()
        assertNull(MessageBackup.parse(broken))

        val badType = """
            {"schema":1,"exportedAt":1,"threads":[
              {"address":"a","messages":[{"date":1,"type":"one","body":"b","read":1}]}
            ]}
        """.trimIndent()
        assertNull(MessageBackup.parse(badType))
    }

    @Test
    fun dedupe_keyIsAddressDateTypeBodyAndIgnoresRead() {
        val existing = listOf(MessageKey("a", 1L, 1, "body"))
        val incoming = listOf(
            MessageKey("a", 1L, 1, "body"), // already in the provider
            MessageKey("a", 1L, 1, "body"), // duplicate within the incoming file
            MessageKey("a", 1L, 2, "body"), // different type
            MessageKey("b", 1L, 1, "body"), // different address
            MessageKey("a", 2L, 1, "body"), // different date
            MessageKey("a", 1L, 1, "other"), // different body
        )
        assertEquals(
            listOf(incoming[2], incoming[3], incoming[4], incoming[5]),
            dedupe(existing, incoming),
        )

        // Two rows that differ only in `read` collapse to a single key.
        val entries = listOf(
            MessageBackup.Entry(1L, 1, "same", 0),
            MessageBackup.Entry(1L, 1, "same", 1),
        )
        val keys = entries.map { MessageKey("a", it.date, it.type, it.body) }
        assertEquals(1, dedupe(emptyList(), keys).size)
    }
}
