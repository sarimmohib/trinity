package com.sarim.trinity.call
import com.sarim.trinity.ui.components.glassBlock

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.theme.Palette
import com.sarim.trinity.ui.theme.ThemePrefs
import com.sarim.trinity.ui.theme.TrinityTheme
import kotlinx.coroutines.delay

/**
 * The screen behind the phone role. It draws whatever the telecom service reports
 * and closes itself when the call is gone, so a call can never leave a dead screen.
 */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        setContent {
            TrinityTheme {
                CallSurface(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun CallSurface(onClose: () -> Unit) {
    val call = CallUi.live
    var seen by remember { mutableStateOf(false) }
    var answeredAt by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(0L) }
    var keys by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf("") }

    // A null call only means "hang up the screen" once there was one to show.
    LaunchedEffect(call) {
        if (call != null) seen = true else if (seen) onClose()
    }

    // The clock starts when the call is answered, never when the number is dialled,
    // and it is read from a timestamp instead of counted in ticks, so a missed
    // recomposition cannot leave it sitting at zero.
    LaunchedEffect(call?.active) {
        if (call?.active == true) {
            val now = SystemClock.elapsedRealtime()
            answeredAt = if (call.connectTime > 0L) {
                now - (System.currentTimeMillis() - call.connectTime).coerceAtLeast(0L)
            } else {
                now
            }
            while (true) {
                clock = SystemClock.elapsedRealtime()
                delay(500)
            }
        } else {
            answeredAt = 0L
        }
    }

    val seconds = if (answeredAt == 0L) 0 else ((clock - answeredAt) / 1000L).toInt()

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bone),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(
                when {
                    call == null -> "CALL OVER"
                    call.ringing -> "INCOMING CALL"
                    call.holding -> "ON HOLD"
                    call.active -> if (call.speaker) "IN CALL · SPEAKER" else "IN CALL · HANDSET"
                    else -> ""
                },
                size = 9,
            )
        }
        Hairline()

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Avatar(
                initials = initials(call?.name),
                filled = true,
                size = 88,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                call?.name ?: formatNumber(call?.number.orEmpty()),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
            )
            if (call?.name != null) {
                Spacer(Modifier.height(3.dp))
                MonoLabel(formatNumber(call.number), size = 10)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                when {
                    call?.active == true -> formatDuration(seconds)
                    call?.ringing == true -> "RINGING"
                    call?.holding == true -> "ON HOLD"
                    else -> "CALLING"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 34.sp,
                color = if (call?.active == true) Palette.Ink else Palette.Faint,
            )
            Spacer(Modifier.height(6.dp))
            MonoLabel(
                when {
                    call == null -> "ENDED"
                    call.ringing -> "INCOMING"
                    call.holding -> "ON HOLD"
                    call.active -> "CONNECTED"
                    else -> ""
                },
                size = 8,
                color = Palette.Faint,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallTile("MUTE", Icons.Outlined.MicOff, call?.muted == true, Modifier.weight(1f)) {
                TrinityInCallService.mute(call?.muted != true)
            }
            CallTile("SPEAKER", Icons.Outlined.VolumeUp, call?.speaker == true, Modifier.weight(1f)) {
                TrinityInCallService.speaker(call?.speaker != true)
            }
            CallTile("KEYPAD", Icons.Outlined.Dialpad, keys, Modifier.weight(1f)) { keys = !keys }
        }

        if (keys) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key ->
                            Box(
                                Modifier
                                    .size(50.dp)
                                    .glassBlock(Palette.Accents[1], 25.dp, 1.1f)
                                    .clickable {
                                        TrinityInCallService.tone(key.first())
                                        sent += key
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    key,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 17.sp,
                                    color = Palette.Ink,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                MonoLabel(
                    if (sent.isEmpty()) "NO DIGITS SENT" else "SENT · $sent",
                    size = 8,
                    color = Palette.Ink,
                )
            }
        }

        Column(Modifier.padding(24.dp)) {
            if (call?.ringing == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(
                        label = "DECLINE",
                        filled = false,
                        modifier = Modifier.weight(1f),
                    ) { TrinityInCallService.decline() }
                    Pill(
                        label = "ANSWER",
                        filled = true,
                        modifier = Modifier.weight(1f),
                    ) { TrinityInCallService.answer() }
                }
            } else {
                Pill(
                    label = "END CALL",
                    filled = true,
                    showIcon = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { TrinityInCallService.hangUp() }
            }
        }
    }
}

private fun initials(name: String?): String =
    (name ?: "")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "#" }

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun Pill(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(56.dp)
            .glassBlock(
                if (filled) Palette.Green else Palette.Paper,
                28.dp,
                if (filled) 3.2f else 0.7f,
            )
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(
                Icons.Outlined.Phone,
                contentDescription = null,
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        MonoLabel(
            label,
            color = if (filled) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Ink,
            size = 10,
        )
    }
}

@Composable
private fun CallTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(62.dp)
            .glassBlock(
                if (active) Palette.Green else Palette.Accents[(label.hashCode() and 0x7FFFFFFF) % Palette.Accents.size],
                22.dp,
                if (active) 3f else 0.9f,
            )
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Ink,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.height(5.dp))
        MonoLabel(
            label,
            color = if (active) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Muted,
            size = 7,
        )
    }
}
