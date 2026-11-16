package com.sarim.trinity.data

import android.content.Context
import java.util.UUID

internal const val MAX_SCHEDULED = 200
internal const val SCHEDULED_ID_KEY = "scheduled_id"

private const val PREFS_NAME = "trinity.scheduled"
private const val KEY_ENTRIES = "entries"

/** One pending text. [id] is assigned by [ScheduledStore.add] when it arrives blank. */
data class ScheduledMessage(
    val id: String,
    val address: String,
    val body: String,
    val sendAt: Long,
    val subscriptionId: Int? = null,
    val lastError: String? = null,
)

/**
 * The queue of texts waiting to go out, kept as JSON in `trinity.scheduled`.
 * A missing file, an empty file and malformed JSON all read as an empty queue.
 */
class ScheduledStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Newest send time first, so a caller can show the queue in a stable order. */
    fun all(): List<ScheduledMessage> = parseScheduled(prefs.getString(KEY_ENTRIES, null))
        .sortedByDescending { it.sendAt }

    fun add(message: ScheduledMessage) {
        write(listOf(withId(message)) + all())
    }

    fun remove(id: String) = write(all().filterNot { it.id == id })

    /** Replaces the entry with the same id; an unknown id is appended instead of lost. */
    fun update(message: ScheduledMessage) {
        val current = all()
        write(
            if (current.any { it.id == message.id }) {
                current.map { if (it.id == message.id) message else it }
            } else {
                listOf(message) + current
            },
        )
    }

    fun due(now: Long = System.currentTimeMillis()): List<ScheduledMessage> = scheduledDue(all(), now)

    private fun write(messages: List<ScheduledMessage>) {
        prefs.edit().putString(KEY_ENTRIES, scheduledJson(capScheduled(messages))).apply()
    }
}

object ScheduleDispatcher {

    /**
     * WorkManager is inexact: the job is guaranteed no earlier than [ScheduledMessage.sendAt],
     * not at that millisecond. A pending card must say "around 14:30"; no exact-alarm
     * permission is requested for this feature.
     */
    fun enqueue(context: Context, message: ScheduledMessage) {
        val delay = maxOf(0L, message.sendAt - System.currentTimeMillis())
        val request = androidx.work.OneTimeWorkRequestBuilder<com.sarim.trinity.work.SendScheduledWorker>()
            .setInputData(androidx.work.workDataOf(SCHEDULED_ID_KEY to message.id))
            .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        androidx.work.WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(
                "send.${message.id}",
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
    }

    fun cancel(context: Context, id: String) {
        androidx.work.WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork("send.$id")
    }
}

// ---- Pure helpers (no Android calls, so JVM tests can round-trip the queue) --------

internal fun withId(message: ScheduledMessage): ScheduledMessage =
    if (message.id.isBlank()) message.copy(id = UUID.randomUUID().toString()) else message

internal fun capScheduled(messages: List<ScheduledMessage>): List<ScheduledMessage> =
    messages.take(MAX_SCHEDULED)

internal fun scheduledDue(messages: List<ScheduledMessage>, now: Long): List<ScheduledMessage> =
    messages.filter { it.sendAt <= now }

internal fun scheduledJson(messages: List<ScheduledMessage>): String =
    messages.joinToString(",", "[", "]") { message ->
        TrinityJson.obj(
            "id" to TrinityJson.quote(message.id),
            "address" to TrinityJson.quote(message.address),
            "body" to TrinityJson.quote(message.body),
            "sendAt" to message.sendAt.toString(),
            "subscriptionId" to (message.subscriptionId?.toString() ?: "null"),
            "lastError" to (message.lastError?.let(TrinityJson::quote) ?: "null"),
        )
    }

/** Never throws: a broken file yields an empty queue and broken entries are skipped. */
internal fun parseScheduled(raw: String?): List<ScheduledMessage> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = TrinityJson.parse(raw).asArr() ?: return emptyList()
        array.mapNotNull { value ->
            val obj = value.asObj() ?: return@mapNotNull null
            val address = obj["address"]?.asString()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val body = obj["body"]?.asString() ?: return@mapNotNull null
            val sendAt = obj["sendAt"]?.asExactLong() ?: return@mapNotNull null
            ScheduledMessage(
                // Older files had no id; derive a stable one so they can still be removed.
                id = obj["id"]?.asString()?.takeIf { it.isNotBlank() } ?: "$sendAt|$address|$body",
                address = address,
                body = body,
                sendAt = sendAt,
                subscriptionId = obj["subscriptionId"]?.asExactLong()?.toInt(),
                lastError = obj["lastError"]?.asString()?.takeIf { it.isNotBlank() },
            )
        }
    }.getOrDefault(emptyList())
}

