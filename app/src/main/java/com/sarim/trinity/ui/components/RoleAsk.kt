package com.sarim.trinity.ui.components

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sarim.trinity.ui.TrinityState

/** Faster than any person can read a dialog and answer it. */
private const val INSTANT = 400L

/**
 * Asks the system for a default-app role, and never ends on an unchanged screen.
 *
 * The dialog is opened through a result launcher because *how fast it comes back*
 * is the only way to tell a real answer from a dead dialog: some vendor builds
 * (MIUI's does) close the role dialog the instant it opens because it cannot work
 * out which app is asking. A person cannot dismiss a dialog in [INSTANT] ms, so an
 * instant return means nobody was asked — and the system page that lists default
 * apps is opened instead, which those builds do honour.
 */
@Composable
fun rememberRoleAsker(
    state: TrinityState,
    onNote: (String?) -> Unit,
): (TrinityState.DefaultApp) -> Unit {
    val context = LocalContext.current
    var askedAt by remember { mutableStateOf(0L) }
    var wanted by remember { mutableStateOf<TrinityState.DefaultApp?>(null) }

    fun openDefaultApps() {
        runCatching {
            context.startActivity(
                state.defaultAppsSettingsIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun gaveUp() {
        askedAt = 0L
        wanted = null
        onNote("NO SYSTEM DIALOG ON THIS DEVICE — OPENED DEFAULT APPS")
        openDefaultApps()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val kind = wanted
        val instant = askedAt > 0L && SystemClock.elapsedRealtime() - askedAt < INSTANT
        askedAt = 0L
        wanted = null
        when {
            kind == null -> Unit
            instant && !state.holdsDefault(kind) -> {
                onNote("THIS DEVICE CLOSES THE ROLE DIALOG — OPENED DEFAULT APPS")
                openDefaultApps()
            }
            else -> onNote(null)
        }
    }

    return { kind ->
        wanted = kind
        askedAt = SystemClock.elapsedRealtime()
        val request = state.defaultAppRequests(kind).firstOrNull()
        if (request == null || runCatching { launcher.launch(request) }.isFailure) {
            gaveUp()
        }
    }
}
