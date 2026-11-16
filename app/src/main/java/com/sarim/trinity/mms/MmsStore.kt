package com.sarim.trinity.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import java.io.File

/**
 * Values of the `Telephony.Mms.MESSAGE_TYPE` column. They are hidden in the public SDK,
 * so they are spelled out here with their AOSP values rather than read from a constant.
 */
const val MMS_TYPE_INCOMING = 132
const val MMS_TYPE_OUTGOING = 128

/**
 * Owns every write to the system MMS provider. Rows go to `content://mms`, parts to
 * `content://mms/<id>/part` (with a fallback to the conventional `content://mms/part`
 * + `mid` form for providers that only accept that one). Image bytes are written to a
 * per-message file under `cacheDir` — they are never held in a field or cached in
 * memory, and nothing is decoded here.
 *
 * There is no MMS provider on the build machine, so all provider behaviour below is
 * device-dependent and UNVERIFIED until exercised on a handset.
 */
object MmsStore {

    data class Part(
        val contentType: String,
        val bytes: ByteArray? = null,
        val text: String? = null,
    )

    private const val MMS_PART = "content://mms/part"
    private const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"

    fun insertReceived(
        context: Context,
        address: String,
        date: Long,
        subject: String?,
        body: String?,
        parts: List<Part>,
        subscriptionId: Int?,
    ): Long? = runCatching {
        insert(context, setOf(address), date, subject, body, parts, subscriptionId, incoming = true)
    }.getOrNull()

    fun insertSent(
        context: Context,
        addresses: List<String>,
        date: Long,
        parts: List<Part>,
        subscriptionId: Int?,
    ): Long? = runCatching {
        insert(context, addresses.toSet(), date, null, null, parts, subscriptionId, incoming = false)
    }.getOrNull()

    /**
     * Content URI of the first image part of a stored message, or null. Only the
     * part's DATA/TEXT columns are inspected to confirm the part has content; the
     * bytes themselves are never copied into memory here.
     */
    fun firstImageUri(context: Context, messageId: Long): String? = runCatching {
        val projection = arrayOf(
            Telephony.Mms.Part._ID,
            Telephony.Mms.Part.CONTENT_TYPE,
            Telephony.Mms.Part._DATA,
            Telephony.Mms.Part.TEXT,
        )
        val selection = "${Telephony.Mms.Part.MSG_ID} = ?"
        val args = arrayOf(messageId.toString())
        findImage(context, MMS_PART, projection, selection, args, "content://mms/$messageId/part")
    }.getOrNull()

    private fun findImage(
        context: Context,
        uri: String,
        projection: Array<String>,
        selection: String,
        args: Array<String>,
        fallbackUri: String,
    ): String? {
        var found = queryImage(context, Uri.parse(uri), projection, selection, args)
        if (found == null) {
            found = queryImage(context, Uri.parse(fallbackUri), projection, selection, args)
        }
        return found
    }

    private fun queryImage(
        context: Context,
        uri: Uri,
        projection: Array<String>,
        selection: String,
        args: Array<String>,
    ): String? = runCatching {
        context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
            val idCol = cursor.getColumnIndex(Telephony.Mms.Part._ID)
            val typeCol = cursor.getColumnIndex(Telephony.Mms.Part.CONTENT_TYPE)
            val dataCol = cursor.getColumnIndex(Telephony.Mms.Part._DATA)
            val textCol = cursor.getColumnIndex(Telephony.Mms.Part.TEXT)
            var result: String? = null
            while (result == null && cursor.moveToNext()) {
                if (idCol < 0 || typeCol < 0) break
                val type = cursor.getString(typeCol).orEmpty()
                if (!type.startsWith("image/")) continue
                val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                val text = if (textCol >= 0) cursor.getString(textCol) else null
                if (data.isNullOrBlank() && text.isNullOrBlank()) continue
                result = "$MMS_PART/${cursor.getLong(idCol)}"
            }
            result
        }
    }.getOrNull()

    private fun insert(
        context: Context,
        addresses: Set<String>,
        date: Long,
        subject: String?,
        body: String?,
        parts: List<Part>,
        subscriptionId: Int?,
        incoming: Boolean,
    ): Long? {
        val threadId = if (addresses.size == 1) {
            Telephony.Threads.getOrCreateThreadId(context, addresses.first())
        } else {
            Telephony.Threads.getOrCreateThreadId(context, addresses)
        }
        val size = parts.sumOf { it.bytes?.size ?: it.text?.toByteArray(Charsets.UTF_8)?.size ?: 0 }
        val values = ContentValues().apply {
            put(Telephony.Mms.DATE, date)
            put(Telephony.Mms.DATE_SENT, date)
            put(
                Telephony.Mms.MESSAGE_TYPE,
                if (incoming) MMS_TYPE_INCOMING else MMS_TYPE_OUTGOING,
            )
            put(Telephony.Mms.READ, if (incoming) 0 else 1)
            put(Telephony.Mms.SEEN, if (incoming) 0 else 1)
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.CONTENT_TYPE, MULTIPART_RELATED)
            put(Telephony.Mms.MESSAGE_SIZE, size)
            body?.let { put("text", it) }
            subject?.let { put(Telephony.Mms.SUBJECT, it) }
            subscriptionId?.let { put(Telephony.Mms.SUBSCRIPTION_ID, it) }
        }
        val row = context.contentResolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return null
        val messageId = ContentUris.parseId(row)
        parts.forEachIndexed { index, part -> insertPart(context, messageId, index, part) }
        return messageId
    }

    private fun insertPart(context: Context, messageId: Long, index: Int, part: Part) {
        val name = "part$index"
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.MSG_ID, messageId)
            put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
            put(Telephony.Mms.Part.NAME, name)
            put(Telephony.Mms.Part.FILENAME, name)
            put(Telephony.Mms.Part.CONTENT_ID, "<$name>")
            put(Telephony.Mms.Part.CONTENT_LOCATION, name)
            if (part.bytes != null) {
                val file = partFile(context, messageId, index)
                file.writeBytes(part.bytes)
                put(Telephony.Mms.Part._DATA, file.absolutePath)
            } else if (part.text != null) {
                put(Telephony.Mms.Part.TEXT, part.text)
                put(Telephony.Mms.Part.CHARSET, "utf-8")
            }
        }
        val primary = Uri.parse("content://mms/$messageId/part")
        val first = runCatching { context.contentResolver.insert(primary, values) }.getOrNull()
        if (first == null) {
            runCatching { context.contentResolver.insert(Uri.parse(MMS_PART), values) }
        }
    }

    private fun partFile(context: Context, messageId: Long, index: Int): File {
        val dir = File(context.cacheDir, "mms")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "$messageId-$index")
    }
}
