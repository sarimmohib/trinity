package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private fun samplePrefs(): Map<String, Map<String, Any?>> = linkedMapOf(
        "trinity.rules" to linkedMapOf<String, Any?>(
            "names" to "[{\"v\":\"bank\",\"s\":\"Both\"}]",
            "hideMatchedCalls" to true,
        ),
        "trinity.ui" to linkedMapOf<String, Any?>(
            "start_tab" to 2,
            "recent_searches" to "[\"0300\"]",
            "screen_unknown" to false,
        ),
        "trinity.sims" to linkedMapOf<String, Any?>("ask_every_time" to true),
        "trinity.threads" to linkedMapOf<String, Any?>(
            "muted" to "1,2",
            "seen.7" to 1_700_000_000_000L,
            "draft.7" to "unsent secret",
        ),
        "trinity.audit" to linkedMapOf<String, Any?>(
            "entries" to """[{"at":9,"kind":"SMS","address":"123","decision":"BLOCKED","rule":"bank","scope":"Both","body":"leak"}]""",
        ),
        "trinity.mms" to linkedMapOf<String, Any?>("ids" to "m1\nm2"),
    )

    @Test
    fun serializeParse_roundTripPreservesEveryType() {
        val prefs = samplePrefs().toMutableMap()
        prefs["trinity.ui"] = linkedMapOf<String, Any?>(
            "aString" to "hi \"there\"\n🙂",
            "aLong" to 9_999_999_999_999L,
            "anInt" to 7,
            "aBool" to true,
            "aSet" to setOf("x", "y", "z"),
        )
        val backup = SettingsBackup.collect(prefs, 1_700_000_000_000L)
        assertEquals(SettingsBackup.SCHEMA, backup.schema)
        assertEquals(SettingsBackup.serialize(backup).let { SettingsBackup.parse(it) }, backup)
    }

    @Test
    fun parse_rejectsBadSchemaAndMalformedInput() {
        assertNull(SettingsBackup.parse("not json"))
        assertNull(SettingsBackup.parse(""))
        assertNull(SettingsBackup.parse("{}"))
        assertNull(SettingsBackup.parse("[]"))
        assertNull(SettingsBackup.parse("""{"schema":2,"exportedAt":1,"prefs":{}}"""))
        assertNull(SettingsBackup.parse("""{"schema":1.0,"exportedAt":1,"prefs":{}}"""))
        assertNull(SettingsBackup.parse("""{"schema":1,"prefs":{}}"""))
        assertNull(SettingsBackup.parse("""{"schema":1,"exportedAt":1,"prefs":[]}"""))
    }

    @Test
    fun parse_validatesEveryTaggedValueBeforeReturning() {
        val badTag = """{"schema":1,"exportedAt":1,"prefs":{"trinity.ui":{"k":{"t":"bogus","v":1}}}}"""
        assertNull(SettingsBackup.parse(badTag))
        val wrongType = """{"schema":1,"exportedAt":1,"prefs":{"trinity.ui":{"k":{"t":"int","v":"one"}}}}"""
        assertNull(SettingsBackup.parse(wrongType))
        val badSet = """{"schema":1,"exportedAt":1,"prefs":{"trinity.ui":{"k":{"t":"set","v":[1,2]}}}}"""
        assertNull(SettingsBackup.parse(badSet))
        // A late bad value rejects the whole file rather than applying part of it.
        val lateBad = """
            {"schema":1,"exportedAt":1,"prefs":{
              "trinity.ui":{"k":{"t":"int","v":1}},
              "trinity.sims":{"bad":{"t":"bool","v":"false"}}
            }}
        """.trimIndent()
        assertNull(SettingsBackup.parse(lateBad))
    }

    @Test
    fun collect_excludesMessageBodiesAndHeldTexts() {
        val backup = SettingsBackup.collect(samplePrefs(), 1L)
        val json = SettingsBackup.serialize(backup)

        assertFalse(json.contains("draft."))
        assertFalse(json.contains("\"body\""))
        assertFalse(json.contains("pending secret"))
        assertFalse(json.contains("unsent secret"))
        assertFalse(json.contains("lastError"))
        assertFalse(json.contains("trinity.quarantine"))

        val parsed = SettingsBackup.parse(json)
        assertNotNull(parsed)
        assertFalse("trinity.quarantine" in parsed!!.prefs)

        val threads = parsed.prefs["trinity.threads"]!!
        assertTrue("muted" in threads)
        assertTrue("seen.7" in threads)
        assertFalse("draft.7" in threads)

        assertFalse("trinity.scheduled" in parsed.prefs)

        val audit = parsed.prefs["trinity.audit"]!!["entries"] as String
        assertTrue(audit.contains("decision"))
        assertFalse(audit.contains("body"))
    }

    @Test
    fun sanitize_ignoresForeignFilesAndEmptyFiles() {
        val raw = mapOf(
            "trinity.quarantine" to mapOf<String, Any?>("entries" to "held text"),
            "trinity.threads" to mapOf<String, Any?>("draft.1" to "text"),
            "trinity.rules" to mapOf<String, Any?>("names" to "[]"),
        )
        val clean = SettingsBackup.sanitize(raw)
        assertEquals(setOf("trinity.rules"), clean.keys)
    }

    @Test
    fun dryRunCount_matchesFilesThatWouldBeRestored() {
        val backup = SettingsBackup.collect(samplePrefs(), 1L)
        val json = SettingsBackup.serialize(backup)
        // Six owned files; trinity.audit has filtered nested entries.
        assertEquals(6, SettingsBackup.countRestorableFiles(json))

        // A file with nothing restorable counts zero; a bad file counts as null.
        val empty = SettingsBackup.serialize(SettingsBackup.collect(emptyMap(), 1L))
        assertEquals(0, SettingsBackup.countRestorableFiles(empty))
        assertNull(SettingsBackup.countRestorableFiles("not json"))
        assertNull(SettingsBackup.countRestorableFiles("""{"schema":99,"exportedAt":1,"prefs":{}}"""))
    }
}
