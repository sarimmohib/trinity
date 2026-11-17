package com.sarim.trinity.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf

/**
 * Trinity's motion vocabulary: a printed page being turned, not a game.
 *
 * All durations are short enough that navigation never feels like it is waiting.
 * Every animation uses only alpha/translation/scale (GPU-composited properties),
 * so enabling motion does not trigger layout or recomposition per frame. Turning
 * motion OFF replaces every spec with [snap] — state changes are instant.
 */
object Motion {
    const val SCREEN_MS = 180
    const val SHEET_MS = 200
    const val ROW_MS = 140
    const val PRESS_MS = 90

    val Easing = FastOutSlowInEasing

    fun alphaSpec(ms: Int, enabled: Boolean) =
        if (enabled) tween<Float>(ms, easing = Easing) else snap<Float>()
}

/** False means no transitions at all, for people who prefer still interfaces. */
val LocalMotionEnabled = compositionLocalOf { true }
