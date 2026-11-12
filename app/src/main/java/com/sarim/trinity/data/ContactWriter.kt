package com.sarim.trinity.data

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract

/** Writes to the phone's own contacts provider. No private store. */
object ContactWriter {

    /**
     * Creates a contact with every number given. Returns the new contact id, or
     * null when the provider refused the write.
     */
    fun save(context: Context, name: String, numbers: List<String>, email: String): Long? {
        val clean = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (name.isBlank() && clean.isEmpty()) return null
        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
        )
        if (name.isNotBlank()) {
            ops.add(nameInsert(name.trim()))
        }
        clean.forEach { number -> ops.add(phoneInsert(number)) }
        if (email.isNotBlank()) {
            ops.add(emailInsert(email.trim()))
        }
        return runCatching {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            results.firstOrNull()?.uri?.let { android.content.ContentUris.parseId(it) }
        }.getOrNull()
    }

    /**
     * Rewrites one contact's name, numbers and email. The provider models these as
     * separate data rows, so they are replaced wholesale rather than diffed.
     */
    fun update(
        context: Context,
        person: Person,
        name: String,
        numbers: List<String>,
        email: String,
    ): Boolean {
        val clean = numbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (name.isBlank() && clean.isEmpty()) return false
        val rawId = rawContactId(context, person)
        if (rawId <= 0L) return false

        val ops = ArrayList<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?,?,?)",
                    arrayOf(
                        rawId.toString(),
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    ),
                )
                .build(),
        )
        if (name.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        name.trim(),
                    )
                    .build(),
            )
        }
        clean.forEach { number ->
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
                    )
                    .build(),
            )
        }
        if (email.isNotBlank()) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.ADDRESS,
                        email.trim(),
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
                    )
                    .build(),
            )
        }
        return runCatching {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        }.getOrDefault(false)
    }

    /** Removes a contact and everything hanging off it. */
    fun delete(context: Context, contactId: Long): Boolean = runCatching {
        context.contentResolver.delete(
            ContactsContract.RawContacts.CONTENT_URI,
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
        ) > 0
    }.getOrDefault(false)

    fun rawContactId(context: Context, person: Person): Long {
        if (person.rawContactId > 0L) return person.rawContactId
        return runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(person.id.toString()),
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun nameInsert(name: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            )
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            .build()

    private fun phoneInsert(number: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            )
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
            .withValue(
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
            )
            .build()

    private fun emailInsert(email: String) =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            )
            .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
            .withValue(
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.TYPE_HOME,
            )
            .build()
}
