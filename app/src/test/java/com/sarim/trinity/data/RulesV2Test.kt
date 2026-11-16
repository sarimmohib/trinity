package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RulesV2Test {

    private val noon = LocalDateTime.of(2026, 11, 17, 12, 0) // Tuesday

    // ---- Time windows -------------------------------------------------------

    @Test
    fun window_insideAndOutside() {
        val window = TimeWindow(9 * 60, 17 * 60)
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 17, 9, 0)))
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 17, 12, 0)))
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 17, 17, 0)))
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 17, 8, 59)))
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 17, 17, 1)))
    }

    @Test
    fun window_crossesMidnight() {
        val window = TimeWindow(22 * 60, 7 * 60)
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 17, 22, 0)))
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 18, 3, 0)))
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 18, 7, 0)))
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 17, 12, 0)))
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 17, 21, 59)))
    }

    @Test
    fun window_singleDay() {
        val window = TimeWindow(0, 1439, setOf(1)) // Mondays only, all day
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 16, 12, 0))) // Monday
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 17, 12, 0))) // Tuesday
        assertFalse(window.contains(LocalDateTime.of(2026, 11, 22, 12, 0))) // Sunday
    }

    @Test
    fun window_emptyDaysMeansEveryDay() {
        val window = TimeWindow(10 * 60, 11 * 60)
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 16, 10, 30))) // Monday
        assertTrue(window.contains(LocalDateTime.of(2026, 11, 22, 10, 30))) // Sunday
    }

    // ---- Number patterns ----------------------------------------------------

    @Test
    fun pattern_starMatchesAnyRunOfDigits() {
        assertTrue(BlockMatcher.sameNumber("+92 300 *", "+923007712008"))
        assertTrue(BlockMatcher.sameNumber("+92 300 *", "92300123"))
        assertFalse(BlockMatcher.sameNumber("+92 300 *", "03007712008"))
    }

    @Test
    fun pattern_questionMatchesExactlyOneDigit() {
        assertTrue(BlockMatcher.sameNumber("92300?????", "9230077120"))
        assertFalse(BlockMatcher.sameNumber("92300?????", "923007712")) // one digit short
        assertFalse(BlockMatcher.sameNumber("92300?????", "92300771201")) // one digit long
    }

    @Test
    fun pattern_tooFewLiteralDigitsNeverMatches() {
        // Only four literal digits each — below the five-digit floor, so invalid.
        assertFalse(BlockMatcher.sameNumber("*1234", "030077121234"))
        assertFalse(BlockMatcher.sameNumber("0300???????", "03007712008"))
        assertFalse(BlockMatcher.sameNumber("1234", "1234"))
    }

    @Test
    fun pattern_noMatchAndInvalidPatternsNeverThrow() {
        assertFalse(BlockMatcher.sameNumber("92300*", "03007712008"))
        assertFalse(BlockMatcher.sameNumber("*", "03007712008"))
        assertFalse(BlockMatcher.sameNumber("?", "03007712008"))
        assertFalse(BlockMatcher.sameNumber("abc*def", "03007712008"))
    }

    @Test
    fun pattern_plainNumberKeepsExactSuffixMatch() {
        assertTrue(BlockMatcher.sameNumber("0300 771 2008", "03007712008"))
        assertTrue(BlockMatcher.sameNumber("7712008", "0300 771 2008"))
        assertFalse(BlockMatcher.sameNumber("03007712008", "03007712009"))
    }

    // ---- Whole-word keywords ------------------------------------------------

    @Test
    fun keyword_wholeWordRequiresBoundaries() {
        val rules = BlockStore.Rules(keywords = listOf("otp"), wholeWordKeywords = listOf("otp"))
        assertNull(BlockMatcher.match(null, "03007712008", "photos of the trip", rules))
        assertNull(BlockMatcher.match(null, "03007712008", "slotp arrived", rules))
        assertNull(BlockMatcher.match(null, "03007712008", "otplus arrived", rules))
        assertKeywordHit(BlockMatcher.match(null, "03007712008", "OTP", rules))
        assertKeywordHit(BlockMatcher.match(null, "03007712008", "your OTP is 1234", rules))
        assertKeywordHit(BlockMatcher.match(null, "03007712008", "otp.", rules))
    }

    @Test
    fun keyword_normalStillMatchesSubstring() {
        val rules = BlockStore.Rules(keywords = listOf("otp"))
        assertKeywordHit(BlockMatcher.match(null, "03007712008", "slotp arrived", rules))
        assertNull(BlockMatcher.match(null, "03007712008", "photos", rules))
    }

    private fun assertKeywordHit(hit: RuleHit?) {
        assertEquals(RuleHit(RuleHit.Kind.KEYWORD, "otp"), hit)
    }

    // ---- JSON backward compatibility --------------------------------------

    @Test
    fun json_oldBareStringsLoadUnchanged() {
        val raw = """{"schema":1,"name":"old","names":["Bank"],""" +
            """"numbers":[{"v":"03007712008","s":"Calls"}],"keywords":["otp","lottery"],""" +
            """"combined":[{"m":"All","s":"Both","c":[{"f":"Name","v":"bank"}]}],""" +
            """"allowed":["UPI"],"exportedAt":1}"""
        val pack = RulePack.parse(raw)
        assertNotNull(pack)
        pack!!
        assertEquals("old", pack.name)
        assertEquals(listOf(Rule("Bank")), pack.names)
        assertNull(pack.names[0].window)
        assertEquals(listOf(Rule("03007712008", Scope.Calls)), pack.numbers)
        assertEquals(listOf("otp", "lottery"), pack.keywords)
        assertTrue(pack.wholeWordKeywords.isEmpty())
        assertEquals(
            listOf(CompoundRule(Match.All, listOf(Condition(Field.Name, "bank")), Scope.Both)),
            pack.combined,
        )
        assertEquals(listOf("UPI"), pack.allowed)
    }

    @Test
    fun json_newFieldsRoundTrip() {
        val window = TimeWindow(1320, 420, setOf(1, 2, 3, 4, 5))
        val pack = RulePack.Pack(
            name = "night",
            names = listOf(Rule("Bank", Scope.Both, window)),
            numbers = listOf(Rule("0300 771 *", Scope.Calls)),
            keywords = listOf("otp", "bank"),
            wholeWordKeywords = listOf("otp"),
            combined = listOf(
                CompoundRule(
                    Match.Any,
                    listOf(Condition(Field.Name, "bank"), Condition(Field.Text, "otp")),
                    Scope.Texts,
                    window,
                ),
            ),
            allowed = listOf("03009998888"),
            exportedAt = 123456789L,
        )
        val raw = RulePack.serialize(pack)
        assertTrue(raw.contains("\"w\""))
        assertTrue(raw.contains("\"ww\":true"))
        assertEquals(pack, RulePack.parse(raw))
    }

    // ---- Rule packs ---------------------------------------------------------

    @Test
    fun rulePack_rejectsInvalidFiles() {
        assertNull(RulePack.parse("not json"))
        assertNull(RulePack.parse("{}"))
        assertNull(RulePack.parse("""{"schema":2}"""))
        assertNull(RulePack.parse("""{"schema":1,"names":[{"v":"x","s":"Bogus"}]}"""))
        assertNull(RulePack.parse("""{"schema":1,"keywords":[42]}"""))
        assertNull(RulePack.parse("""{"schema":1,"combined":[{"m":"All","c":[]}]}"""))
        assertNull(RulePack.parse("""{"schema":1,"names":[{"v":"x","w":{"s":1,"e":2,"d":[9]}}]}"""))
        assertNotNull(RulePack.parse("""{"schema":1}"""))
    }

    @Test
    fun rulePack_mergeDoesNotDuplicate() {
        val current = BlockStore.Rules(
            names = listOf(Rule("Bank")),
            keywords = listOf("otp"),
            hideMatchedCalls = false,
        )
        val pack = RulePack.Pack(
            name = "p",
            names = listOf(Rule("bank"), Rule("Spam")),
            numbers = listOf(Rule("03007712008")),
            keywords = listOf("OTP", "lottery"),
            wholeWordKeywords = listOf("otp"),
            combined = emptyList(),
            allowed = listOf("care"),
            exportedAt = 1L,
        )
        val merged = RulePack.merge(current, pack)
        assertEquals(listOf(Rule("Bank"), Rule("Spam")), merged.names)
        assertEquals(listOf("otp", "lottery"), merged.keywords)
        assertEquals(listOf("otp"), merged.wholeWordKeywords)
        assertEquals(listOf(Rule("03007712008")), merged.numbers)
        assertEquals(listOf("care"), merged.allowed)
        assertFalse(merged.hideMatchedCalls)
    }

    @Test
    fun rulePack_replaceKeepsHideToggle() {
        val current = BlockStore.Rules(names = listOf(Rule("Old")), hideMatchedCalls = false)
        val pack = RulePack.Pack(
            name = "p",
            names = listOf(Rule("New")),
            numbers = emptyList(),
            keywords = listOf("otp"),
            wholeWordKeywords = listOf("otp"),
            combined = emptyList(),
            allowed = emptyList(),
            exportedAt = 1L,
        )
        val replaced = RulePack.replace(current, pack)
        assertEquals(pack.names, replaced.names)
        assertEquals(pack.keywords, replaced.keywords)
        assertEquals(pack.wholeWordKeywords, replaced.wholeWordKeywords)
        assertFalse(replaced.hideMatchedCalls)
    }

    @Test
    fun rulePack_previewCountsEachList() {
        val pack = RulePack.Pack(
            name = "p",
            names = listOf(Rule("a"), Rule("b")),
            numbers = listOf(Rule("1")),
            keywords = listOf("a", "b", "c"),
            wholeWordKeywords = emptyList(),
            combined = listOf(CompoundRule(Match.All, listOf(Condition(Field.Text, "x")))),
            allowed = listOf("z"),
            exportedAt = 1L,
        )
        assertEquals(
            "2 names · 1 number · 3 keywords · 1 combined · 1 allowed",
            RulePack.preview(pack),
        )
    }

    // ---- Precedence (unchanged) --------------------------------------------

    @Test
    fun precedence_nameBeatsEveryOtherLayer() {
        val rules = BlockStore.Rules(
            names = listOf(Rule("bank")),
            numbers = listOf(Rule("03007712008")),
            combined = listOf(CompoundRule(Match.All, listOf(Condition(Field.Text, "otp")))),
            allowed = listOf("03007712008"),
            keywords = listOf("otp"),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.NAME, "bank", scope = Scope.Both),
            BlockMatcher.match("Bank of Stuff", "03007712008", "your otp is 1234", rules, null, noon),
        )
    }

    @Test
    fun precedence_senderComboThenAllowThenTextComboThenKeyword() {
        val senderCombo = CompoundRule(Match.All, listOf(Condition(Field.Name, "spam")))
        val textCombo = CompoundRule(Match.All, listOf(Condition(Field.Text, "otp")))
        val rules = BlockStore.Rules(
            allowed = listOf("03007712008"),
            combined = listOf(senderCombo, textCombo),
            keywords = listOf("otp"),
        )
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO, senderCombo.summary, scope = Scope.Both),
            BlockMatcher.match("Spam Sender", "03007712008", null, rules, null, noon),
        )
        assertNull(BlockMatcher.match("Friend", "03007712008", "your otp", rules, null, noon))
        val withoutAllow = rules.copy(allowed = emptyList())
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO_TEXT, textCombo.summary, scope = Scope.Both),
            BlockMatcher.match("Friend", "03007712008", "your otp", withoutAllow, null, noon),
        )
    }

    @Test
    fun windowedRuleOnlyMatchesInsideItsWindow() {
        val night = TimeWindow(22 * 60, 7 * 60)
        val rules = BlockStore.Rules(
            names = listOf(Rule("Bank", Scope.Both, night)),
            keywords = listOf("otp"),
        )
        assertNull(BlockMatcher.match("Bank", "03007712008", null, rules, null, noon))
        assertEquals(
            RuleHit(RuleHit.Kind.NAME, "Bank", scope = Scope.Both),
            BlockMatcher.match("Bank", "03007712008", null, rules, null, LocalDateTime.of(2026, 11, 17, 23, 0)),
        )
    }

    @Test
    fun windowedCompoundAppliesToWholeRule() {
        val night = TimeWindow(22 * 60, 7 * 60)
        val combo = CompoundRule(
            Match.All,
            listOf(Condition(Field.Name, "bank"), Condition(Field.Text, "otp")),
            Scope.Both,
            night,
        )
        val rules = BlockStore.Rules(combined = listOf(combo))
        assertNull(BlockMatcher.match("Bank", "03007712008", "your otp", rules, null, noon))
        assertEquals(
            RuleHit(RuleHit.Kind.COMBO_TEXT, combo.summary, scope = Scope.Both),
            BlockMatcher.match("Bank", "03007712008", "your otp", rules, null, LocalDateTime.of(2026, 11, 17, 23, 0)),
        )
    }
}
