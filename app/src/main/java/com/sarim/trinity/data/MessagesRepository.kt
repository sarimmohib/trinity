package com.sarim.trinity.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.sarim.trinity.mms.MMS_TYPE_INCOMING
import com.sarim.trinity.mms.MMS_TYPE_OUTGOING
import com.sarim.trinity.mms.MmsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MMS provider ids can collide with SMS ids, while Compose keys must be unique.
 * MMS rows are shifted into a far-negative band; ordinary SMS keep their positive
 * row ids and pending rows keep the small negative ids they already borrow.
 * The real provider id is kept separately in [Message.mmsId].
 */
const val MMS_ID_OFFSET = Long.MIN_VALUE / 4

/**
 * True for MMS ids shifted into the far-negative band. Pending rows use small negative
 * ids (-1, -2, …), so the midpoint of the band cleanly separates the two without ever
 * mistaking an unsent bubble for a provider row.
 */
fun isMmsMessageId(id: Long): Boolean = id < 0 && id < MMS_ID_OFFSET / 2

/**
 * MMS `date` is seconds on most devices while SMS `date` is milliseconds. Values
 * below this ceiling are treated as seconds; it sits well above any real epoch in
 * seconds and well below any real epoch in milliseconds.
 */
private const val MMS_SECONDS_CEILING = 100_000_000_000L

class MessagesRepository(private val context: Context) {

    private val contactsRepo = ContactsRepository(context)

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
        val lookup = NameLookup(contactsRepo, names)
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
                    val name = lookup.of(address)
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
        // One bounded pass over MMS so a newer picture message can own its thread's
        // snippet. A single row avoids a query per conversation on every refresh;
        // threads whose newest item is an SMS are untouched.
        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.SUBJECT,
                    "text",
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_TYPE,
                ),
                null,
                null,
                "${Telephony.Mms.DATE} DESC LIMIT 1",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val threadId = cursor.getLong(1)
                    val date = mmsDateToMillis(cursor.getLong(4))
                    val type = cursor.getInt(5)
                    val body = mmsBody(cursor.getString(2), cursor.getString(3))
                    val existing = byThread[threadId]
                    if (existing == null || date > existing.date) {
                        val address = mmsAddress(id, type == MMS_TYPE_INCOMING)
                        val name = lookup.of(address)
                        byThread[threadId] = Conversation(
                            threadId = threadId,
                            address = address,
                            name = name,
                            snippet = body.replace('\n', ' ').trim(),
                            date = date,
                            unread = 0,
                            rule = BlockMatcher.match(name, address, body, rules, Scope.Texts),
                            lastOutgoing = type == MMS_TYPE_OUTGOING,
                            muted = muted.contains(threadId),
                        )
                    }
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

    /** MMS stores `date` in seconds on most devices; normalise to millis for sorting. */
    private fun mmsDateToMillis(raw: Long): Long =
        if (raw < MMS_SECONDS_CEILING) raw * 1000 else raw

    /** A picture message often has no text: use its subject, then a fixed label. */
    private fun mmsBody(subject: String?, text: String?): String {
        val parts = listOfNotNull(
            subject?.takeIf { it.isNotBlank() },
            text?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" ").ifBlank { "Picture message" }
    }

    /** Resolve one MMS sender/recipient from its addr table; blank when unreadable. */
    private fun mmsAddress(messageId: Long, incoming: Boolean): String {
        val wanted = if (incoming) 137 else 151 // PduHeaders.FROM / TO
        val alsoWanted = if (incoming) null else 153 // CC
        return runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null,
                null,
                null,
            )?.use { c ->
                val found = ArrayList<String>()
                while (c.moveToNext()) {
                    val address = c.getString(0) ?: continue
                    if (address.isBlank()) continue
                    val type = c.getInt(1)
                    if (type == wanted || type == alsoWanted) found.add(address)
                }
                found.firstOrNull()
            }
        }.getOrNull().orEmpty()
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
        runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID),
                "${Telephony.Mms.READ} = 0 AND ${Telephony.Mms.MESSAGE_TYPE} = $MMS_TYPE_INCOMING",
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
            // A thread is mostly one sender: look the name up once, not once per text.
            val lookup = NameLookup(contactsRepo)
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
                                lookup.of(address),
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
            // Picture and group messages live in content://mms, not content://sms.
            val mmsProjection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_TYPE,
                Telephony.Mms.SUBJECT,
                "text",
                "msg_id",
                Telephony.Mms.READ,
            )
            runCatching {
                context.contentResolver.query(
                    Telephony.Mms.CONTENT_URI,
                    mmsProjection,
                    "${Telephony.Mms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val providerId = cursor.getLong(0)
                        val type = cursor.getInt(2)
                        val incoming = type == MMS_TYPE_INCOMING
                        val address = mmsAddress(providerId, incoming)
                        val body = mmsBody(cursor.getString(3), cursor.getString(4))
                        out += Message(
                            id = MMS_ID_OFFSET + providerId,
                            address = address,
                            body = body,
                            date = mmsDateToMillis(cursor.getLong(1)),
                            outgoing = !incoming,
                            rule = BlockMatcher.match(
                                lookup.of(address),
                                address,
                                body,
                                rules,
                                Scope.Texts,
                            ),
                            mmsId = providerId,
                            attachmentUri = runCatching {
                                MmsStore.firstImageUri(context, providerId)
                            }.getOrNull(),
                        )
                    }
                }
            }
            out.sortedBy { it.date }
        }

    /** Everything currently hidden by the rules, newest first. */
    suspend fun blockedMessages(names: Map<String, String>, rules: BlockStore.Rules): List<Message> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            // With no rules nothing can match, so there is no reason to read every text.
            if (rules.isEmpty) return@withContext emptyList()
            val lookup = NameLookup(contactsRepo, names)
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
                            lookup.of(address),
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
            val sms = context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            ) > 0
            val mms = runCatching {
                context.contentResolver.update(
                    Telephony.Mms.CONTENT_URI,
                    ContentValues().apply { put(Telephony.Mms.READ, 1) },
                    "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                    arrayOf(threadId.toString()),
                ) > 0
            }.getOrDefault(false)
            sms || mms
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
            if (isMmsMessageId(id)) {
                // MMS rows are shifted into the far-negative band for Compose keys.
                val providerId = id - MMS_ID_OFFSET
                context.contentResolver.delete(
                    ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId),
                    null,
                    null,
                ) > 0
            } else {
                context.contentResolver.delete(
                    ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                    null,
                    null,
                ) > 0
            }
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

}

/**
 * Names for the addresses one pass over the SMS table runs into. The address book is
 * asked about each distinct address once — including the ones it has no name for —
 * where it used to be asked again for every text from the same sender.
 */
private class NameLookup(
    private val contacts: ContactsRepository,
    private val known: Map<String, String> = emptyMap(),
) {
    private val seen = HashMap<String, String?>()

    suspend fun of(address: String): String? {
        known[address]?.let { return it }
        if (seen.containsKey(address)) return seen[address]
        return contacts.nameFor(address).also { seen[address] = it }
    }
}

/** Writes Trinity's own sent messages back into the provider, as the SMS role requires. */
object SmsStore {

    /** The thread a number belongs to, creating it if this is a first contact. */
    fun threadIdFor(context: Context, address: String): Long? = runCatching {
        Telephony.Threads.getOrCreateThreadId(context, address)
    }.getOrNull()

    fun insertSent(
        context: Context,
        address: String,
        body: String,
        date: Long,
        subscriptionId: Int? = null,
    ): Long? = runCatching {
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
            subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }
        context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()
}
