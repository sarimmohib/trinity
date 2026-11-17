package com.sarim.trinity.ml

/**
 * Minimal BERT-style WordPiece tokenizer for the bundled all-MiniLM-L6-v2 vocabulary.
 *
 * Pure JVM code: no Android or ONNX dependencies, so it is unit-testable on the host.
 */
object WordPieceTokenizer {

    private const val PAD = "[PAD]"
    private const val UNK = "[UNK]"
    private const val CLS = "[CLS]"
    private const val SEP = "[SEP]"

    /**
     * Read a `vocab.txt` (one token per line, Apache-2.0 BERT uncased vocabulary).
     * The 0-based line index is the token id, including for blank lines, so ids stay
     * aligned with the original file even if it contains an empty trailing line.
     */
    fun loadVocab(text: String): Map<String, Int> {
        val vocab = HashMap<String, Int>(32_000)
        var id = 0
        for (raw in text.split('\n')) {
            val token = if (raw.endsWith('\r')) raw.dropLast(1) else raw
            if (token.isNotEmpty() && !vocab.containsKey(token)) {
                vocab[token] = id
            }
            id++
        }
        return vocab
    }

    /** Lowercase, split punctuation, WordPiece into `##` continuations, wrap in
     * [CLS] … [SEP] and pad/truncate to exactly [maxLen] ids. */
    fun encode(text: String, vocab: Map<String, Int>, maxLen: Int = 128): LongArray {
        if (maxLen <= 0) return LongArray(0)

        val padId = (vocab[PAD] ?: 0).toLong()
        val unkId = (vocab[UNK] ?: 100).toLong()
        val clsId = (vocab[CLS] ?: 101).toLong()
        val sepId = (vocab[SEP] ?: 102).toLong()

        val result = LongArray(maxLen) { padId }
        result[0] = clsId

        if (maxLen == 1) return result

        // Reserve the final slot for [SEP].
        val budget = maxLen - 2
        var written = 0
        for (word in basicTokenize(text)) {
            if (written >= budget) break
            for (pieceId in wordPiece(word, vocab, unkId)) {
                if (written >= budget) break
                result[1 + written] = pieceId
                written++
            }
        }
        result[1 + written] = sepId
        return result
    }

    /** BERT "basic" tokenisation: lowercase and split on whitespace/punctuation,
     * keeping punctuation characters as their own tokens. */
    private fun basicTokenize(text: String): List<String> {
        val tokens = ArrayList<String>()
        val current = StringBuilder()
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit()) {
                current.append(ch)
            } else {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.setLength(0)
                }
                if (!ch.isWhitespace()) {
                    tokens.add(ch.toString())
                }
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())
        return tokens
    }

    /** Greedy longest-match-first WordPiece. Returns the single [unkId] if the
     * first sub-token cannot be matched, matching HuggingFace behaviour. */
    private fun wordPiece(word: String, vocab: Map<String, Int>, unkId: Long): List<Long> {
        val ids = ArrayList<Long>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var match: Int? = null
            while (start < end) {
                val piece = word.substring(start, end)
                val candidate = if (start == 0) piece else "##$piece"
                val id = vocab[candidate]
                if (id != null) {
                    match = id
                    break
                }
                end--
            }
            if (match == null) return listOf(unkId)
            ids.add(match.toLong())
            start = end
        }
        return ids
    }
}
