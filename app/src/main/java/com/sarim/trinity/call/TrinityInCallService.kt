package com.sarim.trinity.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import com.sarim.trinity.sms.ContactNames

/** One call, flattened to what a screen needs to draw it. */
data class LiveCall(
    /** Stable for the life of this call; used to target one call in a conference. */
    val id: Long,
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
 * Which calls are up, shared between the telecom service and the screen. The service
 * writes, the screens only read. A second incoming call (call waiting) and calls added
 * from the dial pad land in the same list, so every call stays addressable.
 */
object CallUi {
    /** Every call Telecom has handed us, in arrival order. */
    var calls by mutableStateOf<List<LiveCall>>(emptyList())

    /** The call the main card and the notification follow: ringing first, else active/held. */
    var primaryId by mutableStateOf<Long?>(null)

    /** Compatibility for single-call readers: the primary, or the first call. */
    val live: LiveCall?
        get() = calls.firstOrNull { it.id == primaryId } ?: calls.firstOrNull()

    /**
     * The call that has just finished. Telecom unbinds the service the moment the last
     * call is removed, taking [calls] with it, so how the call ended has to be kept
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

    /**
     * One entry per live call. The id is assigned once when Telecom adds the call and
     * never changes, so a screen action can always target exactly one call even after
     * Telecom rewrites the details as the call connects.
     */
    private class Tracked(
        val call: Call,
        val id: Long,
        var knownNumber: String = "",
        var lookedUpNumber: String? = null,
        var contactName: String? = null,
        var incoming: Boolean = false,
    )

    // LinkedHashMap keeps arrival order, which is the order the screen shows.
    private val calls = LinkedHashMap<Long, Tracked>()
    private var nextId = 1L

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
        detachAll()
        CallUi.calls = emptyList()
        CallUi.primaryId = null
        leaveForeground()
        CallNotifier.clear(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        // A brand-new conference clears the last call's ended summary.
        if (calls.isEmpty()) CallUi.last = null
        val tracked = Tracked(
            call = call,
            id = nextId++,
            incoming = stateOf(call) == Call.STATE_RINGING,
        )
        calls[tracked.id] = tracked
        runCatching { call.registerCallback(watcher) }
        publish()
        // Only the first call opens the screen; the next one arrives behind an
        // activity that is already up.
        if (calls.size == 1) showScreen()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val tracked = calls.values.firstOrNull { it.call === call } ?: return
        val finished = CallUi.calls.firstOrNull { it.id == tracked.id }?.let { live ->
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
        runCatching { call.unregisterCallback(watcher) }
        calls.remove(tracked.id)
        CallUi.last = finished
        publish()
        if (calls.isEmpty()) {
            leaveForeground()
            CallNotifier.clear(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) = publish()

    private fun detachAll() {
        calls.values.forEach { runCatching { it.call.unregisterCallback(watcher) } }
        calls.clear()
    }

    /** Rebuilds the whole list from the tracked calls and follows the primary one. */
    private fun publish() {
        if (calls.isEmpty()) {
            CallUi.calls = emptyList()
            CallUi.primaryId = null
            return
        }
        val live = calls.values.map { buildLive(it) }
        val primary = live.firstOrNull { it.ringing }
            ?: live.firstOrNull { it.active || it.holding }
            ?: live.firstOrNull { !it.ended }
            ?: live.firstOrNull()
        CallUi.calls = live
        CallUi.primaryId = primary?.id
        // A finished primary has nothing left to ring or count.
        if (primary == null || primary.ended) {
            leaveForeground()
            CallNotifier.clear(this)
            return
        }
        // Telecom does not ring for the dialer: this both rings and puts the call
        // in the shade, so an incoming call can never arrive silently again. Only
        // the primary call owns the notification and the foreground slot.
        val posted = CallNotifier.update(this, primary)
        if (posted != null) holdForeground(posted)
    }

    private fun buildLive(tracked: Tracked): LiveCall {
        val details = tracked.call.details
        // The handle can come back empty or reformatted once the call connects; the
        // last real number stands in for it.
        val number = numberOf(details).ifBlank { tracked.knownNumber }
            .also { tracked.knownNumber = it }
        val audio = callAudioState
        val state = stateOf(tracked.call)
        val over = state == Call.STATE_DISCONNECTED
        return LiveCall(
            id = tracked.id,
            number = number,
            name = resolveName(tracked, number, details),
            state = state,
            muted = audio?.isMuted == true,
            speaker = audio?.route == CallAudioState.ROUTE_SPEAKER,
            connectTime = if (state == Call.STATE_ACTIVE) details.connectTimeMillis else 0L,
            incoming = tracked.incoming,
            endCode = if (over) details.disconnectCause?.code ?: DisconnectCause.UNKNOWN else -1,
            talkSeconds = if (over) talkSeconds(details) else 0,
        )
    }

    /**
     * The number as the network gave it. An incoming call can arrive with no handle
     * yet, or with the number in the gateway info or carrying ";" parameters, any of
     * which used to leave the lookup with nothing to look up.
     */
    private fun numberOf(details: Call.Details): String {
        val raw = details.handle?.schemeSpecificPart?.takeIf { it.isNotBlank() }
            ?: details.gatewayInfo?.originalAddress?.schemeSpecificPart.orEmpty()
        return raw.substringBefore(';').trim()
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
    private fun resolveName(tracked: Tracked, number: String, details: Call.Details): String? {
        if (number.isNotBlank() && number != tracked.lookedUpNumber) {
            tracked.lookedUpNumber = number
            tracked.contactName = runCatching { ContactNames.lookup(this, number) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
        tracked.contactName?.let { return it }

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

    /** The primary call, or the first one still around if the primary just ended. */
    private fun primaryTracked(): Tracked? = calls[CallUi.primaryId] ?: calls.values.firstOrNull()

    private fun primaryCall(): Call? = primaryTracked()?.call

    companion object {
        private var running: TrinityInCallService? = null

        fun answer() {
            runCatching { running?.primaryCall()?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        }

        /** Answer a specific call — used by a call-waiting row. */
        fun answer(id: Long) {
            runCatching { running?.calls?.get(id)?.call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
        }

        fun decline() {
            runCatching { running?.primaryCall()?.reject(false, null) }
        }

        /** Decline a specific call — used by a call-waiting row. */
        fun decline(id: Long) {
            runCatching { running?.calls?.get(id)?.call?.reject(false, null) }
        }

        fun hangUp() {
            runCatching { running?.primaryCall()?.disconnect() }
        }

        /** Hang up one call out of a conference. */
        fun hangUp(id: Long) {
            runCatching { running?.calls?.get(id)?.call?.disconnect() }
        }

        /** Make the held call active and put the active one on hold. */
        fun swap() {
            val service = running ?: return
            val all = service.calls.values.map { it.call }
            if (all.size < 2) return
            val active = all.firstOrNull { service.stateOf(it) == Call.STATE_ACTIVE }
            val held = all.firstOrNull { service.stateOf(it) == Call.STATE_HOLDING }
            runCatching {
                active?.hold()
                held?.unhold()
            }
        }

        /** Join every tracked call into one conference. */
        fun merge() {
            val service = running ?: return
            val all = service.calls.values.map { it.call }
            val host = service.primaryCall() ?: return
            if (all.size < 2) return
            runCatching { all.filter { it !== host }.forEach { host.conference(it) } }
        }

        /** Keep the primary in conversation and hold every other call. */
        fun holdOther() {
            val service = running ?: return
            val host = service.primaryCall() ?: return
            runCatching {
                service.calls.values.map { it.call }.filter { it !== host }.forEach { it.hold() }
                host.unhold()
            }
        }

        /**
         * Place another call while one is up. Telecom then adds it through
         * [onCallAdded], where it joins the same list. Nothing is dialled without
         * the CALL_PHONE permission the manifest asks for.
         */
        fun addCall(number: String) {
            val service = running ?: return
            val dialled = number.trim()
            if (dialled.isEmpty()) return
            if (ContextCompat.checkSelfPermission(
                    service,
                    Manifest.permission.CALL_PHONE,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
            val telecom = service.getSystemService(TelecomManager::class.java) ?: return
            runCatching {
                telecom.placeCall(Uri.fromParts("tel", dialled, null), Bundle())
            }.onFailure { android.util.Log.w("TrinityCall", "could not place second call", it) }
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

        /** Pressing a key sends a tone on the primary call; it stops on its own. */
        fun tone(key: Char) {
            val target = running?.primaryCall() ?: return
            runCatching { target.playDtmfTone(key) }
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { target.stopDtmfTone() } },
                120,
            )
        }
    }
}
