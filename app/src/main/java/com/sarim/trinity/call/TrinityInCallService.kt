package com.sarim.trinity.call

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sarim.trinity.sms.ContactNames

/** One call, flattened to what a screen needs to draw it. */
data class LiveCall(
    val number: String,
    val name: String?,
    val state: Int,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    /** When Telecom says the call was answered; 0 until it is. */
    val connectTime: Long = 0L,
) {
    val ringing: Boolean get() = state == Call.STATE_RINGING
    val active: Boolean get() = state == Call.STATE_ACTIVE
    val holding: Boolean get() = state == Call.STATE_HOLDING
    val ending: Boolean
        get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
}

/**
 * Which call is up, shared between the telecom service and the screen. The service
 * writes, the screen only reads.
 */
object CallUi {
    var live by mutableStateOf<LiveCall?>(null)
}

/**
 * The in-call screen owner. Android will not hand out the phone role to an app
 * without one: the default dialer is what draws every call, including calls the
 * user starts from elsewhere, so this service is the requirement rather than a
 * feature. Calls the app places itself still show Trinity's own surface.
 */
class TrinityInCallService : InCallService() {

    private var call: Call? = null

    private val watcher = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()

        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    override fun onCreate() {
        super.onCreate()
        running = this
    }

    override fun onDestroy() {
        if (running === this) running = null
        detach()
        CallUi.live = null
        leaveForeground()
        CallNotifier.clear(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        detach()
        this.call = call
        call.registerCallback(watcher)
        publish()
        showScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (call === this.call) {
            detach()
            this.call = null
            CallUi.live = null
            leaveForeground()
            CallNotifier.clear(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) = publish()

    private fun detach() {
        val current = call ?: return
        runCatching { current.unregisterCallback(watcher) }
        call = null
    }

    private fun publish() {
        val current = call ?: return
        val details = current.details
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val audio = callAudioState
        // Details lag behind the call itself: reading the state off the call is what
        // lets the live notification switch off its ringing card the moment the
        // call is answered.
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            current.state
        } else {
            details.state
        }
        val live = LiveCall(
            number = number,
            name = details.callerDisplayName?.toString()
                ?: runCatching { ContactNames.lookup(this, number) }.getOrNull(),
            state = state,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            connectTime = if (state == Call.STATE_ACTIVE) details.connectTimeMillis else 0L,
        )
        CallUi.live = live
        // Telecom does not ring for the dialer: this both rings and puts the call
        // in the shade, so an incoming call can never arrive silently again.
        val posted = CallNotifier.update(this, live)
        // A live call has to outlive a background kill. Without this the process
        // could go away mid-call, and the shade was left holding a frozen
        // Answer/Decline card with nothing left to update it.
        if (posted != null) holdForeground(posted)
    }

    private fun holdForeground(notification: android.app.Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    CallNotifier.ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
                )
            } else {
                startForeground(CallNotifier.ID, notification)
            }
        }.onFailure { android.util.Log.w("TrinityCall", "foreground call failed", it) }
    }

    private fun leaveForeground() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    /**
     * The call screen is what the user must see, so it is opened straight away.
     * It carries showWhenLocked/turnScreenOn, which is what surfaces it over the
     * lock screen during an incoming call.
     */
    private fun showScreen() {
        val intent = Intent(this, InCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
    }

    companion object {
        private var running: TrinityInCallService? = null

        fun answer() {
            running?.call?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun decline() {
            running?.call?.reject(false, null)
        }

        fun hangUp() {
            running?.call?.disconnect()
        }

        fun mute(on: Boolean) {
            runCatching { running?.setMuted(on) }
        }

        /** Notification buttons toggle, so they read the state the call is in. */
        fun toggleMute() = mute(CallUi.live?.muted != true)

        fun toggleSpeaker() = speaker(CallUi.live?.speaker != true)

        fun speaker(on: Boolean) {
            val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
            runCatching { running?.setAudioRoute(route) }
        }

        /** Pressing a key sends a tone; it is stopped on its own so a tap is enough. */
        fun tone(key: Char) {
            val target = running?.call ?: return
            runCatching { target.playDtmfTone(key) }
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { target.stopDtmfTone() } },
                120,
            )
        }
    }
}
