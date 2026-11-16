package com.sarim.trinity.data

/**
 * Portable rule packs. The JSON codec here is deliberately pure Kotlin so a pack can
 * be validated and round-tripped in a local JVM test without Android's org.json.
 */
object RulePack {

    const val SCHEMA = 1

    data class Pack(
        val name: String,
        val names: List<Rule>,
        val numbers: List<Rule>,
        val keywords: List<String>,
        val wholeWordKeywords: List<String>,
        val combined: List<CompoundRule>,
        val allowed: List<String>,
        val exportedAt: Long,
    )

    fun serialize(pack: Pack): String = Json.obj(
        "schema" to Json.number(SCHEMA.toLong()),
        "name" to Json.quote(pack.name),
        "exportedAt" to Json.number(pack.exportedAt),
        "names" to Json.arr(pack.names.map { ruleJson(it) }),
        "numbers" to Json.arr(pack.numbers.map { ruleJson(it) }),
        "keywords" to Json.arr(keywordsJson(pack)),
        "wholeWordKeywords" to Json.arr(pack.wholeWordKeywords.map { Json.quote(it) }),
        "combined" to Json.arr(pack.combined.map { compoundJson(it) }),
        "allowed" to Json.arr(pack.allowed.map { Json.quote(it) }),
    )

    /** Null on a bad schema, malformed JSON, or any field that fails validation. */
    fun parse(raw: String): Pack? = try {
        parseOrThrow(raw)
    } catch (e: Exception) {
        null
    }

    private fun parseOrThrow(raw: String): Pack {
        val root = Json.parse(raw).asObj() ?: throw IllegalArgumentException("root must be an object")
        if (root["schema"]?.asInt() != SCHEMA) throw IllegalArgumentException("unsupported schema")
        val name = root["name"]?.asString() ?: ""
        val exportedAt = root["exportedAt"]?.asLong() ?: 0L

        val names = root["names"]?.asArr()?.map { parseRule(it) } ?: emptyList()
        val numbers = root["numbers"]?.asArr()?.map { parseRule(it) } ?: emptyList()
        val combined = root["combined"]?.asArr()?.map { parseCompound(it) } ?: emptyList()
        val allowed = root["allowed"]?.asArr()?.map { entry ->
            entry.asString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("bad allowed entry")
        } ?: emptyList()

        val keywordPairs = root["keywords"]?.asArr()?.map { parseKeyword(it) } ?: emptyList()
        val keywords = keywordPairs.map { it.first }
        val wholeWords = mutableListOf<String>()
        keywordPairs.forEach { (value, wholeWord) ->
            if (wholeWord && value !in wholeWords) wholeWords += value
        }
        root["wholeWordKeywords"]?.asArr()?.forEach { entry ->
            val value = entry.asString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("bad whole-word keyword")
            if (value !in wholeWords) wholeWords += value
        }

        return Pack(name, names, numbers, keywords, wholeWords, combined, allowed, exportedAt)
    }

    fun preview(pack: Pack): String = listOf(
        count(pack.names.size, "name"),
        count(pack.numbers.size, "number"),
        count(pack.keywords.size, "keyword"),
        "${pack.combined.size} combined",
        "${pack.allowed.size} allowed",
    ).joinToString(" · ")

    private fun count(n: Int, singular: String): String = "$n ${if (n == 1) singular else "${singular}s"}"

    /** Union: add everything in [pack] that is not already in [current]. */
    fun merge(current: BlockStore.Rules, pack: Pack): BlockStore.Rules {
        fun <T> appendUnique(existing: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
            val seen = existing.map(key).toMutableSet()
            val result = existing.toMutableList()
            incoming.forEach { item ->
                val identity = key(item)
                if (identity !in seen) {
                    result += item
                    seen += identity
                }
            }
            return result
        }
        return BlockStore.Rules(
            names = appendUnique(current.names, pack.names) { "${it.value.lowercase()}|${it.scope}|${it.window}" },
            numbers = appendUnique(current.numbers, pack.numbers) { "${it.value}|${it.scope}|${it.window}" },
            keywords = appendUnique(current.keywords, pack.keywords) { it.lowercase() },
            wholeWordKeywords = appendUnique(current.wholeWordKeywords, pack.wholeWordKeywords) { it },
            combined = appendUnique(current.combined, pack.combined) { "${it.match}|${it.conditions}|${it.scope}|${it.window}" },
            allowed = appendUnique(current.allowed, pack.allowed) { it.lowercase() },
            hideMatchedCalls = current.hideMatchedCalls,
        )
    }

    /** Replace every list from [pack]; the hide-matched-calls toggle is the user's, so it survives. */
    fun replace(current: BlockStore.Rules, pack: Pack): BlockStore.Rules = BlockStore.Rules(
        names = pack.names,
        numbers = pack.numbers,
        keywords = pack.keywords,
        wholeWordKeywords = pack.wholeWordKeywords,
        combined = pack.combined,
        allowed = pack.allowed,
        hideMatchedCalls = current.hideMatchedCalls,
    )

    // ---- JSON shaping -------------------------------------------------------

    private fun windowJson(window: TimeWindow): String = Json.obj(
        "s" to Json.number(window.startMinute.toLong()),
        "e" to Json.number(window.endMinute.toLong()),
        "d" to Json.arr(window.days.sorted().map { Json.number(it.toLong()) }),
    )

