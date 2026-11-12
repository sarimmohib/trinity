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
    ) {
        val isEmpty: Boolean get() = names.isEmpty() && numbers.isEmpty() && keywords.isEmpty()
        val ruleCount: Int get() = names.size + numbers.size + keywords.size
    }

    private fun read(): Rules = Rules(
        names = readRules("names"),
        numbers = readRules("numbers"),
        keywords = readList("keywords"),
        allowed = readList("allowed"),
        hideMatchedCalls = prefs.getBoolean("hideMatchedCalls", true),
    )

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
    fun removeKeyword(value: String) = update { it.copy(keywords = it.keywords - value) }
    fun removeAllowed(value: String) = update {
        it.copy(allowed = it.allowed.filterNot { entry -> BlockMatcher.sameNumber(entry, value) || entry == value })
    }

    fun setHideMatchedCalls(enabled: Boolean) = update { it.copy(hideMatchedCalls = enabled) }

    /** Drops every name, number, keyword and allowance; the toggle survives. */
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
        // A vouched-for sender keeps their texts; keywords stop applying to them.
        if (!address.isNullOrBlank() && isAllowed(address, rules)) return null

        if (!body.isNullOrBlank()) {
            val haystack = body.lowercase()
            rules.keywords.firstOrNull { haystack.contains(it.lowercase()) }
                ?.let { return RuleHit(RuleHit.Kind.KEYWORD, it) }
        }
        return null
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
        val a = rule.filter { it.isDigit() }
        val b = address.filter { it.isDigit() }
        if (a.length < 5 || b.length < 5) return false
        val tail = minOf(a.length, b.length, 8)
        return a.takeLast(tail) == b.takeLast(tail)
    }
}
