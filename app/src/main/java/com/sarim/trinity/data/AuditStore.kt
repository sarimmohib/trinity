package com.sarim.trinity.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A bounded log of the last blocking decisions, newest first. Entries carry only
 * the rule text that fired and the address it fired on — never a message body.
 */
class AuditStore(context: Context) {

    data class Entry(
        val at: Long,
        val kind: String,
        val address: String,
        val decision: String,
        val rule: String,
        val scope: String,
    )

    private val prefs = context.applicationContext.getSharedPreferences("trinity.audit", Context.MODE_PRIVATE)

    /**
     * Prepends the newest decision and trims the log to the most recent 500 entries.
     * An identical decision for the same address within [DEDUPE_MS] is collapsed: the
     * call log is re-read on every refresh, and repeating those rows would drown out
     * real decisions.
     */
    fun record(entry: Entry) {
        val current = all()
        // Scan the whole recent window, not just the newest row: a refresh can contain
        // several blocked callers, and comparing only the head would re-add the others.
        val duplicate = current.takeWhile { entry.at - it.at in 0..DEDUPE_MS }
            .any { existing ->
                existing.kind == entry.kind &&
                    existing.address == entry.address &&
                    existing.decision == entry.decision &&
                    existing.rule == entry.rule &&
                    existing.scope == entry.scope
            }
        if (duplicate) return
        write((listOf(entry) + current).take(MAX))
    }

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    at = obj.optLong("at"),
                    kind = obj.optString("kind"),
                    address = obj.optString("address"),
                    decision = obj.optString("decision"),
                    rule = obj.optString("rule"),
                    scope = obj.optString("scope"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("at", entry.at)
                    .put("kind", entry.kind)
                    .put("address", entry.address)
                    .put("decision", entry.decision)
                    .put("rule", entry.rule)
                    .put("scope", entry.scope),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "entries"
        private const val MAX = 500
        private const val DEDUPE_MS = 60_000L
    }
}
