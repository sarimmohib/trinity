package com.sarim.trinity.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * When Trinity holds the SMS role, blocked texts are never written to the system
 * inbox. They are kept here instead, so nothing is silently destroyed and the
 * user can review, restore or delete them from the Blocked screen.
 */
class QuarantineStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("trinity.quarantine", Context.MODE_PRIVATE)

    data class Entry(
        val address: String,
        val body: String,
        val date: Long,
        val ruleKind: String,
        val ruleValue: String,
        /** Distinguishes two otherwise identical held texts; filled in by [add]. */
        val id: String = "",
    )

    fun add(entry: Entry) {
        val current = all().toMutableList()
        // Callers need not know about the id: a freshly held text gets one here.
        current.add(0, if (entry.id.isBlank()) entry.copy(id = UUID.randomUUID().toString()) else entry)
        write(current.take(200))
    }

    /** Forgets every held text from a sender the user just vouched for. */
    fun removeFor(address: String) = write(all().filterNot { BlockMatcher.sameNumber(address, it.address) })

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach {
            array.put(
                JSONObject()
                    .put("address", it.address)
                    .put("body", it.body)
                    .put("date", it.date)
                    .put("ruleKind", it.ruleKind)
                    .put("ruleValue", it.ruleValue)
                    .put("id", it.id),
            )
        }
        prefs.edit().putString("entries", array.toString()).apply()
    }

    fun all(): List<Entry> {
        val raw = prefs.getString("entries", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                val address = obj.optString("address")
                val body = obj.optString("body")
                val date = obj.optLong("date")
                Entry(
                    address = address,
                    body = body,
                    date = date,
                    ruleKind = obj.optString("ruleKind"),
                    ruleValue = obj.optString("ruleValue"),
                    // Files written before ids existed still load: derive a stable one.
                    id = obj.optString("id").ifBlank { "$date|$address|$body" },
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Drops one held text by its stable id, leaving identical neighbours alone. */
    fun remove(id: String) = write(all().filterNot { it.id == id })

    /**
     * Drops the first held text with this timestamp and body. Kept for old callers;
     * two identical texts used to share these values, so use [remove] where possible.
     */
    @Deprecated("Use remove(id) so only the intended entry is deleted")
    fun removeAt(date: Long, body: String) {
        val entries = all().toMutableList()
        val index = entries.indexOfFirst { it.date == date && it.body == body }
        if (index >= 0) entries.removeAt(index)
        write(entries)
    }

    /** Empties the quarantine; the caller confirms first. */
    fun clear() = write(emptyList())
}