    private fun ruleJson(rule: Rule): String {
        val fields = mutableListOf("v" to Json.quote(rule.value), "s" to Json.quote(rule.scope.name))
        rule.window?.let { fields += "w" to windowJson(it) }
        return Json.obj(*fields.toTypedArray())
    }

    private fun compoundJson(rule: CompoundRule): String {
        val fields = mutableListOf(
            "m" to Json.quote(rule.match.name),
            "s" to Json.quote(rule.scope.name),
            "c" to Json.arr(rule.conditions.map { c ->
                Json.obj("f" to Json.quote(c.field.name), "v" to Json.quote(c.value))
            }),
        )
        rule.window?.let { fields += "w" to windowJson(it) }
        return Json.obj(*fields.toTypedArray())
    }

    private fun keywordsJson(pack: Pack): List<String> {
        val whole = pack.wholeWordKeywords.toSet()
        return pack.keywords.map { value ->
            if (value in whole) Json.obj("v" to Json.quote(value), "ww" to Json.bool(true))
            else Json.quote(value)
        }
    }

    // ---- JSON reading -------------------------------------------------------

    private fun parseRule(value: Any?): Rule {
        // Legacy packs (and older rule files) stored names and numbers as bare strings.
        if (value is String) {
            val text = value.trim()
            if (text.isEmpty()) throw IllegalArgumentException("rule needs a value")
            return Rule(text)
        }
        val obj = value.asObj() ?: throw IllegalArgumentException("rule must be an object")
        val text = obj["v"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("rule needs a value")
        val scope = obj["s"]?.asString()?.let { name ->
            Scope.values().firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("bad scope")
        } ?: Scope.Both
        val window = obj["w"]?.let { parseWindow(it) }
        return Rule(text, scope, window)
    }

    private fun parseWindow(value: Any?): TimeWindow {
        val obj = value.asObj() ?: throw IllegalArgumentException("window must be an object")
        val start = obj["s"]?.asInt() ?: throw IllegalArgumentException("window needs start")
        val end = obj["e"]?.asInt() ?: throw IllegalArgumentException("window needs end")
        if (start !in 0..1439 || end !in 0..1439) throw IllegalArgumentException("window minutes out of range")
        val days = obj["d"]?.asArr()?.map { entry ->
            entry.asInt() ?: throw IllegalArgumentException("bad day")
        }?.toSet() ?: emptySet()
        if (days.any { it !in 1..7 }) throw IllegalArgumentException("day out of range")
        return TimeWindow(start, end, days)
    }

    private fun parseCompound(value: Any?): CompoundRule {
        val obj = value.asObj() ?: throw IllegalArgumentException("combined rule must be an object")
        val matchName = obj["m"]?.asString() ?: throw IllegalArgumentException("combined rule needs match")
        val match = Match.values().firstOrNull { it.name == matchName }
            ?: throw IllegalArgumentException("bad match")
        val scope = obj["s"]?.asString()?.let { name ->
            Scope.values().firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("bad scope")
        } ?: Scope.Both
        val conditions = obj["c"]?.asArr()?.map { entry ->
            val condition = entry.asObj() ?: throw IllegalArgumentException("condition must be an object")
            val fieldName = condition["f"]?.asString() ?: throw IllegalArgumentException("condition needs field")
            val field = Field.values().firstOrNull { it.name == fieldName }
                ?: throw IllegalArgumentException("bad field")
            val text = condition["v"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("condition needs a value")
            Condition(field, text)
        }?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("combined rule needs conditions")
        val window = obj["w"]?.let { parseWindow(it) }
        return CompoundRule(match, conditions, scope, window)
    }

    /** Returns the keyword text and whether it is whole-word. Accepts the legacy bare string. */
    private fun parseKeyword(value: Any?): Pair<String, Boolean> {
        if (value is String) return value to false
        val obj = value.asObj() ?: throw IllegalArgumentException("bad keyword")
        val text = obj["v"]?.asString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("keyword needs a value")
        return text to (obj["ww"]?.asBool() ?: false)
    }

    private fun Any?.asObj(): Map<String, Any?>? = (this as? Map<*, *>)?.entries
        ?.associate { (key, value) -> key as String to value }

    private fun Any?.asArr(): List<*>? = this as? List<*>

    private fun Any?.asString(): String? = this as? String

    private fun Any?.asBool(): Boolean? = this as? Boolean

    private fun Any?.asLong(): Long? = when (this) {
        is Long -> this
        is Int -> toLong()
        is Double -> toLong()
        is Number -> toLong()
        else -> null
    }

    private fun Any?.asInt(): Int? = asLong()?.toInt()
}

/** Minimal JSON writer/reader: enough for rule packs, no reflection and no Android. */
private object Json {

    fun number(value: Long): String = value.toString()

    fun bool(value: Boolean): String = value.toString()

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

    fun obj(vararg fields: Pair<String, String>): String =
        fields.joinToString(",", "{", "}") { (key, value) -> "${quote(key)}:$value" }

    fun arr(values: List<String>): String = values.joinToString(",", "[", "]")

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
                                val code = source.substring(pos, pos + 4).toIntOrNull(16) ?: fail("bad unicode escape")
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
