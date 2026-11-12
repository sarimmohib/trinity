package com.sarim.trinity.data

import android.content.ContentProviderOperation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import java.io.ByteArrayOutputStream

/**
 * The contact photo lives in the phone's contacts provider, next to the name and
 * numbers — nothing is copied into Trinity's own storage.
 */
object ContactPhotos {

    private const val MAX_PX = 512

    /** Decodes the stored photo small enough for a header or a list row. */
    fun load(context: Context, uri: String?, maxPx: Int = 160): Bitmap? {
        if (uri.isNullOrBlank()) return null
        val parsed = Uri.parse(uri)
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
            }
            context.contentResolver.openInputStream(parsed)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    /** Replaces the photo of [person] with the picked image, scaled down first. */
    fun set(context: Context, person: Person, source: Uri): Boolean = runCatching {
        val rawId = ContactWriter.rawContactId(context, person)
        if (rawId <= 0L) return false
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return false
        val square = square(decoded, MAX_PX)
        val bytes = ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
        val photo = ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
        val ops = arrayListOf(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawId.toString(), photo),
                )
                .build(),
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, photo)
                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                .build(),
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    }.getOrDefault(false)

    /** Removes the photo, leaving the contact itself alone. */
    fun remove(context: Context, person: Person): Boolean = runCatching {
        val rawId = ContactWriter.rawContactId(context, person)
        if (rawId <= 0L) return false
        context.contentResolver.delete(
            ContactsContract.Data.CONTENT_URI,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(
                rawId.toString(),
                ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
            ),
        ) > 0
    }.getOrDefault(false)

    private fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    /** Photos are round here, so the stored copy is cropped square to match. */
    private fun square(source: Bitmap, maxPx: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val left = (source.width - side) / 2
        val top = (source.height - side) / 2
        val cropped = Bitmap.createBitmap(source, left, top, side, side)
        if (side <= maxPx) return cropped
        val scaled = Bitmap.createScaledBitmap(cropped, maxPx, maxPx, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }
}
