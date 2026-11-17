package com.sarim.trinity.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Local sentence embeddings for Trinity, backed by the bundled quantised
 * `Xenova/all-MiniLM-L6-v2` ONNX model (Apache-2.0, 384 dimensions).
 *
 * The model and vocabulary are bundled as assets; there is no network code here.
 * The ONNX session and the vocabulary are each loaded once and cached in memory.
 *
 * Threading: [embed] performs blocking ONNX inference. Call it from a background
 * thread (ideally `Dispatchers.IO`); as a safety net, a call made on the main
 * thread is shifted to `Dispatchers.IO` via [runBlocking] so the model is never
 * loaded or run on main. Every fallible path is wrapped in [runCatching] and
 * reports failure by returning an empty list rather than throwing.
 */
object EmbeddingEngine {

    const val DIM = 384

    private const val MODEL_PATH = "models/minilm.onnx"
    private const val VOCAB_PATH = "models/vocab.txt"
    private const val SEQ_LEN = 128
    private const val BATCH_SIZE = 32
    private const val OUTPUT_NAME = "last_hidden_state"
    private const val PAD_ID = 0L

    /** The single ONNX session, created lazily from the asset bytes. */
    private object Sessions {
        @Volatile
        private var session: OrtSession? = null
        private val lock = Any()

        fun get(context: Context): OrtSession? {
            session?.let { return it }
            return synchronized(lock) {
                session ?: runCatching {
                    // Prefer bytes: ORT Android accepts a ByteArray directly, so the
                    // asset never has to be copied into filesDir.
                    val bytes = context.assets.open(MODEL_PATH).use { it.readBytes() }
                    OrtEnvironment.getEnvironment().createSession(bytes)
                }.getOrNull().also { session = it }
            }
        }
    }

    /** Vocabulary cache — the 30k-line file is parsed once per process. */
    @Volatile
    private var cachedVocab: Map<String, Int>? = null
    private val vocabLock = Any()

    private fun vocab(context: Context): Map<String, Int>? {
        cachedVocab?.let { return it }
        return synchronized(vocabLock) {
            cachedVocab ?: runCatching {
                val text = context.assets.open(VOCAB_PATH).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                WordPieceTokenizer.loadVocab(text)
            }.getOrNull().also { cachedVocab = it }
        }
    }

    /** Cheap availability check: verify both bundled assets are present without
     * loading the 23 MB model. */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.assets.open(MODEL_PATH).use { }
        context.assets.open(VOCAB_PATH).use { }
        true
    }.getOrDefault(false)

    /**
     * Embed each input into a unit-length [DIM]-vector. See the class KDoc for
     * threading. Returns an empty list (never throws) when ONNX is unavailable or
     * inference fails; on success the result is aligned one-to-one with [texts].
     */
    fun embed(context: Context, texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()
        return if (onMainThread()) {
            // Blocks the caller while the work runs on IO, but keeps ONNX off main.
            runBlocking(Dispatchers.IO) { embedOnIo(context, texts) }
        } else {
            embedOnIo(context, texts)
        }
    }

    private fun onMainThread(): Boolean =
        Looper.myLooper() == Looper.getMainLooper()

    private fun embedOnIo(context: Context, texts: List<String>): List<FloatArray> =
        runCatching {
            val session = Sessions.get(context) ?: return@runCatching emptyList()
            val vocabulary = vocab(context) ?: return@runCatching emptyList()
            val out = ArrayList<FloatArray>(texts.size)
            for (chunk in texts.chunked(BATCH_SIZE)) {
                val vectors = embedBatch(session, chunk, vocabulary)
                if (vectors.size != chunk.size) return@runCatching emptyList()
                out.addAll(vectors)
            }
            out
        }.getOrDefault(emptyList())

    /** Run one inference batch and mean-pool the last hidden state over real tokens,
     * then L2-normalise. */
    private fun embedBatch(
        session: OrtSession,
        texts: List<String>,
        vocab: Map<String, Int>,
    ): List<FloatArray> = runCatching {
        val env = OrtEnvironment.getEnvironment()
        val batch = texts.size
        val ids = LongArray(batch * SEQ_LEN)
        val mask = LongArray(batch * SEQ_LEN)
        val typeIds = LongArray(batch * SEQ_LEN) // single segment: all zeros

        for (b in texts.indices) {
            val encoded = WordPieceTokenizer.encode(texts[b], vocab, SEQ_LEN)
            for (s in 0 until SEQ_LEN) {
                val at = b * SEQ_LEN + s
                val id = encoded[s]
                ids[at] = id
                mask[at] = if (id == PAD_ID) 0L else 1L
            }
        }

        val shape = longArrayOf(batch.toLong(), SEQ_LEN.toLong())
        val idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape)
        val maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape)
        val typeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(typeIds), shape)

        val result = session.run(
            mapOf(
                "input_ids" to idsTensor,
                "attention_mask" to maskTensor,
                "token_type_ids" to typeTensor,
            ),
        )
        val vectors = result.use { res ->
            // Prefer the discovered output name; fall back to the first output.
            // Result.get(String) returns an Optional; get(int) returns OnnxValue.
            val value = runCatching { res.get(OUTPUT_NAME).orElse(null) }.getOrNull()
                ?: runCatching { res.get(0) }.getOrNull()
                ?: return@use emptyList()
            val tensor = value as? OnnxTensor ?: return@use emptyList()

            val shapeOut = runCatching { tensor.info.shape }.getOrNull()
            val seqOut = shapeOut?.getOrNull(1)?.toInt() ?: SEQ_LEN
            val dimOut = shapeOut?.getOrNull(2)?.toInt() ?: DIM

            val buffer: FloatBuffer = tensor.getFloatBuffer()
            val flat = FloatArray(buffer.remaining())
            buffer.get(flat)

            val pooled = ArrayList<FloatArray>(batch)
            for (b in 0 until batch) {
                val vector = FloatArray(DIM)
                var count = 0
                for (s in 0 until seqOut) {
                    if (mask[b * SEQ_LEN + s] == 1L) {
                        val base = (b * seqOut + s) * dimOut
                        for (d in 0 until minOf(dimOut, DIM)) {
                            vector[d] += flat[base + d]
                        }
                        count++
                    }
                }
                if (count > 0) {
                    for (d in 0 until minOf(dimOut, DIM)) {
                        vector[d] = vector[d] / count
                    }
                }
                var norm = 0f
                for (d in vector.indices) norm += vector[d] * vector[d]
                if (norm > 1e-12f) {
                    val inv = 1f / sqrt(norm)
                    for (d in vector.indices) vector[d] *= inv
                }
                pooled.add(vector)
            }
            pooled
        }
        runCatching { idsTensor.close() }
        runCatching { maskTensor.close() }
        runCatching { typeTensor.close() }
        vectors
    }.getOrDefault(emptyList())

    /** Cosine similarity of two vectors; pure, dimension-agnostic, no Android deps.
     * Identical directions give 1, orthogonal give 0, and a zero vector gives 0. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        var i = 0
        while (i < n) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
            i++
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-12f) dot / denom else 0f
    }
}
