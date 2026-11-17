package com.sarim.trinity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.sarim.trinity.ml.EmbeddingEngine

/**
 * Local store for message embeddings. Plain [SQLiteOpenHelper] — no Room, no network,
 * nothing leaves the device. Every method is synchronous and every caller runs it on
 * [kotlinx.coroutines.Dispatchers.IO]; each operation is still wrapped in [runCatching]
 * so a corrupt or locked database degrades to an empty result instead of a crash.
 *
 * Vectors are stored as signed 8-bit samples. [EmbeddingEngine] produces MiniLM-class
 * floats that are usually within roughly [-1, 1]; [QUANT_SCALE] multiplies each float
 * before it is rounded into a byte, which keeps the useful range while cutting each
 * vector from [EmbeddingEngine.DIM] * 4 bytes (float32) to just [EmbeddingEngine.DIM]
 * bytes. Values outside what a byte can hold are clamped to +/-127; [dequantise] reverses
 * the scale so [nearest] compares like for like.
 */
class EmbeddingStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    companion object {
        private const val DATABASE_NAME = "trinity_embeddings.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE = "embedding"
        private const val COL_MSG_ID = "msg_id"
        private const val COL_BODY_HASH = "body_hash"
        private const val COL_VEC = "vec"

        /** Float -> int8 scale factor; see the class documentation. */
        private const val QUANT_SCALE = 100f
        private const val BYTE_MAX = 127
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE $TABLE (" +
                "$COL_MSG_ID INTEGER PRIMARY KEY, " +
                "$COL_BODY_HASH TEXT NOT NULL, " +
                "$COL_VEC BLOB NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Vectors come from a specific model; there is nothing useful to migrate.
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    /** How many messages are currently indexed. */
    fun count(): Int = runCatching {
        readableDatabase.query(TABLE, arrayOf("COUNT(*)"), null, null, null, null, null)
            .use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }.getOrDefault(0)

    /** Message id -> SHA-256 body hash, used to skip messages that did not change. */
    fun indexedHashes(): Map<Long, String> = runCatching {
        val out = HashMap<Long, String>()
        readableDatabase.query(TABLE, arrayOf(COL_MSG_ID, COL_BODY_HASH), null, null, null, null, null)
            .use { cursor ->
                val idAt = cursor.getColumnIndexOrThrow(COL_MSG_ID)
                val hashAt = cursor.getColumnIndexOrThrow(COL_BODY_HASH)
                while (cursor.moveToNext()) out[cursor.getLong(idAt)] = cursor.getString(hashAt)
            }
        out
    }.getOrDefault(emptyMap())

    /** Inserts or replaces one message vector. */
    fun upsert(id: Long, hash: String, vec: FloatArray) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(COL_MSG_ID, id)
                put(COL_BODY_HASH, hash)
                put(COL_VEC, quantise(vec))
            }
            writableDatabase.insertWithOnConflict(
                TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    /** Drops one message from the index, e.g. after its provider row was deleted. */
    fun delete(id: Long) {
        runCatching {
            writableDatabase.delete(TABLE, "$COL_MSG_ID = ?", arrayOf(id.toString()))
        }
    }

    /**
     * Cosine-ranked message ids for [vec], highest similarity first. Stored vectors are
     * dequantised with the same [QUANT_SCALE] used on write; the query vector is left in
     * float form (cosine is scale-invariant, so no query-side quantisation is needed).
     */
    fun nearest(vec: FloatArray, limit: Int = 10): List<Pair<Long, Float>> {
        if (vec.size != EmbeddingEngine.DIM || limit <= 0) return emptyList()
        return runCatching {
            val scored = ArrayList<Pair<Long, Float>>()
            readableDatabase.query(TABLE, arrayOf(COL_MSG_ID, COL_VEC), null, null, null, null, null)
                .use { cursor ->
                    val idAt = cursor.getColumnIndexOrThrow(COL_MSG_ID)
                    val vecAt = cursor.getColumnIndexOrThrow(COL_VEC)
                    while (cursor.moveToNext()) {
                        val blob = cursor.getBlob(vecAt) ?: continue
                        if (blob.size != EmbeddingEngine.DIM) continue
                        scored += cursor.getLong(idAt) to EmbeddingEngine.cosine(vec, dequantise(blob))
                    }
                }
            scored.sortByDescending { it.second }
            scored.take(limit)
        }.getOrDefault(emptyList())
    }

    /** Removes every vector. The caller decides whether to rebuild afterwards. */
    fun clear() {
        runCatching { writableDatabase.delete(TABLE, null, null) }
    }

    /** Float vector -> int8 blob, one byte per dimension. */
    private fun quantise(vec: FloatArray): ByteArray {
        val out = ByteArray(EmbeddingEngine.DIM)
        var i = 0
        while (i < EmbeddingEngine.DIM) {
            val value = if (i < vec.size) vec[i] else 0f
            out[i] = (value * QUANT_SCALE).toInt().coerceIn(-BYTE_MAX, BYTE_MAX).toByte()
            i++
        }
        return out
    }

    /** int8 blob -> float vector, undoing [QUANT_SCALE]. */
    private fun dequantise(blob: ByteArray): FloatArray {
        val out = FloatArray(blob.size)
        var i = 0
        while (i < blob.size) {
            out[i] = blob[i].toFloat() / QUANT_SCALE
            i++
        }
        return out
    }
}
