package com.sarim.trinity.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.BlockStore
import com.sarim.trinity.data.QuarantineStore
import com.sarim.trinity.data.Scope
import com.sarim.trinity.mms.pdu.NotificationInd
import com.sarim.trinity.mms.pdu.PduParser
import com.sarim.trinity.sms.ContactNames
import com.sarim.trinity.sms.MessageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receives the WAP push that announces an incoming MMS while Trinity holds the SMS
 * role. Parsing, rule matching, provider work and the download are all off the main
 * thread. Only a short notification preview is ever shown — never image bytes.
 *
 * Device-dependent and UNVERIFIED here: there is no telephony provider or MMSC on the
 * build machine, including the exact WAP-push extra key and the carrier APN behaviour.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION || intent.type != MIME) return
        val raw = intent.getByteArrayExtra(EXTRA_PDU) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        val done = AtomicBoolean(false)
        fun finish() {
            if (done.compareAndSet(false, true)) pending.finish()
        }
        // The platform may never deliver the download result; never hold the broadcast
        // open forever. Everything below is idempotent, so an early finish is harmless.
        Handler(Looper.getMainLooper()).postDelayed(Runnable { finish() }, 60_000L)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                handle(app, intent, raw, ::finish)
            } catch (error: Throwable) {
                finish()
            }
        }
    }

    private fun handle(context: Context, intent: Intent, raw: ByteArray, finish: () -> Unit) {
        val pdu = runCatching { PduParser(raw, false).parse() }.getOrNull()
        if (pdu !is NotificationInd) {
            finish()
            return
        }
        val address = pdu.getFrom()?.getString()?.trim().orEmpty()
        val subject = pdu.getSubject()?.getString()?.trim()?.takeIf { it.isNotEmpty() }
        // A NotificationInd carries no Message-Id getter in the vendored classes;
        // its Transaction-Id is the stable identifier a retransmitted push repeats.
        val messageId = pdu.getTransactionId()?.let(::String)?.trim()?.takeIf { it.isNotEmpty() }
        val location = pdu.getContentLocation()?.let(::String)?.trim()?.takeIf { it.isNotEmpty() }
        if (location == null) {
            finish()
            return
        }
        val date = System.currentTimeMillis()
        val name = ContactNames.lookup(context, address)
        // Check dedupe before rules so a retransmitted push cannot duplicate Held entries.
        if (MmsDedupe.isDuplicate(context, messageId)) {
            finish()
            return
        }
        val rules = BlockStore.get(context).rules.value
        val hit = BlockMatcher.match(name, address, subject, rules, Scope.Texts)

        if (hit != null) {
            MmsDedupe.remember(context, messageId)
            QuarantineStore(context).add(
                QuarantineStore.Entry(
                    address = address,
                    body = subject.orEmpty(),
                    date = date,
                    ruleKind = hit.kind.name,
                    ruleValue = hit.value,
                    kind = "MMS",
                ),
            )
            MessageNotifier.showHeld(context, QuarantineStore(context).all().size)
            finish()
            return
        }

        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1)
            .takeIf { it >= 0 }
            ?: intent.getIntExtra(EXTRA_SUBSCRIPTION_MODERN, -1).takeIf { it >= 0 }
        MmsTransaction.download(context, location, subscriptionId) { id, error ->
            try {
                if (id != null && error == null) {
                    // Only a completed download earns a dedupe entry; a failed one must
                    // stay retryable when the carrier resends the notification.
                    MmsDedupe.remember(context, messageId)
                    notify(context, id, address, name)
                } else {
                    MmsDedupe.forget(context, messageId)
                }
            } finally {
                finish()
            }
        }
    }

    private fun notify(context: Context, messageId: Long, address: String, name: String?) {
        val threadId = runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms.THREAD_ID),
                "${Telephony.Mms._ID} = ?",
                arrayOf(messageId.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                    if (col >= 0) cursor.getLong(col) else null
                } else {
                    null
                }
            }
        }.getOrNull() ?: runCatching {
            Telephony.Threads.getOrCreateThreadId(context, address)
        }.getOrNull() ?: return
        MessageNotifier.show(
            context = context,
            threadId = threadId,
            address = address,
            name = name,
            body = "Picture message",
            unread = 1,
            preview = true,
        )
    }

    private companion object {
        const val ACTION = "android.provider.Telephony.WAP_PUSH_DELIVER"
        const val MIME = "application/vnd.wap.mms-message"
        const val EXTRA_PDU = "data"
        const val EXTRA_SUBSCRIPTION = "subscription"
        const val EXTRA_SUBSCRIPTION_MODERN = "android.telephony.extra.SUBSCRIPTION_INDEX"
    }
}