/**
 * Minimal pure-JVM JSON reader/writer shared by the data stores that need a portable
 * file format. No reflection, no Android dependency, so a local test can round-trip a
 * backup without a device or Robolectric.
 */
internal object TrinityJson {

    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (key, value) -> "${quote(key)}:$value" }

    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c.code < 0x20) {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        return out.append('"').toString()
    }

    fun parse(raw: String): Any? = Parser(raw).parse()

    private class Parser(private val source: String) {

        private var pos = 0

        fun parse(): Any? {
            skipWhitespace()
            val result = readValue()
            skipWhitespace()
            if (pos != source.length) fail("trailing characters")
            return result
        }

        private fun fail(message: String): Nothing = throw IllegalArgumentException("$message at $pos")

        private fun skipWhitespace() {
            while (pos < source.length && source[pos].isWhitespace()) pos++
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (pos >= source.length) fail("unexpected end")
            return when (source[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readLiteral(text: String, value: Any?): Any? {
            if (!source.startsWith(text, pos)) fail("invalid literal")
            pos += text.length
            return value
        }

        private fun readObject(): Map<String, Any?> {
            pos++
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (pos < source.length && source[pos] == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (pos >= source.length || source[pos] != '"') fail("expected key")
                val key = readString()
                skipWhitespace()
                if (pos >= source.length || source[pos] != ':') fail("expected colon")
                pos++
                result[key] = readValue()
                skipWhitespace()
                if (pos >= source.length) fail("unterminated object")
                when (source[pos]) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> fail("expected comma or brace")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < source.length && source[pos] == ']') {
                pos++
                return result
            }
            while (true) {
                result += readValue()
                skipWhitespace()
                if (pos >= source.length) fail("unterminated array")
                when (source[pos]) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> fail("expected comma or bracket")
                }
            }
        }

        private fun readString(): String {
            pos++
            val out = StringBuilder()
            while (pos < source.length) {
                when (val c = source[pos++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (pos >= source.length) fail("bad escape")
                        when (source[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > source.length) fail("bad unicode escape")
                                val code = source.substring(pos, pos + 4).toIntOrNull(16)
                                    ?: fail("bad unicode escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("bad escape")
                        }
                    }
                    else -> out.append(c)
                }
            }
            fail("unterminated string")
        }

        private fun readNumber(): Any {
            val start = pos
            while (pos < source.length && (source[pos].isDigit() || source[pos] == '-' ||
                    source[pos] == '+' || source[pos] == '.' || source[pos] == 'e' || source[pos] == 'E')
            ) {
                pos++
            }
            if (start == pos) fail("expected value")
            val token = source.substring(start, pos)
            return token.toLongOrNull() ?: token.toDoubleOrNull() ?: fail("bad number")
        }
    }
}

internal fun Any?.asObj(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
    ?.associate { (key, value) -> key as String to value }

internal fun Any?.asArr(): List<*>? = this as? List<*>

internal fun Any?.asString(): String? = this as? String

/** Integer tokens only; a `1.0` in a schema or count field is invalid. */
internal fun Any?.asExactLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    else -> null
}

internal fun Any?.asLong(): Long? = (this as? Number)?.toLong()

internal fun Any?.asInt(): Int? = asLong()?.toInt()
