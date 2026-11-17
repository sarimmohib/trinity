package com.sarim.trinity.data

import android.content.Context

/** What to do with a caller Trinity does not recognise. */
enum class ScreeningAction { SILENCE, REJECT }

/** The two call-screening settings, owned by `trinity.ui`. Default is screening off. */
data class ScreeningSettings(
    val screenUnknown: Boolean = false,
    val action: ScreeningAction = ScreeningAction.SILENCE,
)

/** Reads and writes the screening primitives in the shared `trinity.ui` preferences file. */
object ScreeningPrefs {
    private const val FILE = "trinity.ui"
    private const val KEY_SCREEN_UNKNOWN = "screen_unknown"
    private const val KEY_ACTION = "screen_action"

    fun load(context: Context): ScreeningSettings {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val screenUnknown = prefs.getBoolean(KEY_SCREEN_UNKNOWN, false)
        val action = runCatching {
            ScreeningAction.valueOf(prefs.getString(KEY_ACTION, null).orEmpty())
        }.getOrDefault(ScreeningAction.SILENCE)
        return ScreeningSettings(screenUnknown = screenUnknown, action = action)
    }

    fun save(context: Context, settings: ScreeningSettings) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SCREEN_UNKNOWN, settings.screenUnknown)
            .putString(KEY_ACTION, settings.action.name)
            .apply()
    }
}

/** The outcome of screening one incoming call. [reason] names the rule that decided it. */
data class ScreeningDecision(
    val silence: Boolean,
    val reject: Boolean,
    val reason: String?,
)

/**
 * Pure call-screening decisions — no Android calls, so the whole table is JVM-testable.
 *
 * Order matters: emergencies always ring; a vouched-for number outranks every rule;
 * a rule scoped to calls then blocks outright; and a caller with no saved name is
 * silenced or rejected only when the user switched screening on.
 */
object CallScreening {
    /** Reason recorded when an unknown caller is screened by the user's setting. */
    const val UNKNOWN_REASON = "UNKNOWN CALLER"

    private val ALLOW = ScreeningDecision(silence = false, reject = false, reason = null)

    fun decide(
        number: String,
        name: String?,
        rules: BlockStore.Rules,
        settings: ScreeningSettings,
    ): ScreeningDecision {
        // 1. An emergency number always gets through, whatever the rules say.
        if (BlockMatcher.isEmergency(number)) return ALLOW

        // 2. The allow-list beats every rule and the unknown-caller screen.
        if (rules.allowed.any { BlockMatcher.sameNumber(it, number) }) return ALLOW

        // 3. A blocked caller is rejected; the hit's label becomes the audit reason.
        //    Passing a null body means text/keyword rules cannot bite a call.
        val hit = BlockMatcher.match(name, number, null, rules, Scope.Calls)
        if (hit != null) {
            return ScreeningDecision(silence = false, reject = true, reason = hit.label)
        }

        // 4. No saved name means "unknown"; act only when the user enabled screening.
        if (name.isNullOrBlank() && settings.screenUnknown) {
            return when (settings.action) {
                ScreeningAction.SILENCE ->
                    ScreeningDecision(silence = true, reject = false, reason = UNKNOWN_REASON)
                ScreeningAction.REJECT ->
                    ScreeningDecision(silence = false, reject = true, reason = UNKNOWN_REASON)
            }
        }

        // 5. A known caller with no matching rule rings normally.
        return ALLOW
    }
}
