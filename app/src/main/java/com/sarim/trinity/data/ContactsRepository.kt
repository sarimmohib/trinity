package com.sarim.trinity.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Everyone in the phone's own address book, with every number and email —
     * one entry per contact, not one per phone row.
     */
    suspend fun load(): List<Person> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val names = LinkedHashMap<Long, String>()
        val numbers = LinkedHashMap<Long, MutableList<PhoneEntry>>()
        val rawIds = HashMap<Long, Long>()
        val photos = HashMap<Long, String>()

        val phoneProjection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                phoneProjection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(0)
                    val rawId = cursor.getLong(1)
                    val name = cursor.getString(2)
                    val number = cursor.getString(3)
                    if (number.isNullOrBlank()) continue
                    val label = name ?: number
                    if (!names.containsKey(contactId)) names[contactId] = label
                    rawIds.putIfAbsent(contactId, rawId)
                    cursor.getString(5)?.let { photos.putIfAbsent(contactId, it) }
                    val entry = PhoneEntry(number.trim(), phoneLabel(cursor.getInt(4)))
                    val bucket = numbers.getOrPut(contactId) { ArrayList() }
                    if (bucket.none { digits(it.number) == digits(number) }) bucket += entry
                }
            }
        }

        val emails = emailMap()
        // Two bounded queries for the extra flags — never one per contact. The cheap
        // projection and ordering above stay untouched.
        val starredIds = starredContactIds()
        val memberships = groupMemberships()
        names.map { (id, name) ->
            val list = numbers[id].orEmpty()
            Person(
                id = id,
                name = name,
                number = list.firstOrNull()?.number.orEmpty(),
                rawContactId = rawIds[id] ?: -1L,
                numbers = list,
                emails = emails[id].orEmpty(),
                photoUri = photos[id],
                starred = id in starredIds,
                groupIds = memberships[id]?.toSet() ?: emptySet(),
            )
        }
    }

    /** One query for every starred contact id; bounded by the size of the address book. */
    private fun starredContactIds(): Set<Long> {
        val out = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.STARRED} = 1",
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (idColumn >= 0) {
                    while (cursor.moveToNext()) out += cursor.getLong(idColumn)
                }
            }
        }
        return out
    }

    /** One query for every (contact, group) membership row — no per-contact queries. */
    private fun groupMemberships(): Map<Long, MutableSet<Long>> {
        val out = HashMap<Long, MutableSet<Long>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
                null,
            )?.use { cursor ->
                val contactColumn = cursor.getColumnIndex(
                    ContactsContract.Data.CONTACT_ID,
                )
                val groupColumn = cursor.getColumnIndex(
                    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID,
                )
                if (contactColumn >= 0 && groupColumn >= 0) {
                    while (cursor.moveToNext()) {
                        val contactId = cursor.getLong(contactColumn)
                        val groupId = cursor.getLong(groupColumn)
                        out.getOrPut(contactId) { LinkedHashSet() }.add(groupId)
                    }
                }
            }
        }
        return out
    }

    /** Every non-deleted group in the address book, sorted by title. Read-only. */
    suspend fun groups(): List<ContactGroup> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                ContactGroups.projection,
                ContactGroups.selection,
                null,
                ContactGroups.sortOrder,
            )?.use { cursor ->
                val out = ArrayList<ContactGroup>()
                while (cursor.moveToNext()) out += ContactGroups.from(cursor)
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun emailMap(): Map<Long, List<String>> {
        val out = HashMap<Long, MutableList<String>>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val address = cursor.getString(1) ?: continue
                    out.getOrPut(id) { ArrayList() }.add(address.trim())
                }
            }
        }
        return out
    }

    private fun phoneLabel(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "MOBILE"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "HOME"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "WORK"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME,
        -> "FAX"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "MAIN"
        else -> "OTHER"
    }

    private fun digits(value: String): String = value.filter { it.isDigit() }

    /** display name for a raw address, used to name SMS threads and calls. */
    suspend fun nameFor(address: String?): String? {
        if (address.isNullOrBlank() || !hasPermission()) return null
        return withContext(Dispatchers.IO) {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(address),
            )
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()
        }
    }
}
