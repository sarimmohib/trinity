package com.sarim.trinity.call

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
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
    /** True when the other side placed the call. */
    val incoming: Boolean = false,
    /** Telecom's reason once the call is over (a [DisconnectCause] code); -1 until then. */
    val endCode: Int = -1,
    /** Seconds the call was actually connected for; 0 if it never was. */
    val talkSeconds: Int = 0,
) {
    val ringing: Boolean get() = state == Call.STATE_RINGING
    val active: Boolean get() = state == Call.STATE_ACTIVE
    val holding: Boolean get() = state == Call.STATE_HOLDING
    val ending: Boolean
        get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING

    /** Over, as opposed to [ending], which also covers the moment of hanging up. */
    val ended: Boolean get() = state == Call.STATE_DISCONNECTED

    /** How the call finished, in words for the screen. Only meaningful once [ended]. */
    val outcome: String
        get() = when (endCode) {
            DisconnectCause.MISSED -> "MISSED CALL"
            DisconnectCause.REJECTED -> "DECLINED"
            DisconnectCause.BUSY -> "LINE BUSY"
            DisconnectCause.CANCELED -> "CANCELLED"
            DisconnectCause.ERROR -> "CALL FAILED"
            DisconnectCause.RESTRICTED -> "CALL NOT ALLOWED"
            else -> when {
                talkSeconds > 0 -> "CALL ENDED"
                // Never connected: what it means depends on which side was waiting.
                incoming && endCode == DisconnectCause.LOCAL -> "DECLINED"
                incoming -> "MISSED CALL"
                endCode == DisconnectCause.LOCAL -> "CANCELLED"
                else -> "NOT ANSWERED"
            }
        }

    /** Who hung up, said only for a call that was actually talked on. */
    val endedBy: String?
        get() = when {
            talkSeconds <= 0 -> null
            endCode == DisconnectCause.LOCAL -> "YOU HUNG UP"
            endCode == DisconnectCause.REMOTE -> "THEY HUNG UP"
            else -> null
        }
}

/**
 * Which call is up, shared between the telecom service and the screen. The service
 * writes, the screen only reads.
 */
object CallUi {
    var live by mutableStateOf<LiveCall?>(null)

    /**
     * The call that has just finished. Telecom unbinds the service the moment the last
     * call is removed, taking [live] with it, so how the call ended has to be kept
     * somewhere that outlives the service for the screen to say it.
     */
    var last by mutableStateOf<LiveCall?>(null)
}

/**
 * The in-call screen owner. Android will not hand out the phone role to an app
 * without one: the default dialer is what draws every call, including calls the
 * user starts from elsewhere, so this service is the requirement rather than a
 * feature. Calls the app places itself still show Trinity's own surface.
 */
class TrinityInCallService : InCallService() {

    private var call: Call? = null
    private var incoming = false

    // What this call is known as. Telecom rewrites the call's details as it connects
    // (the number's form, the carrier's name field), so what was found while it rang
    // is kept for the rest of the call instead of being worked out again each time.
    private var knownNumber = ""
    private var lookedUpNumber: String? = null
    private var contactName: String? = null

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
        knownNumber = ""
        lookedUpNumber = null
        contactName = null
        incoming = stateOf(call) == Call.STATE_RINGING
        // A new call replaces whatever the screen was still saying about the last one.
        CallUi.last = null
        this.call = call
        call.registerCallback(watcher)
        publish()
        showScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (call === this.call) {
            val finished = CallUi.live?.let { live ->
                if (live.ended) {
                    live
                } else {
                    // Removed without a disconnected state having been seen.
                    live.copy(
                        state = Call.STATE_DISCONNECTED,
                        endCode = call.details.disconnectCause?.code ?: DisconnectCause.UNKNOWN,
                        talkSeconds = talkSeconds(call.details),
                    )
                }
            }
            detach()
            this.call = null
            CallUi.last = finished
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
        // The handle can come back empty or reformatted once the call connects; the
        // last real number stands in for it.
        val number = details.handle?.schemeSpecificPart.orEmpty()
            .ifBlank { knownNumber }
            .also { knownNumber = it }
        val audio = callAudioState
        val state = stateOf(current)
        val over = state == Call.STATE_DISCONNECTED
        val live = LiveCall(
            number = number,
            name = resolveName(number, details),
            state = state,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            connectTime = if (state == Call.STATE_ACTIVE) details.connectTimeMillis else 0L,
            incoming = incoming,
            endCode = if (over) details.disconnectCause?.code ?: DisconnectCause.UNKNOWN else -1,
            talkSeconds = if (over) talkSeconds(details) else 0,
        )
        CallUi.live = live
        // A finished call has nothing left to ring or count: no live card for it.
        if (over) {
            leaveForeground()
            CallNotifier.clear(this)
            return
        }
        // Telecom does not ring for the dialer: this both rings and puts the call
        // in the shade, so an incoming call can never arrive silently again.
        val posted = CallNotifier.update(this, live)
        // A live call has to outlive a background kill. Without this the process
        // could go away mid-call, and the shade was left holding a frozen
        // Answer/Decline card with nothing left to update it.
        if (posted != null) holdForeground(posted)
    }

    /**
     * Details lag behind the call itself: reading the state off the call is what
     * lets the live notification switch off its ringing card the moment the
     * call is answered.
     */
    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) call.state else call.details.state

    /** Seconds the call was connected for, from Telecom's own connect time. */
    private fun talkSeconds(details: Call.Details): Int {
        val connected = details.connectTimeMillis
        if (connected <= 0L) return 0
        return ((System.currentTimeMillis() - connected) / 1000L).toInt().coerceAtLeast(0)
    }

    /**
     * Who the call is with. The address book comes first: it is the person's own
     * word for the number. The carrier's name field is only a fallback, and only when
     * it actually says something — Telecom commonly reports it as an empty string
     * (not null), which used to win over the contact and blank the name on incoming
     * calls and, once an outgoing call was picked up, on those too.
     */
    private fun resolveName(number: String, details: Call.Details): String? {
        if (number.isNotBlank() && number != lookedUpNumber) {
            lookedUpNumber = number
            contactName = runCatching { ContactNames.lookup(this, number) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
        contactName?.let { return it }

        if (details.callerDisplayNamePresentation != TelecomManager.PRESENTATION_ALLOWED) return null
        val carrierName = details.callerDisplayName?.toString()?.trim().orEmpty()
        // A carrier that sends the number back as the "name" adds nothing.
        return carrierName.takeIf { name -> name.any { it.isLetter() } }
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
