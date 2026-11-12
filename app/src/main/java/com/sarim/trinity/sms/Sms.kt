package com.sarim.trinity.sms

import android.app.Activity
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.sarim.trinity.MainActivity
import com.sarim.trinity.R
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.Scope
import com.sarim.trinity.data.BlockStore
import com.sarim.trinity.data.QuarantineStore
import com.sarim.trinity.data.SmsStore

object SmsSender {

    /**
     * Sends one text. [onResult] gets null on success, or a short reason the UI
     * can print verbatim — never a silent failure.
     */
    fun send(
        context: Context,
        address: String,
        body: String,
        subscriptionId: Int? = null,
        onResult: (String?) -> Unit = {},
    ) {
        if (address.isBlank() || body.isBlank()) {
            onResult("NOTHING TO SEND")
            return
        }
        val manager = manager(context, subscriptionId)
        if (manager == null) {
            onResult("NO TELEPHONY")
            return
        }
        val action = "com.sarim.trinity.SMS_SENT.${System.nanoTime()}"
        var finished = false
        lateinit var receiver: BroadcastReceiver
        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (finished) return
                finished = true
                runCatching { receiverContext.unregisterReceiver(this) }
                onResult(
                    when (resultCode) {
                        Activity.RESULT_OK -> null
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "NO SERVICE"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "RADIO OFF"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "MESSAGE REJECTED"
                        else -> "SEND FAILED"
                    },
                )
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val sentIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { manager.sendTextMessage(address, null, body, sentIntent, null) }
            .onFailure { error ->
                if (!finished) {
                    finished = true
                    runCatching { context.unregisterReceiver(receiver) }
                    onResult(error.message?.take(40)?.uppercase() ?: "SEND FAILED")
                }
            }
    }

    private fun manager(context: Context, subscriptionId: Int?): SmsManager? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val default = context.getSystemService(SmsManager::class.java)
            if (subscriptionId == null) default else default?.createForSubscriptionId(subscriptionId)
        } else {
            if (subscriptionId == null) {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        }
    }.getOrNull()
}

object ContactNames {

    /** Blocking phone lookup used at delivery time, where we cannot suspend. */
    fun lookup(context: Context, address: String): String? = runCatching {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}

/**
 * One notification per conversation, carrying the sender's name and, unless the
 * conversation is muted or the text was held, a one-line preview.
 */
object MessageNotifier {

    const val EXTRA_THREAD = "trinity.thread"
    const val EXTRA_ADDRESS = "trinity.address"
    const val EXTRA_REPLY = "trinity.reply"
    private const val CHANNEL_MESSAGES = "messages"
    private const val CHANNEL_HELD = "held"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(android.app.NotificationManager::class.java) ?: return
        runCatching {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "New texts that were not held back" },
            )
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_HELD,
                    "Held texts",
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "A text matched your rules and is waiting for review" },
            )
        }
    }

    private fun openThread(context: Context, threadId: Long, address: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun markRead(context: Context, threadId: Long): PendingIntent {
        val intent = Intent(context, MarkReadReceiver::class.java)
            .putExtra(EXTRA_THREAD, threadId)
        return PendingIntent.getBroadcast(
            context,
            threadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reply(context: Context, threadId: Long, address: String): PendingIntent {
        val intent = Intent(context, DirectReplyReceiver::class.java)
            .putExtra(EXTRA_THREAD, threadId)
            .putExtra(EXTRA_ADDRESS, address)
        return PendingIntent.getBroadcast(
            context,
            (threadId * 31).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    private fun replyAction(context: Context, threadId: Long, address: String): NotificationCompat.Action {
        val input = RemoteInput.Builder(EXTRA_REPLY).setLabel("Message").build()
        return NotificationCompat.Action.Builder(0, "Reply", reply(context, threadId, address))
            .addRemoteInput(input)
            .setAllowGeneratedReplies(true)
            .build()
    }

    fun show(
        context: Context,
        threadId: Long,
        address: String,
        name: String?,
        body: String,
        unread: Int,
        preview: Boolean = true,
    ) {
        ensureChannels(context)
        if (unread <= 0) return
        val sender = Person.Builder().setName(name ?: address).build()
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .setConversationTitle(name)
            .addMessage(
                NotificationCompat.MessagingStyle.Message(
                    if (preview) body else "New message",
                    System.currentTimeMillis(),
                    sender,
                ),
            )
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher)
            .setStyle(style)
            .setContentTitle(name ?: address)
            .setContentText(if (preview) body else "New message")
            .setNumber(unread)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openThread(context, threadId, address))
            .addAction(0, "Mark read", markRead(context, threadId))
            .addAction(replyAction(context, threadId, address))

        runCatching {
            NotificationManagerCompat.from(context).notify(threadId.toInt(), builder.build())
        }
    }

    /** A held text says only that something is waiting — never who or what. */
    fun showHeld(context: Context, count: Int) {
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_HELD)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$count text${if (count == 1) "" else "s"} held")
            .setContentText("Matched your rules. Tap to review.")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    9001,
                    Intent(context, MainActivity::class.java)
                        .putExtra("trinity.held", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        runCatching {
            NotificationManagerCompat.from(context).notify(9001, builder.build())
        }
    }

    fun cancel(context: Context, threadId: Long) {
        runCatching { NotificationManagerCompat.from(context).cancel(threadId.toInt()) }
    }
}

/** Clears a conversation's notification when the thread is opened or the action is used. */
class MarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (threadId < 0) return
        runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
            )
        }
        MessageNotifier.cancel(context, threadId)
    }
}

