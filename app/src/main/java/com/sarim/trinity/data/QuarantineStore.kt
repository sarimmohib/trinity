package com.sarim.trinity.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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
    )

    fun add(entry: Entry) {
        val current = all().toMutableList()
        current.add(0, entry)
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
                    .put("ruleValue", it.ruleValue),
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
                Entry(
                    address = obj.optString("address"),
                    body = obj.optString("body"),
                    date = obj.optLong("date"),
                    ruleKind = obj.optString("ruleKind"),
                    ruleValue = obj.optString("ruleValue"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun removeAt(date: Long, body: String) =
        write(all().filterNot { it.date == date && it.body == body })

    /** Empties the quarantine; the caller confirms first. */
    fun clear() = write(emptyList())
}
