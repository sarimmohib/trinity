package com.sarim.trinity.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sarim.trinity.sms.holdsSmsRole

/**
 * A portable, versioned copy of the text inbox.
 *
 * v1 covers `content://sms` only. MMS lives in `content://mms` plus its part and addr
 * tables and is deliberately not included — the restore/backup sheet must say so.
 * The codec is pure Kotlin so a file can be validated and counted without a provider.
 */
object MessageBackup {

    const val SCHEMA = 1

    data class Backup(val schema: Int, val exportedAt: Long, val threads: List<Thread>)

    data class Thread(val address: String, val messages: List<Entry>)

    data class Entry(val date: Long, val type: Int, val body: String, val read: Int)

    fun serialize(backup: Backup): String = TrinityJson.obj(
        "schema" to backup.schema.toString(),
        "exportedAt" to backup.exportedAt.toString(),
        "threads" to backup.threads.joinToString(",", "[", "]") { thread ->
            TrinityJson.obj(
                "address" to TrinityJson.quote(thread.address),
                "messages" to thread.messages.joinToString(",", "[", "]") { message ->
                    TrinityJson.obj(
                        "date" to message.date.toString(),
                        "type" to message.type.toString(),
                        "body" to TrinityJson.quote(message.body),
                        "read" to message.read.toString(),
                    )
                },
            )
        },
    )

    /** Null on a bad schema, malformed JSON, or any field that fails validation. */
    fun parse(raw: String): Backup? = runCatching { parseOrThrow(raw) }.getOrNull()

    private fun parseOrThrow(raw: String): Backup {
        val root = TrinityJson.parse(raw).asObj()
            ?: throw IllegalArgumentException("root must be an object")
        if (root["schema"]?.asExactLong() != SCHEMA.toLong()) {
            throw IllegalArgumentException("unsupported schema")
        }
        val exportedAt = root["exportedAt"]?.asExactLong()
            ?: throw IllegalArgumentException("exportedAt missing")
        val threads = root["threads"]?.asArr()
            ?: throw IllegalArgumentException("threads missing")
        return Backup(
            schema = SCHEMA,
            exportedAt = exportedAt,
            threads = threads.map { value ->
                val obj = value.asObj() ?: throw IllegalArgumentException("thread must be an object")
                val address = obj["address"]?.asString()
                    ?: throw IllegalArgumentException("address missing")
                val messages = obj["messages"]?.asArr()
                    ?: throw IllegalArgumentException("messages missing")
                Thread(
                    address = address,
                    messages = messages.map { entry ->
                        val message = entry.asObj()
                            ?: throw IllegalArgumentException("message must be an object")
                        Entry(
                            date = message["date"]?.asExactLong()
                                ?: throw IllegalArgumentException("date missing"),
                            type = message["type"]?.asExactLong()?.toInt()
                                ?: throw IllegalArgumentException("type missing"),
                            body = message["body"]?.asString()
                                ?: throw IllegalArgumentException("body missing"),
                            read = message["read"]?.asExactLong()?.toInt()
                                ?: throw IllegalArgumentException("read missing"),
                        )
                    },
                )
            },
        )
    }

    fun count(backup: Backup): Int = backup.threads.sumOf { it.messages.size }

