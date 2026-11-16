package com.sarim.trinity.work

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.sarim.trinity.data.SCHEDULED_ID_KEY
import com.sarim.trinity.data.ScheduledStore
import com.sarim.trinity.data.ScheduleDispatcher
import com.sarim.trinity.data.SmsStore
import com.sarim.trinity.sms.SmsSender
import com.sarim.trinity.sms.holdsSmsRole
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Sends one queued text. WorkManager already runs this off the main thread. */
class SendScheduledWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext
        val id = inputData.getString(SCHEDULED_ID_KEY)
        if (id.isNullOrBlank()) return Result.failure()

        val store = ScheduledStore(app)
        val message = store.all().firstOrNull { it.id == id }
            ?: return Result.success() // Already sent or cancelled; nothing left to do.

        if (message.address.isBlank() || message.body.isBlank()) {
            store.remove(id)
            ScheduleDispatcher.cancel(app, id)
            return Result.success()
        }

        // SmsSender is callback-based (its broadcast arrives on the main thread), so
        // bridge it to doWork with a latch. A stuck broadcast must not pin a worker.
        val latch = CountDownLatch(1)
        var reason: String? = null
        runCatching {
            SmsSender.send(app, message.address, message.body, message.subscriptionId) { error ->
                reason = error
                latch.countDown()
            }
        }.onFailure { error ->
            if (reason == null) reason = error.message?.take(40)?.uppercase() ?: "SEND FAILED"
            latch.countDown()
        }
        val finished = runCatching { latch.await(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .getOrDefault(false)
        if (!finished) {
            // An unanswered broadcast is transient: keep the queue entry and retry.
            store.update(message.copy(lastError = "TIMEOUT"))
            return Result.retry()
        }

        val error = reason
        if (error == null) {
            if (holdsSmsRole(app)) {
                SmsStore.insertSent(
                    app,
                    message.address,
                    message.body,
                    System.currentTimeMillis(),
                    message.subscriptionId,
                )
            }
            store.remove(id)
            ScheduleDispatcher.cancel(app, id)
            return Result.success()
        }

        // MessageNotifier only exposes showHeld(context, count), which cannot carry a
        // reason; the failure stays on the entry as lastError for the pending card.
        store.update(message.copy(lastError = error))
        return if (error == NO_SERVICE && runAttemptCount >= MAX_ATTEMPTS - 1) {
            Result.failure()
        } else {
            Result.retry()
        }
    }

    private companion object {
        const val SEND_TIMEOUT_SECONDS = 30L
        const val MAX_ATTEMPTS = 2
        const val NO_SERVICE = "NO SERVICE"
    }
}
