package com.sarim.trinity.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/** One physical SIM Trinity can dial from. */
data class SimCard(
    val subscriptionId: Int,
    val slotIndex: Int,
    val label: String,
    val carrier: String?,
) {
    val name: String
        get() = label.ifBlank { "SIM ${slotIndex + 1}" }

    /** Short tag for a row: SIM 1, SIM 2. */
    val tag: String get() = "SIM ${slotIndex + 1}"
}

/**
 * Dual-SIM plumbing. Everything degrades to nothing on a single-SIM or
 * SIM-less device: no chips, no tags, and `placeCall` falls back to the
 * ordinary ACTION_CALL intent.
 */
class Sims(private val context: Context) {

    private fun canReadSubscriptions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun active(): List<SimCard> {
        if (!canReadSubscriptions()) return emptyList()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val list = runCatching { manager.activeSubscriptionInfoList }.getOrNull()
            ?: return emptyList()
        return list.sortedBy { it.simSlotIndex }.map { info ->
            SimCard(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                label = info.displayName?.toString().orEmpty(),
                carrier = info.carrierName?.toString(),
            )
        }
    }

    fun defaultVoiceSubscriptionId(): Int? {
        val id = runCatching { SubscriptionManager.getDefaultVoiceSubscriptionId() }.getOrNull()
        return id?.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID }
    }

    /**
     * Places the call on [card]. Returns the SIM tag actually used, or null when the
     * call went out through the ordinary intent (so the default SIM took it).
     *
     * Android has no public "dial from this subscription" API: the phone account has
     * to be resolved from the subscription id, which only the framework method does.
     * We ask for it, and fall back honestly when it is unavailable.
     */
    fun placeCall(number: String, card: SimCard?): String? {
        val uri = Uri.fromParts("tel", number, null)
        val telecom = context.getSystemService(TelecomManager::class.java)
        // A dialer dials through Telecom, which then binds this app's own
        // InCallService to draw the call. Starting an ACTION_CALL intent instead
        // resolves back into this app once it is the default dialer, and a call
        // that was never handed to Telecom simply never goes out.
        if (telecom != null) {
            val extras = Bundle()
            var onRequestedSim = true
            if (card != null && card.subscriptionId != defaultVoiceSubscriptionId()) {
                val handle = accountHandleFor(telecom, card.subscriptionId)
                onRequestedSim = handle != null
                if (handle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                }
            }
            if (runCatching { telecom.placeCall(uri, extras) }.isSuccess) {
                return if (onRequestedSim) card?.tag else null
            }
        }
        // Nothing could dial: hand the number to whatever can, pre-filled.
        val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!runCatching { context.startActivity(intent) }.isSuccess) return null
        return card?.tag
    }

    private fun accountHandleFor(telecom: TelecomManager, subscriptionId: Int): PhoneAccountHandle? =
        runCatching {
            val method = TelecomManager::class.java
                .getMethod("getPhoneAccountHandleForSubscriptionId", Int::class.javaPrimitiveType)
            method.invoke(telecom, subscriptionId) as? PhoneAccountHandle
        }.getOrNull()

    /**
     * The call log stores the phone account that carried a call. On dual-SIM it is
     * the subscription id, which we can turn back into "SIM 2".
     */
    fun labelForAccount(accountId: String?, cards: List<SimCard>): String? {
        if (accountId.isNullOrBlank() || cards.size < 2) return null
        val id = accountId.toIntOrNull() ?: return null
        return cards.firstOrNull { it.subscriptionId == id }?.tag
    }
}