/** Reply straight from the notification, without opening Trinity. */
class DirectReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(MessageNotifier.EXTRA_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val address = intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
        val threadId = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (text.isBlank() || address.isBlank()) return
        SmsSender.send(context, address, text) { error ->
            if (error == null) {
                MessageNotifier.cancel(context, threadId)
            }
        }
    }
}

/**
 * Only the default SMS app receives SMS_DELIVER. That is what makes real blocking
 * possible: a message that hits a rule is filed in quarantine instead of the
 * system inbox, so it never reaches the conversation list at all.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val address = messages.first().originatingAddress ?: return
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        val timestamp = messages.first().timestampMillis
        val rules = BlockStore.get(context).rules.value
        val name = ContactNames.lookup(context, address)
        val hit = BlockMatcher.match(name, address, body, rules, Scope.Texts)

        if (hit != null) {
            QuarantineStore(context).add(
                QuarantineStore.Entry(
                    address = address,
                    body = body,
                    date = timestamp,
                    ruleKind = hit.kind.name,
                    ruleValue = hit.value,
                ),
            )
            Log.i("Trinity", "quarantined SMS from $address by ${hit.kind} '${hit.value}'")
            MessageNotifier.showHeld(context, QuarantineStore(context).all().size)
            return
        }

        val threadId = runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            uri?.let { android.content.ContentUris.parseId(it) }
        }.getOrNull()

        val realThread = threadId?.let { id ->
            runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms.THREAD_ID),
                    "${Telephony.Sms._ID} = ?",
                    arrayOf(id.toString()),
                    null,
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            }.getOrNull()
        } ?: SmsStore.threadIdFor(context, address) ?: -1L

        if (realThread >= 0) {
            val muted = context.getSharedPreferences("trinity.threads", Context.MODE_PRIVATE)
                .getString("muted", "").orEmpty()
                .split(',')
                .mapNotNull { it.trim().toLongOrNull() }
                .contains(realThread)
            if (!muted) {
                MessageNotifier.show(
                    context = context,
                    threadId = realThread,
                    address = address,
                    name = name,
                    body = body,
                    unread = 1,
                )
            }
        }
    }
}

/** Required to hold the default-SMS role; MMS bodies are left untouched for v1. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** True while Trinity holds the SMS role — blocking only bites when it does. */
fun holdsSmsRole(context: Context): Boolean {
    val roleManager = context.getSystemService(RoleManager::class.java)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
        roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    } else {
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}
