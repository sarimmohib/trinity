package com.sarim.trinity.data

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How a person can be reached beside a plain call or SMS, as the phone knows it. */
data class Channels(
    val whatsappInstalled: Boolean = false,
    /**
     * The identity WhatsApp itself synced for this contact, e.g.
     * "923425898663@s.whatsapp.net". Present only when WhatsApp has this person.
     */
    val whatsappJid: String? = null,
    val googleAccounts: Int = 0,
    val googleSyncOn: Boolean = false,
    val autoSync: Boolean = false,
) {
    val onWhatsApp: Boolean get() = whatsappJid != null
}

/**
 * WhatsApp publishes its contacts through the phone's contacts provider as raw
 * contacts under its own account, with the JID in SYNC1. Reading that is how we
 * can say whether a number is actually on WhatsApp, instead of guessing from an
 * installed app.
 */
object ContactChannels {

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_ACCOUNT = "com.whatsapp"
    private const val GOOGLE_ACCOUNT = "com.google"

    suspend fun read(context: Context, person: Person): Channels = withContext(Dispatchers.IO) {
        runCatching {
            val accounts = accounts(context)
            val google = accounts.filter { it.type == GOOGLE_ACCOUNT }
            Channels(
                whatsappInstalled = installed(context),
                whatsappJid = whatsappJid(context, person),
                googleAccounts = google.size,
                googleSyncOn = google.any { syncing(context, it) },
                // Reading the master switch needs READ_SYNC_SETTINGS; never let a
                // locked-down ROM take the screen down with it.
                autoSync = runCatching {
                    ContentResolver.getMasterSyncAutomatically()
                }.getOrDefault(false),
            )
        }.getOrDefault(Channels())
    }

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
    }.isSuccess

    /** "923425898663" for "0342 5898663"-style input WhatsApp will accept. */
    fun digits(number: String): String? {
        var digits = number.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.removePrefix("00")
        return digits.takeIf { it.length in 7..15 }
    }

    fun chatIntent(number: String): Intent? = digits(number)?.let {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$it"))
    }

    fun callIntent(number: String): Intent? = digits(number)?.let {
        Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://call?number=$it"))
    }

    private fun whatsappJid(context: Context, person: Person): String? {
        val jids = LinkedHashSet<String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.SYNC1),
                "${ContactsContract.RawContacts.CONTACT_ID} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
                arrayOf(person.id.toString(), WHATSAPP_ACCOUNT),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.takeIf { it.contains('@') }?.let { jids += it }
                }
            }
        }
        if (jids.isEmpty()) return null
        val known = person.allNumbers.mapNotNull { digits(it.number) }.toSet()
        // The contact's own number first; otherwise the one identity WhatsApp
        // carries for this contact is theirs by definition.
        return jids.firstOrNull { jid -> jid.substringBefore('@').filter { it.isDigit() } in known }
            ?: jids.singleOrNull()
    }

    /** Every account that stores contacts on this phone, from the provider itself. */
    private fun accounts(context: Context): Set<Account> {
        val found = LinkedHashSet<Account>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    found += Account(name, type)
                }
            }
        }
        return found
    }

    private fun syncing(context: Context, account: Account): Boolean = runCatching {
        ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) > 0 &&
            ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)
    }.getOrDefault(false)
}
