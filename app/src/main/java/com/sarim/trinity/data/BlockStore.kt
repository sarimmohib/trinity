package com.sarim.trinity.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Where a rule bites. Keywords are texts-only, so they never need this. */
enum class Scope(val label: String) {
    Both("BOTH"),
    Texts("TEXTS"),
    Calls("CALLS"),
    ;

    /** True when a rule with this scope applies to [channel]; null asks "any". */
    fun covers(channel: Scope?): Boolean =
        channel == null || this == Both || this == channel
}

/**
 * A clock window measured in minutes from midnight. A [startMinute] greater than
 * [endMinute] crosses midnight. An empty [days] means every day; otherwise the set
 * holds ISO day numbers, 1 = Monday through 7 = Sunday.
 */
data class TimeWindow(
    val startMinute: Int,
    val endMinute: Int,
    val days: Set<Int> = emptySet(),
) {
    /** True when [now] falls inside this window. Pure JVM logic, no Android calls. */
    fun contains(now: LocalDateTime): Boolean {
        if (days.isNotEmpty() && now.dayOfWeek.value !in days) return false
        val minute = now.hour * 60 + now.minute
        return if (startMinute <= endMinute) {
            minute in startMinute..endMinute
        } else {
            minute >= startMinute || minute <= endMinute
        }
    }
}

/** A blocked name or number, the surface it was meant for, and an optional time window. */
data class Rule(
    val value: String,
    val scope: Scope = Scope.Both,
    val window: TimeWindow? = null,
)

/** How a combined rule joins its conditions: every one must hold, or any one will do. */
enum class Match(val label: String) {
    All("AND"),
    Any("OR"),
}

/** What a condition looks at. Text is the body of a message, so calls never satisfy it. */
enum class Field(val label: String) {
    Name("NAME"),
    Number("NUMBER"),
    Text("TEXT"),
}

/** One test inside a combined rule: the name contains it, the number matches it, or the text says it. */
data class Condition(val field: Field, val value: String)

/**
 * A rule made of several conditions joined by AND or OR, e.g. NAME "bank" AND TEXT "otp".
 * A condition that cannot be checked (text on a call) counts as false, and a rule with
 * no conditions never matches. [window], when set, gates the whole rule rather than
 * each condition on its own.
 */
data class CompoundRule(
    val match: Match,
    val conditions: List<Condition>,
    val scope: Scope = Scope.Both,
    val window: TimeWindow? = null,
) {
    /** True when the rule reads message text, which makes it a per-message rule. */
    val readsText: Boolean get() = conditions.any { it.field == Field.Text }

    /** Reads back as written: NAME "bank" AND TEXT "otp". */
    val summary: String
        get() = conditions.joinToString(" ${match.label} ") { "${it.field.label} \"${it.value}\"" }
}

/**
 * Blocking rules live in one SharedPreferences file. Three lists, one order of
 * precedence: an explicit name or number match beats a keyword match, because a
 * blocked person should stay blocked even when their message says nothing
 * suspicious. Every name and number carries the surface it applies to, so a number
 * can be silenced for calls while its texts still arrive.
 */
class BlockStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("trinity.rules", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(read())
    val rules: StateFlow<Rules> = _rules.asStateFlow()

    data class Rules(
        val names: List<Rule> = emptyList(),
        val numbers: List<Rule> = emptyList(),
        val keywords: List<String> = emptyList(),
        val allowed: List<String> = emptyList(),
        val hideMatchedCalls: Boolean = true,
        val combined: List<CompoundRule> = emptyList(),
        /** The subset of [keywords] that must appear as a whole word to hit. */
        val wholeWordKeywords: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = names.isEmpty() && numbers.isEmpty() && keywords.isEmpty() && combined.isEmpty()
        val ruleCount: Int get() = names.size + numbers.size + keywords.size + combined.size

        /** Split once per rule set: sender-only combined rules run before the allow-list, text ones after. */
        val senderCombined: List<CompoundRule> by lazy { combined.filterNot { it.readsText } }
        val textCombined: List<CompoundRule> by lazy { combined.filter { it.readsText } }

        /** Lower-cased once per rule set, not once per keyword for every message checked. */
        val lowerKeywords: List<String> by lazy { keywords.map { it.lowercase() } }

        /** Membership set for whole-word keywords, so matching stays O(1) per keyword. */
        val lowerWholeWords: Set<String> by lazy { wholeWordKeywords.map { it.lowercase() }.toSet() }
    }

    private fun read(): Rules {
        val (keywords, wholeWordKeywords) = readKeywords()
        return Rules(
            names = readRules("names"),
            numbers = readRules("numbers"),
            keywords = keywords,
            wholeWordKeywords = wholeWordKeywords,
            allowed = readList("allowed"),
            hideMatchedCalls = prefs.getBoolean("hideMatchedCalls", true),
            combined = readCombined(),
        )
    }

    /** Re-read the preferences file, e.g. after a rule-pack import replaced it on disk. */
    fun reload() {
        _rules.value = read()
    }

    /** Persist every list at once and publish the new rule set in one shot. */
    fun applyRules(rules: Rules) {
        writeRules("names", rules.names)
        writeRules("numbers", rules.numbers)
        writeKeywords(rules.keywords, rules.wholeWordKeywords)
        writeList("allowed", rules.allowed)
        writeCombined(rules.combined)
        prefs.edit().putBoolean("hideMatchedCalls", rules.hideMatchedCalls).apply()
        _rules.value = rules
    }

    private fun readCombined(): List<CompoundRule> {
        val raw = prefs.getString("combined", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val entry = array.optJSONObject(index) ?: return@mapNotNull null
                val match = runCatching { Match.valueOf(entry.optString("m")) }.getOrNull()
                    ?: return@mapNotNull null
                val scope = runCatching { Scope.valueOf(entry.optString("s")) }
                    .getOrDefault(Scope.Both)
                val list = entry.optJSONArray("c") ?: return@mapNotNull null
                val conditions = (0 until list.length()).mapNotNull { i ->
                    val item = list.optJSONObject(i) ?: return@mapNotNull null
                    val field = runCatching { Field.valueOf(item.optString("f")) }.getOrNull()
                        ?: return@mapNotNull null
                    val value = item.optString("v").trim()
                    Condition(field, value).takeIf { value.isNotBlank() }
                }
                CompoundRule(match, conditions, scope, readWindow(entry)).takeIf { conditions.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCombined(rules: List<CompoundRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val list = JSONArray()
            rule.conditions.forEach { list.put(JSONObject().put("f", it.field.name).put("v", it.value)) }
            val entry = JSONObject().put("m", rule.match.name).put("s", rule.scope.name).put("c", list)
            rule.window?.let { entry.put("w", windowJson(it)) }
            array.put(entry)
        }
        prefs.edit().putString("combined", array.toString()).apply()
    }

    /** Reads the optional `"w": {"s":..,"e":..,"d":[..]}` object; absence means no schedule. */
    private fun readWindow(entry: JSONObject): TimeWindow? {
        val window = entry.optJSONObject("w") ?: return null
        val days = window.optJSONArray("d")
        val selected = if (days == null) {
            emptySet()
        } else {
            (0 until days.length()).mapNotNull { i -> days.optInt(i, -1).takeIf { it in 1..7 } }.toSet()
        }
        return TimeWindow(window.optInt("s", 0), window.optInt("e", 0), selected)
    }

    private fun windowJson(window: TimeWindow): JSONObject {
        val days = JSONArray()
        window.days.sorted().forEach { days.put(it) }
        return JSONObject().put("s", window.startMinute).put("e", window.endMinute).put("d", days)
    }

    /**
     * Rules are stored as objects carrying their scope. Anything written by an
     * older build is a bare string, which meant "both" — that is how an existing
     * rule keeps behaving exactly as it did before this field existed. A "w" object,
     * when present, restores the optional schedule.
     */
    private fun readRules(key: String): List<Rule> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                when (val entry = array.get(index)) {
                    is String -> Rule(entry.trim()).takeIf { it.value.isNotBlank() }
                    is JSONObject -> {
                        val value = entry.optString("v").trim()
                        val scope = runCatching { Scope.valueOf(entry.optString("s")) }
                            .getOrDefault(Scope.Both)
                        Rule(value, scope, readWindow(entry)).takeIf { value.isNotBlank() }
                    }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRules(key: String, rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val entry = JSONObject().put("v", rule.value).put("s", rule.scope.name)
            rule.window?.let { entry.put("w", windowJson(it)) }
            array.put(entry)
        }
        prefs.edit().putString(key, array.toString()).apply()
    }

    /**
     * Keywords are bare strings for the common case; a whole-word keyword is stored
     * as `{"v":"otp","ww":true}`. Old bare-string files therefore load unchanged.
     */
    private fun readKeywords(): Pair<List<String>, List<String>> {
        val raw = prefs.getString("keywords", null) ?: return emptyList<String>() to emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val values = mutableListOf<String>()
            val wholeWords = mutableListOf<String>()
            (0 until array.length()).forEach { i ->
                when (val entry = array.get(i)) {
                    is String -> values += entry
                    is JSONObject -> {
                        val value = entry.optString("v")
                        if (value.isNotBlank()) {
                            values += value
                            if (entry.optBoolean("ww")) wholeWords += value
                        }
                    }
                }
            }
            values to wholeWords
        }.getOrDefault(emptyList<String>() to emptyList())
    }

    private fun writeKeywords(values: List<String>, wholeWords: List<String>) {
        val array = JSONArray()
        values.forEach { value ->
            if (value in wholeWords) array.put(JSONObject().put("v", value).put("ww", true))
            else array.put(value)
        }
        prefs.edit().putString("keywords", array.toString()).apply()
    }

    private fun readList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun writeList(key: String, values: List<String>) {
        prefs.edit().putString(key, JSONArray(values).toString()).apply()
    }

    private fun update(block: (Rules) -> Rules) = applyRules(block(_rules.value))

    fun addScheduledName(raw: String, scope: Scope = Scope.Both, window: TimeWindow? = null) {
        val value = raw.trim()
        if (value.isBlank()) return
        if (value.any { it.isDigit() } && value.count { it.isDigit() } >= 5) {
            addScheduledNumber(value, scope, window)
            return
        }
        update { current ->
            if (current.names.any {
                it.value.equals(value, ignoreCase = true) && it.scope == scope && it.window == window
            }) current
            else current.copy(names = current.names + Rule(value, scope, window))
        }
    }

    fun addName(raw: String, scope: Scope = Scope.Both) = addScheduledName(raw, scope, null)

    fun addKeyword(raw: String, wholeWord: Boolean = false) {
        val value = raw.trim()
        if (value.isBlank()) return
        update { current ->
            val index = current.keywords.indexOfFirst { it.equals(value, ignoreCase = true) }
            when {
                index < 0 -> current.copy(
                    keywords = current.keywords + value,
                    wholeWordKeywords = if (wholeWord) current.wholeWordKeywords + value else current.wholeWordKeywords,
                )
                wholeWord && current.wholeWordKeywords.none { it.equals(value, ignoreCase = true) } ->
                    current.copy(wholeWordKeywords = current.wholeWordKeywords + current.keywords[index])
                else -> current
            }
        }
    }

    /** Flips the whole-word flag on an existing keyword without changing its text. */
    fun setKeywordWholeWord(value: String, enabled: Boolean) = update { current ->
        val existing = current.keywords.firstOrNull { it == value } ?: return@update current
        val has = current.wholeWordKeywords.any { it == existing }
        if (enabled == has) current
        else current.copy(
            wholeWordKeywords = if (enabled) current.wholeWordKeywords + existing
            else current.wholeWordKeywords - existing,
        )
    }

    fun addScheduledNumber(raw: String, scope: Scope = Scope.Both, window: TimeWindow? = null) {
        val value = raw.trim()
        if (value.isBlank()) return
        update { current ->
            if (current.numbers.any { it.value == value && it.scope == scope && it.window == window }) current
            else current.copy(numbers = current.numbers + Rule(value, scope, window))
        }
    }

    fun addNumber(raw: String, scope: Scope = Scope.Both) = addScheduledNumber(raw, scope, null)

    /**
     * A sender the user vouched for. Their texts skip keyword rules; an explicit
     * name or number rule still wins, because blocking a person outranks allow.
     */
    fun addAllowed(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        update { current ->
            if (current.allowed.any { BlockMatcher.sameNumber(it, value) || it == value }) current
            else current.copy(allowed = current.allowed + value)
        }
    }

    fun removeName(value: String) = update { it.copy(names = it.names.filterNot { rule -> rule.value == value }) }

    /** Matches on the digits, so "0300 7712 008" also clears a stored "03007712008". */
    fun removeNumber(value: String) = update {
        it.copy(numbers = it.numbers.filterNot { rule -> BlockMatcher.sameNumber(rule.value, value) || rule.value == value })
    }
    fun addScheduledCombined(rule: CompoundRule) {
        val clean = rule.copy(
            conditions = rule.conditions
                .map { it.copy(value = it.value.trim()) }
                .filter { it.value.isNotBlank() },
        )
        if (clean.conditions.isEmpty()) return
        update { current ->
            if (current.combined.contains(clean)) current
            else current.copy(combined = current.combined + clean)
        }
    }

    fun addCombined(rule: CompoundRule) = addScheduledCombined(rule.copy(window = null))

    fun removeCombined(rule: CompoundRule) = update { it.copy(combined = it.combined - rule) }

    /** Removes the keyword and its whole-word flag together, so re-adding it starts clean. */
    fun removeKeyword(value: String) = update { current ->
        current.copy(
            keywords = current.keywords - value,
            wholeWordKeywords = current.wholeWordKeywords.filterNot { it.equals(value, ignoreCase = true) },
        )
    }
    fun removeAllowed(value: String) = update {
        it.copy(allowed = it.allowed.filterNot { entry -> BlockMatcher.sameNumber(entry, value) || entry == value })
    }

    fun setHideMatchedCalls(enabled: Boolean) = update { it.copy(hideMatchedCalls = enabled) }

    /** Drops every name, number, keyword, combined rule and allowance; the toggle survives. */
    fun clearAll() = update { Rules(hideMatchedCalls = it.hideMatchedCalls) }

    companion object {
        @Volatile
        private var instance: BlockStore? = null

        fun get(context: Context): BlockStore =
            instance ?: synchronized(this) {
                instance ?: BlockStore(context).also { instance = it }
            }
    }
}

/**
 * Rule evaluation. Order matters: name, then number, then sender-only combined rules,
 * then the allow-list, then text combined rules, then keywords. [channel] is the
 * surface asking — texts, calls, or null for "this contact is blocked somewhere".
 * A rule that carries a window is consulted only when [now] falls inside it.
 */
object BlockMatcher {

    fun match(
        name: String?,
        address: String?,
        body: String?,
        rules: BlockStore.Rules,
        channel: Scope? = null,
        now: LocalDateTime = LocalDateTime.now(),
    ): RuleHit? {
        // Emergencies always go through, whatever the rules say.
        if (!address.isNullOrBlank() && isEmergency(address)) return null
        val label = name?.trim().orEmpty()
        if (label.isNotBlank()) {
            rules.names.firstOrNull {
                it.scope.covers(channel) && it.window.allows(now) && label.contains(it.value, ignoreCase = true)
            }?.let { rule -> return RuleHit(RuleHit.Kind.NAME, rule.value, scope = rule.scope) }
        }
        if (!address.isNullOrBlank()) {
            rules.numbers.firstOrNull { it.scope.covers(channel) && it.window.allows(now) && sameNumber(it.value, address) }
                ?.let { rule -> return RuleHit(RuleHit.Kind.NUMBER, rule.value, scope = rule.scope) }
        }
        // Combined rules that only look at the sender are as firm as a name or number rule.
        rules.senderCombined.firstOrNull {
            it.scope.covers(channel) && it.window.allows(now) && holds(it, label, address, body)
        }?.let { rule ->
            return RuleHit(
                RuleHit.Kind.COMBO,
                rule.summary,
                detail = explanation(rule, label, address, body),
                scope = rule.scope,
            )
        }

        // A vouched-for sender keeps their texts; keywords stop applying to them.
        if (!address.isNullOrBlank() && isAllowed(address, rules)) return null

        rules.textCombined.firstOrNull {
            it.scope.covers(channel) && it.window.allows(now) && holds(it, label, address, body)
        }?.let { rule ->
            return RuleHit(
                RuleHit.Kind.COMBO_TEXT,
                rule.summary,
                detail = explanation(rule, label, address, body),
                scope = rule.scope,
            )
        }

        if (!body.isNullOrBlank()) {
            val haystack = body.lowercase()
            val hit = rules.lowerKeywords.indexOfFirst { keyword ->
                if (keyword in rules.lowerWholeWords) containsWholeWord(haystack, keyword)
                else haystack.contains(keyword)
            }
            if (hit >= 0) return RuleHit(RuleHit.Kind.KEYWORD, rules.keywords[hit])
        }
        return null
    }

    /** A null window — the common case and every legacy rule — always applies. */
    private fun TimeWindow?.allows(now: LocalDateTime): Boolean = this == null || contains(now)

    /** Case-insensitive substring match with a non-letter/digit boundary on both sides. */
    private fun containsWholeWord(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) return false
            val end = at + needle.length
            val leftOk = at == 0 || !haystack[at - 1].isLetterOrDigit()
            val rightOk = end == haystack.length || !haystack[end].isLetterOrDigit()
            if (leftOk && rightOk) return true
            from = at + 1
        }
    }

    /** AND needs every condition true, OR needs one; an empty rule never holds. */
    private fun holds(rule: CompoundRule, label: String, address: String?, body: String?): Boolean {
        if (rule.conditions.isEmpty()) return false
        val test = { c: Condition -> satisfied(c, label, address, body) }
        return if (rule.match == Match.All) rule.conditions.all(test) else rule.conditions.any(test)
    }

    /**
     * An AND rule's whole summary already names every condition, so it needs no
     * explanation. An OR rule did not necessarily fire all of them — say which one
     * actually matched, in the same `FIELD "value"` form the summary uses.
     */
    private fun explanation(
        rule: CompoundRule,
        label: String,
        address: String?,
        body: String?,
    ): String? {
        if (rule.match != Match.Any) return null
        val fired = rule.conditions.firstOrNull { satisfied(it, label, address, body) } ?: return null
        return "${fired.field.label} \"${fired.value}\""
    }

    private fun satisfied(c: Condition, label: String, address: String?, body: String?): Boolean =
        when (c.field) {
            Field.Name -> label.isNotBlank() && label.contains(c.value, ignoreCase = true)
            Field.Number -> !address.isNullOrBlank() && sameNumber(c.value, address)
            Field.Text -> !body.isNullOrBlank() && body.contains(c.value, ignoreCase = true)
        }

    fun isAllowed(address: String, rules: BlockStore.Rules): Boolean =
        rules.allowed.any { sameNumber(it, address) }

    private val emergencyNumbers =
        setOf("112", "911", "999", "110", "118", "102", "100", "108")

    fun isEmergency(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        return digits.isNotEmpty() && digits.length <= 4 && emergencyNumbers.contains(digits)
    }

    /**
     * Compares the last 8 digits so +92 300 7712008 matches 0300-7712008. When the
     * rule contains a wildcard it is matched as a digit glob instead: `*` is any run
     * of digits and `?` is exactly one. Patterns need at least five literal digits,
     * otherwise they are rejected rather than trusted with a real incoming message.
     * Allow-list and dedupe callers pass plain numbers and keep the suffix behaviour.
     */
    fun sameNumber(rule: String, address: String): Boolean {
        if (!rule.contains('*') && !rule.contains('?')) {
            val ruleDigits = rule.count { it.isDigit() }
            val addressDigits = address.count { it.isDigit() }
            if (ruleDigits < 5 || addressDigits < 5) return false
            // Walk both strings from the end, one digit at a time, instead of building
            // filtered copies: this runs for every message against every number rule, and
            // for every unnamed call against every saved contact.
            var i = rule.length - 1
            var j = address.length - 1
            repeat(minOf(ruleDigits, addressDigits, 8)) {
                while (!rule[i].isDigit()) i--
                while (!address[j].isDigit()) j--
                if (rule[i] != address[j]) return false
                i--
                j--
            }
            return true
        }
        if (rule.count { it.isDigit() } < 5) return false
        val pattern = rule.filter { it.isDigit() || it == '*' || it == '?' }
        val digits = address.filter { it.isDigit() }
        if (digits.length < 5) return false
        return wildcardMatch(pattern, digits)
    }

    /** Linear glob over a digit-only text: `?` consumes one digit, `*` consumes a run. */
    private fun wildcardMatch(pattern: String, text: String): Boolean {
        var p = 0
        var t = 0
        var star = -1
        var mark = 0
        while (t < text.length) {
            if (p < pattern.length && (pattern[p] == '?' || pattern[p] == text[t])) {
                p++
                t++
            } else if (p < pattern.length && pattern[p] == '*') {
                star = p
                mark = t
                p++
            } else if (star != -1) {
                p = star + 1
                t = ++mark
            } else {
                return false
            }
        }
        while (p < pattern.length && pattern[p] == '*') p++
        return p == pattern.length
    }
}