    /** Reads the SMS provider and writes JSON to [uri]; null unless the SMS role is held. */
    fun export(context: Context, uri: Uri): Int? {
        if (!holdsSmsRole(context)) return null
        val rows = readRows(context) ?: return null
        val byAddress = LinkedHashMap<String, MutableList<Entry>>()
        rows.forEach { (address, entry) ->
            byAddress.getOrPut(address) { ArrayList() } += entry
        }
        val backup = Backup(
            schema = SCHEMA,
            exportedAt = System.currentTimeMillis(),
            threads = byAddress.map { (address, messages) -> Thread(address, messages) },
        )
        val written = runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
            stream.use { it.write(serialize(backup).toByteArray(Charsets.UTF_8)) }
            true
        }.getOrDefault(false)
        return if (written) count(backup) else null
    }

    /** How many rows a restore would insert. Writes nothing; needs the SMS role to dedupe. */
    fun dryRun(context: Context, uri: Uri): Int? {
        if (!holdsSmsRole(context)) return null
        val backup = readBackup(context, uri) ?: return null
        val existing = existingKeys(context)
        return dedupe(existing, backup.keys()).size
    }

    /**
     * Inserts every row in the file that is not already in the provider. The file is
     * parsed and validated in full before a single insert, so a malformed backup never
     * leaves a half-restored inbox.
     */
    fun restore(context: Context, uri: Uri): Int? {
        if (!holdsSmsRole(context)) return null
        val backup = readBackup(context, uri) ?: return null
        val existing = existingKeys(context)
        val candidates = backup.entriesWithKeys()
        val accepted = dedupe(existing, candidates.map { it.second }).toHashSet()
        val done = HashSet<MessageKey>()
        var inserted = 0
        candidates.forEach { (entry, key) ->
            if (key !in accepted || !done.add(key)) return@forEach
            val ok = runCatching {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, key.address)
                    put(Telephony.Sms.BODY, entry.body)
                    put(Telephony.Sms.DATE, entry.date)
                    put(Telephony.Sms.DATE_SENT, entry.date)
                    // Preserve the original row state rather than calling insertSent,
                    // which always writes TYPE=sent and READ=1.
                    put(Telephony.Sms.TYPE, entry.type)
                    put(Telephony.Sms.READ, entry.read)
                    put(Telephony.Sms.SEEN, entry.read)
                    SmsStore.threadIdFor(context, key.address)?.let {
                        put(Telephony.Sms.THREAD_ID, it)
                    }
                }
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null
            }.getOrDefault(false)
            if (ok) inserted++
        }
        return inserted
    }

    private fun readBackup(context: Context, uri: Uri): Backup? = runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        val raw = stream.use { String(it.readBytes(), Charsets.UTF_8) }
        parse(raw)
    }.getOrNull()

    private fun readRows(context: Context): List<Pair<String, Entry>>? = runCatching {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY,
            Telephony.Sms.READ,
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val out = ArrayList<Pair<String, Entry>>()
            while (cursor.moveToNext()) {
                val address = cursor.getString(0).orEmpty()
                out += address to Entry(
                    date = cursor.getLong(1),
                    type = cursor.getInt(2),
                    body = cursor.getString(3).orEmpty(),
                    read = cursor.getInt(4),
                )
            }
            out
        }
    }.getOrNull()

    private fun existingKeys(context: Context): Set<MessageKey> = runCatching {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.BODY,
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val out = HashSet<MessageKey>()
            while (cursor.moveToNext()) {
                out += MessageKey(
                    address = cursor.getString(0).orEmpty(),
                    date = cursor.getLong(1),
                    type = cursor.getInt(2),
                    body = cursor.getString(3).orEmpty(),
                )
            }
            out
        } ?: emptySet()
    }.getOrDefault(emptySet())

    private fun Backup.keys(): List<MessageKey> = entriesWithKeys().map { it.second }

    private fun Backup.entriesWithKeys(): List<Pair<Entry, MessageKey>> =
        threads.flatMap { thread ->
            thread.messages.map { entry -> entry to MessageKey(thread.address, entry.date, entry.type, entry.body) }
        }
}

/** Identity of a message in the provider. `read` is intentionally not part of it. */
internal data class MessageKey(val address: String, val date: Long, val type: Int, val body: String)

/** Keeps the first occurrence of each key, including duplicates within [incoming]. */
internal fun dedupe(existing: Collection<MessageKey>, incoming: List<MessageKey>): List<MessageKey> {
    val seen = HashSet(existing)
    return incoming.filter { seen.add(it) }
}
