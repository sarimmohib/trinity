package com.sarim.trinity.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Per-thread state Trinity keeps for itself: unsent drafts and muted threads.
 * Deliberately tiny — the provider stays the source of truth for messages.
 */
class ThreadPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("trinity.threads", Context.MODE_PRIVATE)

    fun draft(threadId: Long): String = prefs.getString("draft.$threadId", "").orEmpty()

    fun setDraft(threadId: Long, value: String) {
        prefs.edit().apply {
            if (value.isBlank()) remove("draft.$threadId") else putString("draft.$threadId", value)
        }.apply()
    }

    fun isMuted(threadId: Long): Boolean = muted().contains(threadId)

    fun mutedThreads(): Set<Long> = muted()

    fun setMuted(threadId: Long, muted: Boolean) {
        val next = muted().toMutableSet()
        if (muted) next.add(threadId) else next.remove(threadId)
        prefs.edit().putString("muted", next.joinToString(",")).apply()
    }

    /**
     * The newest message date already shown in a thread. The SMS provider only lets
     * the default SMS app write the read flag, so Trinity remembers this itself and
     * clears the badge from it.
     */
    fun seenUpTo(threadId: Long): Long = prefs.getLong("seen.$threadId", 0L)

    fun markSeen(threadId: Long, date: Long) {
        if (date <= seenUpTo(threadId)) return
        prefs.edit().putLong("seen.$threadId", date).apply()
    }

    fun clearSeen(threadId: Long) {
        prefs.edit().remove("seen.$threadId").apply()
    }

    fun seenThreads(): Map<Long, Long> = prefs.all.entries.mapNotNull { (key, value) ->
        if (!key.startsWith("seen.")) return@mapNotNull null
        val id = key.removePrefix("seen.").toLongOrNull() ?: return@mapNotNull null
        val date = value as? Long ?: return@mapNotNull null
        id to date
    }.toMap()

    private fun muted(): Set<Long> =
        prefs.getString("muted", "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
}
