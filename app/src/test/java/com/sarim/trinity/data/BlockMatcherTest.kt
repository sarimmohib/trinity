package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockMatcherTest {

    @Test
    fun sameNumber_matchesInternationalAndLocalForms() {
        assertTrue(BlockMatcher.sameNumber("+92 300 7712008", "0300-7712008"))
    }

    @Test
    fun sameNumber_ignoresFormatting() {
        assertTrue(BlockMatcher.sameNumber("0300 771 2008", "03007712008"))
    }

    @Test
    fun sameNumber_ignoresLeadingZero() {
        assertTrue(BlockMatcher.sameNumber("03007712008", "3007712008"))
    }

    @Test
    fun sameNumber_matchesOnStoredSuffix() {
        assertTrue(BlockMatcher.sameNumber("7712008", "0300 771 2008"))
    }

    @Test
    fun sameNumber_rejectsShortCodes() {
        assertFalse(BlockMatcher.sameNumber("911", "0911"))
        assertFalse(BlockMatcher.sameNumber("1122", "01122"))
        assertFalse(BlockMatcher.sameNumber("1234", "1234"))
    }

    @Test
    fun sameNumber_rejectsDifferentNumbers() {
        assertFalse(BlockMatcher.sameNumber("03007712008", "03007712009"))
    }

    @Test
    fun isEmergency_recognisesKnownNumbers() {
        listOf("112", "911", "999", "110", "118", "102", "100", "108").forEach {
            assertTrue(it, BlockMatcher.isEmergency(it))
        }
    }

    @Test
    fun isEmergency_toleratesFormatting() {
        assertTrue(BlockMatcher.isEmergency("9-1-1"))
        assertTrue(BlockMatcher.isEmergency(" 112 "))
    }

    @Test
    fun isEmergency_rejectsNormalAndNonNumericInput() {
        assertFalse(BlockMatcher.isEmergency("03007712008"))
        assertFalse(BlockMatcher.isEmergency("1234"))
        assertFalse(BlockMatcher.isEmergency("abc"))
        assertFalse(BlockMatcher.isEmergency(""))
    }

    @Test
    fun match_nameBeatsNumberAndKeyword() {
        val rules = BlockStore.Rules(
            names = listOf(Rule("bank")),
            numbers = listOf(Rule("03007712008")),
            keywords = listOf("otp"),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.NAME, "bank"),
            BlockMatcher.match("Bank of Stuff", "03007712008", "your otp is 1234", rules),
        )
    }

    @Test
    fun match_numberBeatsSenderCombo() {
        val rules = BlockStore.Rules(
            numbers = listOf(Rule("03007712008")),
            combined = listOf(
                CompoundRule(Match.All, listOf(Condition(Field.Number, "03007712008"))),
            ),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.NUMBER, "03007712008"),
            BlockMatcher.match(null, "03007712008", null, rules),
        )
    }

    @Test
    fun match_senderComboBeatsAllowList() {
        val rules = BlockStore.Rules(
            allowed = listOf("03007712008"),
            combined = listOf(
                CompoundRule(Match.All, listOf(Condition(Field.Number, "03007712008"))),
            ),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO, "NUMBER \"03007712008\""),
            BlockMatcher.match(null, "03007712008", null, rules),
        )
    }

    @Test
    fun match_allowListBeatsTextCombo() {
        val rules = BlockStore.Rules(
            allowed = listOf("03007712008"),
            combined = listOf(
                CompoundRule(Match.All, listOf(Condition(Field.Text, "otp"))),
            ),
        )
        assertNull(BlockMatcher.match(null, "03007712008", "your otp is 1234", rules))
    }

    @Test
    fun match_textComboBeatsKeyword() {
        val rules = BlockStore.Rules(
            keywords = listOf("otp"),
            combined = listOf(
                CompoundRule(Match.All, listOf(Condition(Field.Text, "otp"))),
            ),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO_TEXT, "TEXT \"otp\""),
            BlockMatcher.match(null, "03007712008", "your otp is 1234", rules),
        )
    }

    @Test
    fun match_keywordAlone() {
        val rules = BlockStore.Rules(keywords = listOf("lottery"))
        assertEquals(
            RuleHit(RuleHit.Kind.KEYWORD, "lottery"),
            BlockMatcher.match(null, "03007712008", "You won the LOTTERY!", rules),
        )
    }

    @Test
    fun match_emergencyBypassesEveryRule() {
        val rules = BlockStore.Rules(
            names = listOf(Rule("emergency")),
            numbers = listOf(Rule("911")),
            keywords = listOf("help"),
        )
        assertNull(BlockMatcher.match("Emergency Services", "911", "help", rules))
    }

    @Test
    fun match_respectsRuleScope() {
        val rules = BlockStore.Rules(numbers = listOf(Rule("03007712008", Scope.Calls)))
        assertNull(BlockMatcher.match(null, "03007712008", null, rules, Scope.Texts))
        assertEquals(
            RuleHit(RuleHit.Kind.NUMBER, "03007712008"),
            BlockMatcher.match(null, "03007712008", null, rules, Scope.Calls),
        )
    }

    @Test
    fun match_compoundAndRequiresEveryCondition() {
        val rules = BlockStore.Rules(
            combined = listOf(
                CompoundRule(
                    Match.All,
                    listOf(Condition(Field.Name, "bank"), Condition(Field.Number, "03007712008")),
                ),
            ),
        )
        assertNull(BlockMatcher.match("Bank", "03009998888", null, rules))
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO, "NAME \"bank\" AND NUMBER \"03007712008\""),
            BlockMatcher.match("Bank", "03007712008", null, rules),
        )
    }

    @Test
    fun match_compoundOrNeedsOneCondition() {
        val rules = BlockStore.Rules(
            combined = listOf(
                CompoundRule(
                    Match.Any,
                    listOf(Condition(Field.Name, "bank"), Condition(Field.Number, "03007712008")),
                ),
            ),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO, "NAME \"bank\" OR NUMBER \"03007712008\""),
            BlockMatcher.match("Unknown", "03007712008", null, rules),
        )
    }

    @Test
    fun match_emptyRulesNeverHit() {
        assertNull(BlockMatcher.match("Anyone", "03007712008", "anything", BlockStore.Rules()))
    }
}
