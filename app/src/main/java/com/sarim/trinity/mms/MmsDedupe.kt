package com.sarim.trinity.mms

import android.content.Context

/**
 * Remembers MMS message ids we have already handled, so a retransmitted WAP push does
 * not create a second message or a second notification. The set is bounded at 200 ids
 * in `trinity.mms`; the pure helpers are exposed as `internal` for JVM tests.
 */
object MmsDedupe {

    private const val PREFS = "trinity.mms"
    private const val KEY = "ids"
    private const val MAX_IDS = 200

    /** False for a null/blank id — a push without one is never suppressed. */
    fun isDuplicate(context: Context, messageId: String?): Boolean {
        val id = normalise(messageId) ?: return false
        return ids(context).contains(id)
    }

    /** Records an id, evicting the oldest when the bounded set is full. */
    fun remember(context: Context, messageId: String?) {
        val id = normalise(messageId) ?: return
        write(context, rememberId(ids(context), id))
    }

    /** Clears an id after a failed download so a retransmitted push can be retried. */
    fun forget(context: Context, messageId: String?) {
        val id = normalise(messageId) ?: return
        write(context, ids(context).filterNot { it == id })
    }

    private fun write(context: Context, ids: List<String>) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, ids.joinToString("\n"))
                .apply()
        }
    }

    internal fun normalise(id: String?): String? = id?.trim()?.takeIf { it.isNotEmpty() }

    internal fun parseIds(raw: String?): List<String> = raw
        ?.split('\n')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeLast(MAX_IDS)
        ?: emptyList()

    internal fun rememberId(current: List<String>, id: String, max: Int = MAX_IDS): List<String> =
        (current.filterNot { it == id } + id).takeLast(max)

    private fun ids(context: Context): List<String> = runCatching {
        parseIds(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null),
        )
    }.getOrDefault(emptyList())
}
