package com.sarim.trinity.data

import android.content.Context
import android.net.Uri

/**
 * A portable, versioned copy of the settings Trinity owns in SharedPreferences.
 *
 * This is **not** a message backup. Message bodies and anything that can hold one are
 * deliberately left out, so a settings file can be shared or stored without carrying
 * private text:
 *
 * - `trinity.threads`: only the `muted` and `seen.*` keys. `draft.*` holds unsent text.
 * - `trinity.scheduled` is **not** owned either: a queued text cannot be restored without
 *   its body, and writing body-less entries back would silently empty the pending queue.
 * - `trinity.audit`: only decision metadata (`at`, `kind`, `address`, `decision`,
 *   `rule`, `scope`). No body field exists and none is written back.
 * - `trinity.quarantine` is **not** an owned file at all: held entries are message
 *   bodies. M4's [MessageBackup] is the separate, explicit flow for those.
 *
 * Values are primitives only: String, Long, Int, Boolean and Set<String>. Each value is
 * written with a type tag so restore rebuilds the exact SharedPreferences type. The codec
 * is pure Kotlin (no `org.json`, no Android) so a file can be validated in a local JVM
 * test. Reading and writing the app-owned files needs no special permission.
 */
object SettingsBackup {

    const val SCHEMA = 1

    data class Backup(
        val schema: Int,
        val exportedAt: Long,
        /** Preference file name -> (key -> primitive value). */
        val prefs: Map<String, Map<String, Any?>>,
    )

    /** Files this backup owns. Anything else in a file is ignored on restore. */
    val OWNED_FILES: List<String> = listOf(
        "trinity.rules",
        "trinity.ui",
        "trinity.sims",
        "trinity.threads",
        "trinity.audit",
        "trinity.mms",
    )

    private val AUDIT_META_KEYS = listOf("at", "kind", "address", "decision", "rule", "scope")

    // ---- pure codec ---------------------------------------------------------

    fun serialize(backup: Backup): String = TrinityJson.obj(
        "schema" to backup.schema.toString(),
        "exportedAt" to backup.exportedAt.toString(),
        "prefs" to TrinityJson.obj(
            *backup.prefs.map { (file, values) ->
                file to TrinityJson.obj(
                    *values.map { (key, value) -> key to valueJson(value) }.toTypedArray(),
                )
            }.toTypedArray(),
        ),
    )

    /** Null on a bad schema, malformed JSON, or any field that fails validation. */
    fun parse(raw: String): Backup? = runCatching { parseOrThrow(raw) }.getOrNull()

    private fun parseOrThrow(raw: String): Backup {
        val root = TrinityJson.parse(raw).asObj()
            ?: throw IllegalArgumentException("root must be an object")
        if (root["schema"]?.asExactLong()?.toInt() != SCHEMA) {
            throw IllegalArgumentException("unsupported schema")
        }
        val exportedAt = root["exportedAt"]?.asExactLong()
            ?: throw IllegalArgumentException("exportedAt missing")
        val prefsNode = root["prefs"]?.asObj()
            ?: throw IllegalArgumentException("prefs missing")
        val prefs = linkedMapOf<String, Map<String, Any?>>()
        prefsNode.forEach { (file, values) ->
            val node = values.asObj() ?: throw IllegalArgumentException("preference file must be an object")
            prefs[file] = linkedMapOf<String, Any?>().apply {
                node.forEach { (key, value) -> put(key, parseValue(value)) }
            }
        }
        return Backup(SCHEMA, exportedAt, prefs)
    }

    private fun valueJson(value: Any?): String = when (value) {
        null -> TrinityJson.obj("t" to TrinityJson.quote("null"))
        is String -> TrinityJson.obj(
            "t" to TrinityJson.quote("str"),
            "v" to TrinityJson.quote(value),
        )
        is Long -> TrinityJson.obj(
            "t" to TrinityJson.quote("long"),
            "v" to value.toString(),
        )
        is Int -> TrinityJson.obj(
            "t" to TrinityJson.quote("int"),
            "v" to value.toString(),
        )
        is Boolean -> TrinityJson.obj(
            "t" to TrinityJson.quote("bool"),
            "v" to value.toString(),
        )
        is Set<*> -> TrinityJson.obj(
            "t" to TrinityJson.quote("set"),
            "v" to value.joinToString(",", "[", "]") { TrinityJson.quote(it.toString()) },
        )
        else -> throw IllegalArgumentException("unsupported value type: ${value::class.java.name}")
    }

    private fun parseValue(value: Any?): Any? {
        val node = value.asObj() ?: throw IllegalArgumentException("value must be an object")
        return when (node["t"]?.asString()) {
            "null" -> null
            "str" -> node["v"]?.asString() ?: throw IllegalArgumentException("string value missing")
            "long" -> node["v"]?.asExactLong() ?: throw IllegalArgumentException("long value missing")
            "int" -> node["v"]?.asExactLong()?.toInt()
                ?: throw IllegalArgumentException("int value missing")
            "bool" -> node["v"] as? Boolean ?: throw IllegalArgumentException("bool value missing")
            "set" -> {
                val array = node["v"]?.asArr() ?: throw IllegalArgumentException("set value missing")
                array.map { it as? String ?: throw IllegalArgumentException("set entry must be a string") }.toSet()
            }
            else -> throw IllegalArgumentException("unknown value tag")
        }
    }

