package com.sarim.trinity.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sarim.trinity.mms.MMS_TYPE_INCOMING

/** One message that matched a search, with the thread to open and the text to emphasise. */
data class MessageHit(
    val message: Message,
    val threadId: Long,
    val highlighted: String?,
)

/**
 * Unified search over the phone's own stores. The ranking here is pure JVM so it can be
 * tested without a device; only [messages] touches a ContentResolver, and the caller runs
 * that off the main thread.
 */
object Search {

    /** Hard cap on the MESSAGES section, and on each provider read that feeds it. */
    private const val MAX_RESULTS = 100

    /** MMS stores `date` in seconds on most devices while SMS stores milliseconds. */
    private const val MMS_SECONDS_CEILING = 100_000_000_000L

    /**
     * Escapes the three LIKE metacharacters so a typed `%`, `_` or `\` is searched for
     * literally instead of widening the pattern. Backslash must be escaped first or the
     * two escapes after it would be escaped twice.
     */
    internal fun escapeLike(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    /**
     * How well [query] matches [text]: 0 = no match, 4 = exact normalised number,
     * 3 = name prefix, 2 = word boundary, 1 = substring. The tiers are ordered so a
     * caller can sort descending and keep the best hit at the top.
     *
     * Queries shorter than three characters are ignored, so a one-letter query never
     * lights up the whole phone. All-digit queries follow `sameNumber`'s five-digit
     * floor — a five-digit (or longer) number is the one short form that is useful.
     */
    fun rank(query: String, text: String): Int {
        val q = query.trim()
        if (q.length < 3) return 0

        // Tier 4 — the same number after normalisation. Reusing the rule engine's own
        // comparison keeps "+92 300 7712008" and "0300-7712008" as one number here too.
        if (BlockMatcher.sameNumber(q, text)) return 4

        val lowerText = text.lowercase()
        val lowerQuery = q.lowercase()

        // Tier 3 — a label that begins with the query.
        if (lowerText.startsWith(lowerQuery)) return 3

        // Tier 2 — the query begins a word (start of text or after a non-alphanumeric).
        var from = 0
        while (true) {
            val at = lowerText.indexOf(lowerQuery, from)
            if (at < 0) break
            val before = if (at == 0) null else lowerText[at - 1]
            if (before == null || !before.isLetterOrDigit()) return 2
            from = at + 1
        }

        // Tier 1 — present anywhere, including inside another word.
        if (lowerText.contains(lowerQuery)) return 1
        return 0
    }

    /**
     * Bounded search of the SMS and MMS providers, newest hit first inside each rank
     * tier. The query only ever reaches a `selectionArgs` slot: the selection string is
     * a fixed literal with `ESCAPE '\'` so `%`, `_` and `\` in a query stay literal.
     */
    fun messages(
        context: Context,
        query: String,
        rules: BlockStore.Rules,
        names: Map<String, String> = emptyMap(),
    ): List<MessageHit> {
        val q = query.trim()
        if (q.length < 3) return emptyList()

        val escaped = escapeLike(q)
        val pattern = "%$escaped%"
        // One mutable result list, shared by both providers, with dedupe by message id.
        val found = ArrayList<Pair<Int, MessageHit>>()
        val seen = HashSet<Long>()

        runCatching {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.THREAD_ID,
            )
            val selection = "${Telephony.Sms.BODY} LIKE ? ESCAPE '\\' OR " +
                "${Telephony.Sms.ADDRESS} LIKE ? ESCAPE '\\'"
            val args = arrayOf(pattern, pattern)
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Sms.DATE} DESC LIMIT $MAX_RESULTS",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    if (!seen.add(id)) continue
                    val address = cursor.getString(1).orEmpty()
                    val body = cursor.getString(2).orEmpty()
                    val date = cursor.getLong(3)
                    val type = cursor.getInt(4)
                    val threadId = cursor.getLong(5)
                    val name = names[address]
                    val score = score(q, body, address, name)
                    if (score == 0) continue
                    val message = Message(
                        id = id,
                        address = address,
                        body = body,
                        date = date,
                        outgoing = isOutgoingSms(type),
                        rule = BlockMatcher.match(name, address, body, rules, Scope.Texts),
                    )
                    found += score to MessageHit(message, threadId, snippet(body, name, address, q))
                }
            }
        }

        runCatching {
            val projection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.SUBJECT,
                "text",
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_TYPE,
            )
            val selection = "${Telephony.Mms.SUBJECT} LIKE ? ESCAPE '\\' OR " +
                "text LIKE ? ESCAPE '\\'"
            val args = arrayOf(pattern, pattern)
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                args,
                "${Telephony.Mms.DATE} DESC LIMIT $MAX_RESULTS",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val providerId = cursor.getLong(0)
                    // Keep the same far-negative id band the thread view uses, so an
                    // MMS hit opens exactly the bubble the Thread screen will show.
                    val shiftedId = MMS_ID_OFFSET + providerId
                    if (!seen.add(shiftedId)) continue
                    val threadId = cursor.getLong(1)
                    val body = mmsBody(cursor.getString(2), cursor.getString(3))
                    val date = mmsDateToMillis(cursor.getLong(4))
                    val incoming = cursor.getInt(5) == MMS_TYPE_INCOMING
                    val address = mmsAddress(context, providerId, incoming)
                    val name = names[address]
                    val score = score(q, body, address, name)
                    if (score == 0) continue
                    val message = Message(
                        id = shiftedId,
                        address = address,
                        body = body,
                        date = date,
                        outgoing = !incoming,
                        rule = BlockMatcher.match(name, address, body, rules, Scope.Texts),
                        mmsId = providerId,
                    )
                    found += score to MessageHit(message, threadId, snippet(body, name, address, q))
                }
            }
        }

        return found
            .sortedWith(
                compareByDescending<Pair<Int, MessageHit>> { it.first }
                    .thenByDescending { it.second.message.date },
            )
            .take(MAX_RESULTS)
            .map { it.second }
    }

    /** In-memory call filter: best rank first, newest first when ranks tie. */
    fun calls(query: String, calls: List<CallEntry>): List<CallEntry> =
        calls.mapNotNull { entry ->
            val score = maxOf(rank(query, entry.name.orEmpty()), rank(query, entry.number))
            if (score == 0) null else score to entry
        }
            .sortedWith(
                compareByDescending<Pair<Int, CallEntry>> { it.first }
                    .thenByDescending { it.second.date },
            )
            .map { it.second }

    /** In-memory contact filter: best rank first, alphabetical by name when ranks tie. */
    fun contacts(query: String, contacts: List<Person>): List<Person> =
        contacts.mapNotNull { person ->
            val numberScore = person.allNumbers.maxOf { rank(query, it.number) }
            val score = maxOf(rank(query, person.name), rank(query, person.number), numberScore)
            if (score == 0) null else score to person
        }
            .sortedWith(
                compareByDescending<Pair<Int, Person>> { it.first }
                    .thenBy { it.second.name.lowercase() },
            )
            .map { it.second }

    /** Best tier across every field a message row can match. */
    private fun score(query: String, body: String, address: String, name: String?): Int =
        maxOf(rank(query, body), rank(query, address), if (name == null) 0 else rank(query, name))

    /**
     * The text a result row shows: a short window around the hit in the body when the
     * body matched, otherwise the label or number that did. Keeping this with the hit
     * means the screen never has to guess which field earned the match.
     */
    private fun snippet(body: String, name: String?, address: String, query: String): String {
        val flat = body.replace('\n', ' ').trim()
        if (flat.contains(query, ignoreCase = true)) {
            val at = flat.indexOf(query, ignoreCase = true)
            val start = (at - 32).coerceAtLeast(0)
            val end = (at + query.length + 48).coerceAtMost(flat.length)
            return buildString {
                if (start > 0) append('…')
                append(flat.substring(start, end).trim())
                if (end < flat.length) append('…')
            }
        }
        if (name?.contains(query, ignoreCase = true) == true) return name
        if (address.contains(query, ignoreCase = true) || BlockMatcher.sameNumber(query, address)) {
            return address
        }
        return flat.take(96)
    }

    private fun isOutgoingSms(type: Int): Boolean = when (type) {
        Telephony.Sms.MESSAGE_TYPE_SENT,
        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
        Telephony.Sms.MESSAGE_TYPE_QUEUED,
        Telephony.Sms.MESSAGE_TYPE_FAILED,
        -> true
        else -> false
    }

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
    private fun mmsAddress(context: Context, messageId: Long, incoming: Boolean): String {
        val wanted = if (incoming) 137 else 151 // PduHeaders.FROM / TO
        val alsoWanted = if (incoming) null else 153 // CC
        return runCatching {
            context.contentResolver.query(
                Uri.parse("content://mms/$messageId/addr"),
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null,
                null,
                null,
            )?.use { cursor ->
                val found = ArrayList<String>()
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: continue
                    if (address.isBlank()) continue
                    val type = cursor.getInt(1)
                    if (type == wanted || type == alsoWanted) found.add(address)
                }
                found.firstOrNull()
            }
        }.getOrNull().orEmpty()
    }
}
