package com.sarim.trinity.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * The system's "respond via message" quick reply (declining a call with a text),
 * with no screen of its own. Android registers this as a service rather than an
 * activity, and only hands out the SMS role to an app that owns one: this is the
 * app's eligibility ticket as much as it is a feature.
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.data?.schemeSpecificPart.orEmpty()
        val body = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (address.isNotBlank() && body.isNotBlank()) {
            SmsSender.send(this, address, body)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