    // ---- filtering (pure, so JVM tests can assert body keys are gone) -------

    /** Applies all exclusions and drops empty files. Used before export and restore. */
    internal fun sanitize(prefs: Map<String, Map<String, Any?>>): Map<String, Map<String, Any?>> {
        val out = linkedMapOf<String, Map<String, Any?>>()
        prefs.forEach { (file, values) ->
            if (file !in OWNED_FILES) return@forEach
            val filtered = filterFile(file, values)
            if (filtered.isNotEmpty()) out[file] = filtered
        }
        return out
    }

    /** Builds a backup from in-memory preference maps; the IO wrapper reuses this. */
    internal fun collect(prefs: Map<String, Map<String, Any?>>, exportedAt: Long): Backup =
        Backup(SCHEMA, exportedAt, sanitize(prefs))

    internal fun filterFile(file: String, values: Map<String, Any?>): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        values.forEach { (key, raw) ->
            if (isBodyKey(key)) return@forEach
            val value = when {
                file == "trinity.threads" && !key.startsWith("muted") && !key.startsWith("seen") ->
                    return@forEach
                file == "trinity.audit" && key == "entries" && raw is String ->
                    filterEntries(raw, AUDIT_META_KEYS)
                else -> raw
            }
            // Only the five contract types survive; a Float or a Set of non-strings
            // is dropped rather than silently changing its type on restore.
            out[key] = when (value) {
                is String, is Long, is Int, is Boolean -> value
                is Set<*> -> value.filterIsInstance<String>().toSet()
                else -> return@forEach
            }
        }
        return out
    }

    private fun isBodyKey(key: String): Boolean =
        key == "body" || key.startsWith("body.") || key.startsWith("draft.")

    /** Rebuilds a JSON array keeping only [keys], which contain no message text. */
    private fun filterEntries(raw: String, keys: List<String>): String {
        val array = runCatching { TrinityJson.parse(raw).asArr() }.getOrNull() ?: return "[]"
        return array.mapNotNull { value ->
            val obj = value.asObj() ?: return@mapNotNull null
            val fields = keys.mapNotNull { key -> obj[key]?.let { key to encodeParsed(it) } }
            if (fields.isEmpty()) null else TrinityJson.obj(*fields.toTypedArray())
        }.joinToString(",", "[", "]")
    }

    private fun encodeParsed(value: Any?): String = when (value) {
        null -> "null"
        is String -> TrinityJson.quote(value)
        is Number -> value.toString()
        is Boolean -> value.toString()
        else -> TrinityJson.quote(value.toString())
    }

    // ---- file IO ------------------------------------------------------------

    private fun read(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            String(stream.readBytes(), Charsets.UTF_8)
        }
    }.getOrNull()

    private fun write(context: Context, uri: Uri, text: String): Boolean = runCatching {
        val stream = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        true
    }.getOrDefault(false)

    /** Exports the owned preference files; returns how many files the backup holds. */
    fun export(context: Context, uri: Uri): Int? {
        val raw = OWNED_FILES.associateWith { name ->
            context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .all
        }
        val backup = collect(raw, System.currentTimeMillis())
        if (!write(context, uri, serialize(backup))) return null
        return backup.prefs.size
    }

    /** Pure counterpart of [dryRun]: how many files a validated file would restore. */
    internal fun countRestorableFiles(raw: String): Int? = parse(raw)?.let { sanitize(it.prefs).size }

    /** Counts restorable files without writing anything. */
    fun dryRun(context: Context, uri: Uri): Int? {
        val raw = read(context, uri) ?: return null
        return countRestorableFiles(raw)
    }

    /**
     * Validates the whole file before a single preference is touched, then replaces
     * each owned file in full (`clear()` followed by the tagged puts). A file that does
     * not parse, or a write that throws, returns null — callers must not report success.
     * SharedPreferences files are independent, so `apply()` is per-file rather than a
     * cross-file transaction; validation itself is all-or-nothing.
     */
    fun restore(context: Context, uri: Uri): Int? {
        val raw = read(context, uri) ?: return null
        val backup = parse(raw) ?: return null
        val prefs = sanitize(backup.prefs)
        return runCatching {
            prefs.forEach { (name, values) ->
                val editor = context.applicationContext
                    .getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                values.forEach { (key, value) -> editor.putValue(key, value) }
                editor.apply()
            }
            prefs.size
        }.getOrNull()
    }

    private fun android.content.SharedPreferences.Editor.putValue(
        key: String,
        value: Any?,
    ): android.content.SharedPreferences.Editor = when (value) {
        is String -> putString(key, value)
        is Long -> putLong(key, value)
        is Int -> putInt(key, value)
        is Boolean -> putBoolean(key, value)
        is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
        null -> remove(key)
        else -> this
    }
}
