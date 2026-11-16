package com.sarim.trinity.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.sarim.trinity.mms.pdu.CharacterSets
import com.sarim.trinity.mms.pdu.EncodedStringValue
import com.sarim.trinity.mms.pdu.PduBody
import com.sarim.trinity.mms.pdu.PduComposer
import com.sarim.trinity.mms.pdu.PduPart
import com.sarim.trinity.mms.pdu.SendReq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Builds one M-Send.req with a single image (and an optional text caption), stores the
 * sent rows through [MmsStore], then hands the composed message to the platform
 * `SmsManager`. All PDU work is off the main thread; the platform does the networking.
 */
object MmsComposer {

    private const val MAX_IMAGE_BYTES = 1024 * 1024
    private const val TIMEOUT_MS = 45_000L

    fun send(
        context: Context,
        addresses: List<String>,
        imageUri: Uri,
        text: String? = null,
        subscriptionId: Int? = null,
        onResult: (String?) -> Unit,
    ) {
        val recipients = MmsAddresses.parse(addresses.joinToString(","))
        if (recipients.isEmpty()) {
            onResult("NO RECIPIENT")
            return
        }
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var handedOff = false
            val failure = runCatching<String?> {
                val bytes = readImage(app, imageUri) ?: return@runCatching "NO IMAGE"
                val mime = runCatching { app.contentResolver.getType(imageUri) }
                    .getOrNull()
                    ?.takeIf { it.startsWith("image/") && !it.contains("*") }
                    ?: "image/jpeg"

                val request = SendReq().apply {
                    date = System.currentTimeMillis() / 1000
                    setTo(recipients.map(::EncodedStringValue).toTypedArray())
                }
                val body = PduBody().apply {
                    addPart(
                        PduPart().apply {
                            setData(bytes)
                            setContentType(mime.toByteArray())
                            setName("image.jpg".toByteArray())
                            setContentId("image.jpg".toByteArray())
                            setContentDisposition("attachment".toByteArray())
                        },
                    )
                    if (!text.isNullOrBlank()) {
                        addPart(
                            PduPart().apply {
                                setData(text.toByteArray(Charsets.UTF_8))
                                setContentType("text/plain".toByteArray())
                                setCharset(CharacterSets.UTF_8)
                                setName("text.txt".toByteArray())
                                setContentId("text.txt".toByteArray())
                            },
                        )
                    }
                }
                request.setBody(body)
                // make() validates the PDU; the provider rows below carry the same parts.
                if (PduComposer(app, request).make() == null) return@runCatching "MESSAGE REJECTED"

                val storedParts = ArrayList<MmsStore.Part>(2)
                storedParts += MmsStore.Part(contentType = mime, bytes = bytes)
                if (!text.isNullOrBlank()) storedParts += MmsStore.Part(contentType = "text/plain", text = text)
                val messageId = MmsStore.insertSent(app, recipients, System.currentTimeMillis(), storedParts, subscriptionId)
                    ?: return@runCatching "SEND FAILED"
                val manager = manager(app, subscriptionId) ?: return@runCatching "NO TELEPHONY"
                handedOff = dispatch(app, manager, messageId, onResult)
                null
            }.getOrElse { error -> error.message?.take(40)?.uppercase() ?: "SEND FAILED" }
            if (!handedOff) onResult(failure ?: "SEND FAILED")
        }
    }

    private fun dispatch(
        context: Context,
        manager: SmsManager,
        messageId: Long,
        onResult: (String?) -> Unit,
    ): Boolean {
        val app = context.applicationContext
        val action = "com.sarim.trinity.MMS_SENT.${System.nanoTime()}"
        var finished = false
        lateinit var receiver: BroadcastReceiver
        val timeout = Handler(Looper.getMainLooper())
        fun finish(error: String?) {
            if (finished) return
            finished = true
            timeout.removeCallbacksAndMessages(null)
            runCatching { app.unregisterReceiver(receiver) }
            onResult(error)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                finish(
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
        val registered = runCatching {
            ContextCompat.registerReceiver(
                app,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        if (!registered) return false
        timeout.postDelayed({ finish("TIMEOUT") }, TIMEOUT_MS)
        val pending = PendingIntent.getBroadcast(
            app,
            0,
            Intent(action).setPackage(app.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            manager.sendMultimediaMessage(
                app,
                Uri.parse("content://mms/$messageId"),
                null,
                Bundle(),
                pending,
            )
        }.onFailure { error ->
            finish(error.message?.take(40)?.uppercase() ?: "SEND FAILED")
        }
        return true
    }

    /** Reads at most 1 MB from the picked image, one bounded stream, never cached. */
    private fun readImage(context: Context, uri: Uri): ByteArray? = runCatching<ByteArray?> {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
        input.use { stream ->
            var total = 0
            while (total < MAX_IMAGE_BYTES) {
                val count = stream.read(buffer, 0, minOf(buffer.size, MAX_IMAGE_BYTES - total))
                if (count < 0) break
                out.write(buffer, 0, count)
                total += count
            }
        }
        if (out.size() == 0) null else out.toByteArray()
    }.getOrNull()

    private fun manager(context: Context, subscriptionId: Int?): SmsManager? = runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
