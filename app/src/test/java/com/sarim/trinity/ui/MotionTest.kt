package com.sarim.trinity.ui

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the performance budget agreed in the motion spec: short, GPU-friendly, off-able. */
class MotionTest {

    @Test
    fun everyDurationFitsTheBudget() {
        assertTrue(Motion.PRESS_MS in 1..120)
        assertTrue(Motion.ROW_MS in 1..240)
        assertTrue(Motion.SCREEN_MS in 1..240)
        assertTrue(Motion.SHEET_MS in 1..240)
    }

    @Test
    fun disabledMotionUsesSnap() {
        assertTrue(Motion.alphaSpec(Motion.SCREEN_MS, false) is SnapSpec<*>)
    }

    @Test
    fun enabledMotionUsesTween() {
        assertTrue(Motion.alphaSpec(Motion.SCREEN_MS, true) is TweenSpec<*>)
    }
}
