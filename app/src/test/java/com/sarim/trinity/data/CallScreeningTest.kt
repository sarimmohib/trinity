package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure screening decision table. No Android framework calls. */
class CallScreeningTest {

    private val screeningOff = ScreeningSettings()
    private val screenSilence = ScreeningSettings(screenUnknown = true, action = ScreeningAction.SILENCE)
    private val screenReject = ScreeningSettings(screenUnknown = true, action = ScreeningAction.REJECT)

    @Test
    fun emergency_alwaysAllowed_evenWithRulesAndScreening() {
        val rules = BlockStore.Rules(
            numbers = listOf(Rule("911")),
            names = listOf(Rule("emergency")),
        )
        val decision = CallScreening.decide("911", null, rules, screenReject)
        assertFalse(decision.silence)
        assertFalse(decision.reject)
        assertNull(decision.reason)
    }

    @Test
    fun allowList_beatsNumberRule() {
        val rules = BlockStore.Rules(
            allowed = listOf("03007712008"),
            numbers = listOf(Rule("03007712008", Scope.Calls)),
        )
        val decision = CallScreening.decide("+92 300 7712008", "Spam", rules, screeningOff)
        assertFalse(decision.silence)
        assertFalse(decision.reject)
        assertNull(decision.reason)
    }

    @Test
    fun allowList_beatsNameRule() {
        val rules = BlockStore.Rules(
            allowed = listOf("03007712008"),
            names = listOf(Rule("bank", Scope.Calls)),
        )
        val decision = CallScreening.decide("03007712008", "Bank of Stuff", rules, screeningOff)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
    }

    @Test
    fun allowList_beatsUnknownScreening() {
        val rules = BlockStore.Rules(allowed = listOf("03007712008"))
        val decision = CallScreening.decide("03007712008", null, rules, screenSilence)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
        assertNull(decision.reason)
    }

    @Test
    fun callsScopedNumberRule_rejects_withNumberRuleReason() {
        val rules = BlockStore.Rules(numbers = listOf(Rule("03007712008", Scope.Calls)))
        val decision = CallScreening.decide("03007712008", "Someone", rules, screeningOff)
        assertTrue(decision.reject)
        assertFalse(decision.silence)
        assertEquals("NUMBER RULE", decision.reason)
    }

    @Test
    fun textsScopedNumberRule_doesNotBlockCalls() {
        val rules = BlockStore.Rules(numbers = listOf(Rule("03007712008", Scope.Texts)))
        val decision = CallScreening.decide("03007712008", "Someone", rules, screeningOff)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
        assertNull(decision.reason)
    }

    @Test
    fun bothScopedNumberRule_blocksCallsToo() {
        val rules = BlockStore.Rules(numbers = listOf(Rule("03007712008", Scope.Both)))
        val decision = CallScreening.decide("03007712008", "Someone", rules, screeningOff)
        assertTrue(decision.reject)
        assertEquals("NUMBER RULE", decision.reason)
    }

    @Test
    fun nameRule_rejects_withNameRuleReason() {
        val rules = BlockStore.Rules(names = listOf(Rule("bank", Scope.Calls)))
        val decision = CallScreening.decide("03007712008", "Bank of Stuff", rules, screeningOff)
        assertTrue(decision.reject)
        assertEquals("NAME RULE", decision.reason)
    }

    @Test
    fun callsScopedComboRule_rejects_withCombinedRuleReason() {
        val rules = BlockStore.Rules(
            combined = listOf(
                CompoundRule(Match.All, listOf(Condition(Field.Name, "bank")), Scope.Calls),
            ),
        )
        val decision = CallScreening.decide("03007712008", "Bank", rules, screeningOff)
        assertTrue(decision.reject)
        assertEquals("COMBINED RULE", decision.reason)
    }

    @Test
    fun keywordRule_doesNotBlockCalls_bodyIsNeverRead() {
        val rules = BlockStore.Rules(keywords = listOf("otp"))
        val decision = CallScreening.decide("03007712008", "Someone", rules, screeningOff)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
    }

    @Test
    fun unknownScreening_off_allowsUnknownCaller() {
        val decision = CallScreening.decide("03007712008", null, BlockStore.Rules(), screeningOff)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
        assertNull(decision.reason)
    }

    @Test
    fun unknownScreening_on_silencesByDefault_withReason() {
        val decision = CallScreening.decide("03007712008", null, BlockStore.Rules(), screenSilence)
        assertTrue(decision.silence)
        assertFalse(decision.reject)
        assertEquals(CallScreening.UNKNOWN_REASON, decision.reason)
    }

    @Test
    fun unknownScreening_on_rejectsWhenActionIsReject() {
        val decision = CallScreening.decide("03007712008", null, BlockStore.Rules(), screenReject)
        assertTrue(decision.reject)
        assertFalse(decision.silence)
        assertEquals(CallScreening.UNKNOWN_REASON, decision.reason)
    }

    @Test
    fun blankName_countsAsUnknown() {
        val decision = CallScreening.decide("03007712008", "   ", BlockStore.Rules(), screenSilence)
        assertTrue(decision.silence)
        assertEquals(CallScreening.UNKNOWN_REASON, decision.reason)
    }

    @Test
    fun knownCaller_isNotScreenedAsUnknown() {
        val decision = CallScreening.decide("03007712008", "Bilal", BlockStore.Rules(), screenReject)
        assertFalse(decision.reject)
        assertFalse(decision.silence)
        assertNull(decision.reason)
    }

    @Test
    fun ruleHit_rejectsEvenWhenUnknownActionIsSilence() {
        val rules = BlockStore.Rules(numbers = listOf(Rule("03007712008", Scope.Calls)))
        val decision = CallScreening.decide("03007712008", null, rules, screenSilence)
        assertTrue(decision.reject)
        assertFalse(decision.silence)
        assertEquals("NUMBER RULE", decision.reason)
    }
}
