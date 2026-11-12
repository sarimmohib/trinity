package com.sarim.trinity.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The phone's own call log, newest first. Names come from the contact row when
     * the log carries one, and from the address book otherwise, so a number you
     * have saved never shows up as a wall of digits.
     */
    suspend fun load(
        limit: Int = 200,
        rules: BlockStore.Rules,
        names: Map<String, String> = emptyMap(),
        cards: List<SimCard> = emptyList(),
    ): List<CallEntry> = withContext(Dispatchers.IO) {
        val sims = Sims(context)
        if (!hasPermission()) return@withContext emptyList()
        val out = ArrayList<CallEntry>()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext() && out.size < limit) {
                    val number = cursor.getString(1).orEmpty()
                    val cached = cursor.getString(2)
                    val type = cursor.getInt(3)
                    val name = cached?.takeIf { it.isNotBlank() } ?: nameFor(number, names)
                    val account = runCatching { cursor.getString(6) }.getOrNull()
                    out += CallEntry(
                        id = cursor.getLong(0),
                        number = number,
                        name = name,
                        outgoing = type == CallLog.Calls.OUTGOING_TYPE,
                        missed = type == CallLog.Calls.MISSED_TYPE ||
                            type == CallLog.Calls.REJECTED_TYPE,
                        date = cursor.getLong(4),
                        durationSeconds = cursor.getLong(5),
                        rule = BlockMatcher.match(name, number, null, rules, Scope.Calls),
                        simLabel = sims.labelForAccount(account, cards),
                    )
                }
            }
        }
        out
    }

    private fun nameFor(number: String, names: Map<String, String>): String? {
        if (number.isBlank()) return null
        names[number]?.let { return it }
        val digits = number.filter { it.isDigit() }
        return names.entries.firstOrNull { (saved, _) ->
            BlockMatcher.sameNumber(saved, digits)
        }?.value
    }

    /** Removes one log entry. Needs WRITE_CALL_LOG; silently does nothing without it. */
    suspend fun delete(id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext false
        runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ?",
                arrayOf(id.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /** Clears the whole log. Needs WRITE_CALL_LOG. */
    suspend fun deleteAll(): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite()) return@withContext false
        runCatching {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        }.getOrDefault(false)
    }
}
