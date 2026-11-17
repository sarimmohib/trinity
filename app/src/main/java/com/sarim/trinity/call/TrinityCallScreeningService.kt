package com.sarim.trinity.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import androidx.annotation.RequiresApi
import com.sarim.trinity.data.AuditStore
import com.sarim.trinity.data.BlockStore
import com.sarim.trinity.data.CallScreening
import com.sarim.trinity.data.ScreeningPrefs
import com.sarim.trinity.sms.ContactNames

/**
 * System call-screening service. Only bound while the user grants ROLE_CALL_SCREENING
 * (API 29+); on API 26–28 a third-party screening service is not bound and the ordinary
 * InCallService behaviour still applies. The decision itself is pure and lives in
 * [CallScreening]; this class only gathers context and translates the result into a
 * [CallResponse] plus an audit entry. No message bodies are ever read or stored.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class TrinityCallScreeningService : CallScreeningService() {

    override fun onScreenCall(call: Call.Details) {
        val number = call.handle?.schemeSpecificPart.orEmpty()
        val rules = BlockStore.get(this).rules.value
        val settings = ScreeningPrefs.load(this)
        val name = runCatching { ContactNames.lookup(this, number) }.getOrNull()

        val decision = CallScreening.decide(number, name, rules, settings)

        val response = CallResponse.Builder()
        when {
            decision.reject -> response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
            decision.silence -> response
                .setDisallowCall(false)
                .setSilenceCall(true)
            else -> response.setDisallowCall(false)
        }
        runCatching { respondToCall(call, response.build()) }

        // Audit only an actual screening decision. Ordinary allowed calls — no rule hit,
        // screening off — are left out, so the log stays a record of decisions made.
        if (decision.reject || decision.silence) {
            runCatching {
                AuditStore(this).record(
                    AuditStore.Entry(
                        at = System.currentTimeMillis(),
                        kind = "CALL",
                        address = number,
                        decision = if (decision.reject) "BLOCKED" else if (decision.silence) "BLOCKED" else "ALLOWED",
                        rule = decision.reason ?: "-",
                        scope = "CALLS",
                    ),
                )
            }
        }
    }
}
