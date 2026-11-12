package com.sarim.trinity.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessagesRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Some devices (and Windows Subsystem for Android) ship no telephony provider
     * at all. Asking whether the URI resolves beats discovering it through an
     * exception on every screen that wants to show an empty inbox.
     */
    suspend fun hasProvider(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                null,
            )?.use { true } ?: false
        }.getOrDefault(false)
    }

    /**
     * Newest message per thread, annotated with the rule that hides it (if any).
     * Blocked conversations are still returned — the caller decides whether to
     * show them behind the "hidden" banner or drop them from the list.
     */
    suspend fun conversations(
        names: Map<String, String>,
        rules: BlockStore.Rules,
        muted: Set<Long> = emptySet(),
    ): List<Conversation> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val byThread = LinkedHashMap<Long, Conversation>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(0)
                    if (byThread.containsKey(threadId)) continue
                    val address = cursor.getString(1) ?: continue
                    val body = cursor.getString(2).orEmpty()
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(5)
                    val name = names[address] ?: nameForAddress(address)
                    byThread[threadId] = Conversation(
                        threadId = threadId,
                        address = address,
                        name = name,
                        snippet = body.replace('\n', ' ').trim(),
                        date = date,
                        unread = 0,
                        rule = BlockMatcher.match(name, address, body, rules, Scope.Texts),
                        lastOutgoing = isOutgoingType(type),
                        muted = muted.contains(threadId),
                    )
                }
            }
        }
        // Unread counts in one extra pass; cheap enough for a phone's inbox.
        val unread = unreadCounts()
        byThread.values.map { it.copy(unread = unread[it.threadId] ?: 0) }
    }

    private fun isOutgoingType(type: Int): Boolean = when (type) {
        Telephony.Sms.MESSAGE_TYPE_SENT,
        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
        Telephony.Sms.MESSAGE_TYPE_QUEUED,
        Telephony.Sms.MESSAGE_TYPE_FAILED,
        -> true
        else -> false
    }

    private suspend fun unreadCounts(): Map<Long, Int> = withContext(Dispatchers.IO) {
        val counts = HashMap<Long, Int>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${Telephony.Sms.MESSAGE_TYPE_INBOX}",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    counts[id] = (counts[id] ?: 0) + 1
                }
            }
        }
        counts
    }

    /** Total unread across every conversation, for the list header. */
    suspend fun unreadTotal(): Int = withContext(Dispatchers.IO) {
        unreadCounts().values.sum()
    }

    suspend fun messages(threadId: Long, rules: BlockStore.Rules): List<Message> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val out = ArrayList<Message>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
            )
            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                    "${Telephony.Sms.DATE} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(1).orEmpty()
                        val body = cursor.getString(2).orEmpty()
                        val type = cursor.getInt(4)
                        val outgoing = isOutgoingType(type)
                        out += Message(
                            id = cursor.getLong(0),
                            address = address,
                            body = body,
                            date = cursor.getLong(3),
                            outgoing = outgoing,
                            rule = BlockMatcher.match(
                                nameForAddress(address),
                                address,
                                body,
                                rules,
                                Scope.Texts,
                            ),
                            state = when (type) {
                                Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                                Telephony.Sms.MESSAGE_TYPE_QUEUED,
                                -> MessageState.SENDING
                                Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageState.FAILED
                                else -> if (outgoing) MessageState.SENT else MessageState.RECEIVED
                            },
                        )
                    }
                }
            }
            out
        }

    /** Everything currently hidden by the rules, newest first. */
    suspend fun blockedMessages(names: Map<String, String>, rules: BlockStore.Rules): List<Message> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val out = ArrayList<Message>()
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
            )
            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val address = cursor.getString(1).orEmpty()
                        val body = cursor.getString(2).orEmpty()
                        val hit = BlockMatcher.match(
                            names[address] ?: nameForAddress(address),
                            address,
                            body,
                            rules,
                            Scope.Texts,
                        ) ?: continue
                        out += Message(
                            id = cursor.getLong(0),
                            address = address,
                            body = body,
                            date = cursor.getLong(3),
                            outgoing = cursor.getInt(4) == Telephony.Sms.MESSAGE_TYPE_SENT,
                            rule = hit,
                        )
                    }
                }
            }
            out
        }

    /** Opening a thread is what marks it read — the same contract stock apps keep. */
    suspend fun markThreadRead(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    /** Marking unread again flags the newest message only, as stock apps do. */
    suspend fun markThreadUnread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val newest = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1",
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (newest == null) {
                false
            } else {
                val values = ContentValues().apply { put(Telephony.Sms.READ, 0) }
                context.contentResolver.update(
                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, newest),
                    values,
                    null,
                    null,
                ) > 0
            }
        }.getOrDefault(false)
    }

    suspend fun deleteMessage(id: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                null,
                null,
            ) > 0
        }.getOrDefault(false)
    }

    suspend fun deleteThread(threadId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    private suspend fun nameForAddress(address: String): String? =
        ContactsRepository(context).nameFor(address)
}

/** Writes Trinity's own sent messages back into the provider, as the SMS role requires. */
object SmsStore {

    /** The thread a number belongs to, creating it if this is a first contact. */
    fun threadIdFor(context: Context, address: String): Long? = runCatching {
        Telephony.Threads.getOrCreateThreadId(context, address)
    }.getOrNull()

    fun insertSent(context: Context, address: String, body: String, date: Long): Long? = runCatching {
        val threadId = threadIdFor(context, address)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, date)
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            threadId?.let { put(Telephony.Sms.THREAD_ID, it) }
        }
        context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()
}
