package com.sarim.trinity.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure, host-testable pieces of the local embedding engine. No Android/ONNX calls. */
class EmbeddingMathTest {

    private val delta = 1e-5f

    // ---------------------------------------------------------------- cosine

    @Test
    fun cosine_identicalVectors_isOne() {
        val v = floatArrayOf(1f, 2f, 3f, 4f)
        assertEquals(1f, EmbeddingEngine.cosine(v, v), delta)
    }

    @Test
    fun cosine_orthogonalVectors_isZero() {
        assertEquals(0f, EmbeddingEngine.cosine(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
        ), delta)
    }

    @Test
    fun cosine_oppositeVectors_isMinusOne() {
        assertEquals(-1f, EmbeddingEngine.cosine(
            floatArrayOf(1f, 0f),
            floatArrayOf(-1f, 0f),
        ), delta)
    }

    @Test
    fun cosine_isInvariantToMagnitude() {
        assertEquals(1f, EmbeddingEngine.cosine(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(10f, 20f, 30f),
        ), delta)
    }

    @Test
    fun cosine_zeroVector_isZero() {
        assertEquals(0f, EmbeddingEngine.cosine(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 2f, 3f),
        ), delta)
    }

    @Test
    fun cosine_emptyInput_isZero() {
        assertEquals(0f, EmbeddingEngine.cosine(floatArrayOf(), floatArrayOf(1f)), delta)
    }

    @Test
    fun cosine_dimension_is384() {
        assertEquals(384, EmbeddingEngine.DIM)
    }

    // ------------------------------------------------------------ loadVocab

    /** Small inline vocabulary with deliberately known ids. */
    private val vocab = WordPieceTokenizer.loadVocab(
        listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "hello", "##ing", "wor", "##ld", ",", "world")
            .joinToString("\n", postfix = "\n"),
    )

    @Test
    fun loadVocab_assignsZeroBasedLineIds() {
        assertEquals(0, vocab["[PAD]"])
        assertEquals(2, vocab["[CLS]"])
        assertEquals(3, vocab["[SEP]"])
        assertEquals(4, vocab["hello"])
        assertEquals(5, vocab["##ing"])
        assertEquals(9, vocab["world"])
        assertEquals(10, vocab.size)
    }

    @Test
    fun loadVocab_handlesCrLfAndTrailingBlankLine() {
        val parsed = WordPieceTokenizer.loadVocab("[PAD]\r\n[UNK]\r\n[CLS]\r\n")
        assertEquals(0, parsed["[PAD]"])
        assertEquals(1, parsed["[UNK]"])
        assertEquals(2, parsed["[CLS]"])
        assertEquals(3, parsed.size) // blank trailing line is not a token
    }

    // --------------------------------------------------------------- encode

    @Test
    fun encode_wrapsWithClsSepAndPads() {
        assertArrayEquals(
            longArrayOf(2, 4, 3, 0, 0, 0, 0, 0),
            WordPieceTokenizer.encode("hello", vocab, 8),
        )
    }

    @Test
    fun encode_usesWordPieceContinuations() {
        // "helloing" -> "hello" (4) + "##ing" (5)
        assertArrayEquals(
            longArrayOf(2, 4, 5, 3, 0, 0),
            WordPieceTokenizer.encode("helloing", vocab, 6),
        )
    }

    @Test
    fun encode_lowercasesAndSplitsPunctuation() {
        assertArrayEquals(
            longArrayOf(2, 4, 8, 9, 3, 0, 0, 0, 0, 0),
            WordPieceTokenizer.encode("HELLO, world", vocab, 10),
        )
    }

    @Test
    fun encode_truncatesToMaxLenKeepingSep() {
        val encoded = WordPieceTokenizer.encode("hello hello hello hello", vocab, 5)
        assertEquals(5, encoded.size)
        assertArrayEquals(longArrayOf(2, 4, 4, 4, 3), encoded)
    }

    @Test
    fun encode_unknownWord_fallsBackToUnk() {
        assertArrayEquals(
            longArrayOf(2, 1, 3, 0),
            WordPieceTokenizer.encode("zzzzz", vocab, 4),
        )
    }

    @Test
    fun encode_emptyVocab_usesStandardSpecialTokenIds() {
        assertArrayEquals(
            longArrayOf(101, 100, 102, 0),
            WordPieceTokenizer.encode("anything", emptyMap(), 4),
        )
    }

    @Test
    fun encode_nonPositiveMaxLen_returnsEmpty() {
        assertTrue(WordPieceTokenizer.encode("hello", vocab, 0).isEmpty())
    }
}
