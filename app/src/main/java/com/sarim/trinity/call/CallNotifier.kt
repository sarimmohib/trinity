package com.sarim.trinity.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.sarim.trinity.R
import com.sarim.trinity.ui.formatNumber

/**
 * Telecom hands the ringtone to whichever app owns the call, so a dialer that only
 * draws a screen leaves an incoming call silent. This is that missing half: one
 * notification per call — ringing, then the live call with its counter and
 * controls — and a ringtone and vibration while it rings.
 */
object CallNotifier {

    private const val TAG = "TrinityCall"
    private const val CHANNEL = "calls"

    /** Also the id the service uses to hold itself in the foreground. */
    const val ID = 7100

    const val ACTION_ANSWER = "com.sarim.trinity.call.ANSWER"
    const val ACTION_DECLINE = "com.sarim.trinity.call.DECLINE"
    const val ACTION_HANGUP = "com.sarim.trinity.call.HANGUP"
    const val ACTION_MUTE = "com.sarim.trinity.call.MUTE"
    const val ACTION_SPEAKER = "com.sarim.trinity.call.SPEAKER"

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    /** Which face the posted notification currently has. */
    private var wasRinging = false

    /** Posts the call card and hands back what was posted, for the foreground call. */
    fun update(context: Context, call: LiveCall): Notification? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        channel(manager)

        val who = call.name ?: formatNumber(call.number).ifBlank { "Unknown number" }
        val person = Person.Builder().setName(who).setImportant(true).build()
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Ringing and answered are two different cards, and the shade keeps the old
        // layout when only the style changes — which left Answer/Decline sitting
        // there through the whole call. Drop the card first, then post the new face.
        if (call.ringing != wasRinging) {
            runCatching { manager.cancel(ID) }
            wasRinging = call.ringing
        }

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(true)

        if (call.ringing) {
            builder
                .setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(
                        person,
                        action(context, ACTION_DECLINE, 1),
                        action(context, ACTION_ANSWER, 2),
                    ),
                )
                .setShowWhen(false)
            // The full-screen intent is the fallback: even where a background
            // activity launch is refused, the call screen still lands on top.
            builder.setFullScreenIntent(open, true)
            ring(context)
        } else {
            builder
                // The counter: the clock runs from the moment the call connected,
                // not from when the card was drawn.
                .setUsesChronometer(true)
                .setWhen(if (call.connectTime > 0L) call.connectTime else System.currentTimeMillis())
                .setShowWhen(true)
                .addAction(0, if (call.muted) "Unmute" else "Mute", action(context, ACTION_MUTE, 4))
                .addAction(
                    0,
                    if (call.speaker) "Speaker off" else "Speaker",
                    action(context, ACTION_SPEAKER, 5),
                )
                // CallStyle draws the end-call button itself.
                .setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(
                        person,
                        action(context, ACTION_HANGUP, 3),
                    ),
                )
            quiet()
        }

        val posted = builder.build()
        runCatching { manager.notify(ID, posted) }
            .onFailure { Log.w(TAG, "call notification failed", it) }
        return posted
    }

    fun clear(context: Context) {
        quiet()
        wasRinging = false
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }

    /** Silent channel: the ringtone comes from [ring], not a second time from here. */
    private fun channel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun action(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            request,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ring(context: Context) {
        if (ringtone?.isPlaying == true) return
        val uri = runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
        }.getOrNull()
        if (uri != null) {
            ringtone = runCatching {
                RingtoneManager.getRingtone(context, uri).apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
            }.getOrNull()
        }
        val motor = vibrator ?: vibratorFor(context)?.also { vibrator = it }
        runCatching {
            val pattern = longArrayOf(0, 700, 900)
            motor?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun quiet() {
        runCatching { if (ringtone?.isPlaying == true) ringtone?.stop() }
        ringtone = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private fun vibratorFor(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        }.getOrNull()
    } else {
        @Suppress("DEPRECATION")
        runCatching { context.getSystemService(Vibrator::class.java) }.getOrNull()
    }
}

/** Answer, decline, mute, speaker or hang up from the call notification itself. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifier.ACTION_ANSWER -> TrinityInCallService.answer()
            CallNotifier.ACTION_DECLINE -> TrinityInCallService.decline()
            CallNotifier.ACTION_HANGUP -> TrinityInCallService.hangUp()
            CallNotifier.ACTION_MUTE -> TrinityInCallService.toggleMute()
            CallNotifier.ACTION_SPEAKER -> TrinityInCallService.toggleSpeaker()
        }
    }
}
