package com.sarim.trinity.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

/** A blocked name or number, and the surface it was meant for. */
data class Rule(val value: String, val scope: Scope = Scope.Both)

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
 * no conditions never matches.
 */
data class CompoundRule(
    val match: Match,
    val conditions: List<Condition>,
    val scope: Scope = Scope.Both,
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
    ) {
        val isEmpty: Boolean
            get() = names.isEmpty() && numbers.isEmpty() && keywords.isEmpty() && combined.isEmpty()
        val ruleCount: Int get() = names.size + numbers.size + keywords.size + combined.size

        /** Split once per rule set: sender-only combined rules run before the allow-list, text ones after. */
        val senderCombined: List<CompoundRule> by lazy { combined.filterNot { it.readsText } }
        val textCombined: List<CompoundRule> by lazy { combined.filter { it.readsText } }

        /** Lower-cased once per rule set, not once per keyword for every message checked. */
        val lowerKeywords: List<String> by lazy { keywords.map { it.lowercase() } }
    }

    private fun read(): Rules = Rules(
        names = readRules("names"),
        numbers = readRules("numbers"),
        keywords = readList("keywords"),
        allowed = readList("allowed"),
        hideMatchedCalls = prefs.getBoolean("hideMatchedCalls", true),
        combined = readCombined(),
    )

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
                CompoundRule(match, conditions, scope).takeIf { conditions.isNotEmpty() }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeCombined(rules: List<CompoundRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            val list = JSONArray()
            rule.conditions.forEach { list.put(JSONObject().put("f", it.field.name).put("v", it.value)) }
            array.put(JSONObject().put("m", rule.match.name).put("s", rule.scope.name).put("c", list))
        }
        prefs.edit().putString("combined", array.toString()).apply()
    }

    /**
     * Rules are stored as objects carrying their scope. Anything written by an
     * older build is a bare string, which meant "both" — that is how an existing
     * rule keeps behaving exactly as it did before this field existed.
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
                        Rule(value, scope).takeIf { value.isNotBlank() }
                    }
                    else -> null
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRules(key: String, rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().put("v", rule.value).put("s", rule.scope.name))
        }
        prefs.edit().putString(key, array.toString()).apply()
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

    private fun update(block: (Rules) -> Rules) {
        val next = block(_rules.value)
        writeRules("names", next.names)
        writeRules("numbers", next.numbers)
        writeList("keywords", next.keywords)
        writeList("allowed", next.allowed)
        writeCombined(next.combined)
        prefs.edit().putBoolean("hideMatchedCalls", next.hideMatchedCalls).apply()
        _rules.value = next
    }

    fun addName(raw: String, scope: Scope = Scope.Both) {
        val value = raw.trim()
        if (value.isBlank()) return
        if (value.any { it.isDigit() } && value.count { it.isDigit() } >= 5) {
            addNumber(value, scope)
            return
        }
        update { current ->
            if (current.names.any { it.value.equals(value, ignoreCase = true) && it.scope == scope }) current
            else current.copy(names = current.names + Rule(value, scope))
        }
    }

    fun addKeyword(raw: String) {
        val value = raw.trim()
        if (value.isBlank()) return
        update { current ->
            if (current.keywords.any { it.equals(value, ignoreCase = true) }) current
            else current.copy(keywords = current.keywords + value)
        }
    }

    fun addNumber(raw: String, scope: Scope = Scope.Both) {
        val value = raw.trim()
        if (value.isBlank()) return
        update { current ->
            if (current.numbers.any { it.value == value && it.scope == scope }) current
            else current.copy(numbers = current.numbers + Rule(value, scope))
        }
    }

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
    fun addCombined(rule: CompoundRule) {
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

    fun removeCombined(rule: CompoundRule) = update { it.copy(combined = it.combined - rule) }

    fun removeKeyword(value: String) = update { it.copy(keywords = it.keywords - value) }
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
 * Rule evaluation. Order matters: name, then number, then keyword. [channel] is the
 * surface asking — texts, calls, or null for "this contact is blocked somewhere".
 */
object BlockMatcher {

    fun match(
        name: String?,
        address: String?,
        body: String?,
        rules: BlockStore.Rules,
        channel: Scope? = null,
    ): RuleHit? {
        // Emergencies always go through, whatever the rules say.
        if (!address.isNullOrBlank() && isEmergency(address)) return null
        val label = name?.trim().orEmpty()
        if (label.isNotBlank()) {
            rules.names.firstOrNull { it.scope.covers(channel) && label.contains(it.value, ignoreCase = true) }
                ?.let { return RuleHit(RuleHit.Kind.NAME, it.value) }
        }
        if (!address.isNullOrBlank()) {
            rules.numbers.firstOrNull { it.scope.covers(channel) && sameNumber(it.value, address) }
                ?.let { return RuleHit(RuleHit.Kind.NUMBER, it.value) }
        }
        // Combined rules that only look at the sender are as firm as a name or number rule.
        rules.senderCombined.firstOrNull { it.scope.covers(channel) && holds(it, label, address, body) }
            ?.let { return RuleHit(RuleHit.Kind.COMBO, it.summary) }

        // A vouched-for sender keeps their texts; keywords stop applying to them.
        if (!address.isNullOrBlank() && isAllowed(address, rules)) return null

        rules.textCombined.firstOrNull { it.scope.covers(channel) && holds(it, label, address, body) }
            ?.let { return RuleHit(RuleHit.Kind.COMBO_TEXT, it.summary) }

        if (!body.isNullOrBlank()) {
            val haystack = body.lowercase()
            val hit = rules.lowerKeywords.indexOfFirst { haystack.contains(it) }
            if (hit >= 0) return RuleHit(RuleHit.Kind.KEYWORD, rules.keywords[hit])
        }
        return null
    }

    /** AND needs every condition true, OR needs one; an empty rule never holds. */
    private fun holds(rule: CompoundRule, label: String, address: String?, body: String?): Boolean {
        if (rule.conditions.isEmpty()) return false
        val test = { c: Condition -> satisfied(c, label, address, body) }
        return if (rule.match == Match.All) rule.conditions.all(test) else rule.conditions.any(test)
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

    /** Compares the last 8 digits so +92 300 7712008 matches 0300-7712008. */
    fun sameNumber(rule: String, address: String): Boolean {
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
}
