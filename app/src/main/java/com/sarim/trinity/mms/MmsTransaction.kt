package com.sarim.trinity.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Downloads an MMS using the platform's public `SmsManager` API. The system writes the
 * retrieved PDU and its parts into `content://mms`; we only register a one-shot result
 * receiver and report the new row id (or a short failure reason, in the same style as
 * `SmsSender`). No PDU parsing and no network code of our own run here.
 *
 * A carrier can suppress the result PendingIntent entirely, so the callback is bounded:
 * exactly one result is delivered, then the receiver is unregistered. Device behaviour
 * remains UNVERIFIED without a telephony provider.
 */
object MmsTransaction {

    private const val TIMEOUT_MS = 45_000L

    fun download(
        context: Context,
        locationUrl: String,
        subscriptionId: Int?,
        onResult: (Long?, String?) -> Unit,
    ) {
        val app = context.applicationContext
        val manager = manager(app, subscriptionId)
        if (manager == null) {
            onResult(null, "NO TELEPHONY")
            return
        }
        if (locationUrl.isBlank()) {
            onResult(null, "DOWNLOAD FAILED")
            return
        }
        val action = "com.sarim.trinity.MMS_DOWNLOADED.${System.nanoTime()}"
        var finished = false
        lateinit var receiver: BroadcastReceiver
        val timeout = Handler(Looper.getMainLooper())
        fun finish(id: Long?, error: String?) {
            if (finished) return
            finished = true
            timeout.removeCallbacksAndMessages(null)
            runCatching { app.unregisterReceiver(receiver) }
            onResult(id, error)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val http = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, -1)
                if (resultCode != Activity.RESULT_OK || (http != -1 && http != 200)) {
                    finish(null, "DOWNLOAD FAILED")
                    return
                }
                val id = messageId(app, intent)
                finish(id, if (id == null) "DOWNLOAD FAILED" else null)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        if (!registered) {
            onResult(null, "DOWNLOAD FAILED")
            return
        }
        timeout.postDelayed({ finish(null, "TIMEOUT") }, TIMEOUT_MS)
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            Intent(action).setPackage(app.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            manager.downloadMultimediaMessage(
                app,
                locationUrl,
                Telephony.Mms.CONTENT_URI,
                Bundle(),
                pending,
            )
        }.onFailure { error ->
            finish(null, error.message?.take(40)?.uppercase() ?: "DOWNLOAD FAILED")
        }
    }

    /** The downloaded intent may carry the new content URI; otherwise take the newest row. */
    private fun messageId(context: Context, intent: Intent): Long? {
        intent.data?.let { uri ->
            if (uri.scheme == "content" && uri.path?.contains("mms") == true) {
                uri.lastPathSegment?.toLongOrNull()?.let { return it }
            }
        }
        return runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                null,
                null,
                "${Telephony.Mms.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(Telephony.Mms._ID)
                    if (col >= 0) cursor.getLong(col) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun manager(context: Context, subscriptionId: Int?): SmsManager? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val default = context.getSystemService(SmsManager::class.java)
            if (subscriptionId == null) default else default?.createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            if (subscriptionId == null) {
                SmsManager.getDefault()
            } else {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        }
    }.getOrNull()
}
