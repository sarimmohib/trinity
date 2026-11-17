package com.sarim.trinity.data

import android.database.Cursor
import android.provider.ContactsContract

/** A label from the phone's own address book. Trinity never writes these. */
data class ContactGroup(val id: Long, val name: String)

/**
 * Shared projection/parsing for the read-only groups query. Keeping the column order
 * here means [ContactsRepository.groups] and any other reader cannot drift apart.
 */
internal object ContactGroups {
    /** [ContactsContract.Groups._ID, ContactsContract.Groups.TITLE] — cheap and ordered. */
    val projection = arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE)

    val selection: String = "${ContactsContract.Groups.DELETED} = 0"

    val sortOrder: String = "${ContactsContract.Groups.TITLE} COLLATE LOCALIZED ASC"

    /** Reads the row the cursor is currently on. Null title becomes an empty label. */
    fun from(cursor: Cursor): ContactGroup = ContactGroup(
        id = cursor.getLong(0),
        name = cursor.getString(1).orEmpty(),
    )
}

/**
 * Pure duplicate detection — no provider, no coroutines, safe on the JVM test source set.
 * Co-located here because this is the new data file owned by the M6 data change.
 */
object ContactDuplicates {
    /** Keeps the merge sheet cheap even for a large address book. */
    private const val MAX_PAIRS = 100

    /**
     * Strong means the two people share at least one normalised number. Reuses
     * [BlockMatcher.sameNumber]'s digit rules (last 8 digits, formatting ignored),
     * which is pure and in this same package.
     */
    fun isStrong(a: Person, b: Person): Boolean {
        for (x in a.allNumbers) {
            for (y in b.allNumbers) {
                if (BlockMatcher.sameNumber(x.number, y.number)) return true
            }
        }
        return false
    }

    /**
     * Each unordered pair at most once, never a self-pair. A pair is included when it
     * is strong, or when the trimmed names match ignoring case and no number is shared
     * (weak). Blank names are not a basis for a weak match.
     */
    fun candidates(people: List<Person>): List<Pair<Person, Person>> {
        val out = ArrayList<Pair<Person, Person>>()
        for (i in people.indices) {
            val a = people[i]
            for (j in i + 1 until people.size) {
                if (out.size >= MAX_PAIRS) return out
                val b = people[j]
                val sameName = a.name.trim().isNotBlank() &&
                    a.name.trim().equals(b.name.trim(), ignoreCase = true)
                if (isStrong(a, b) || sameName) out += a to b
            }
        }
        return out
    }
}
