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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
                CallSurface(
                    onClose = {
                        CallUi.last = null
                        finish()
                    },
                )
            }
        }
    }
}

/** How long a finished call stays up, saying how it ended, before the screen closes. */
private const val ENDED_SCREEN_MS = 2_500L

@Composable
private fun CallSurface(onClose: () -> Unit) {
    val calls = CallUi.calls
    val primary = CallUi.live
    val finished = CallUi.last
    // A call that has just ended stays up for a moment, saying how it ended.
    val call = primary ?: finished
    val over = call?.ended == true
    var seen by remember { mutableStateOf(false) }
    var answeredAt by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(0L) }
    var keys by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var addNumber by remember { mutableStateOf("") }

    // No call left closes the screen — after a moment on how the last one ended, when
    // there is something to say. A new call arriving cancels the wait, because the
    // effect is keyed on both.
    LaunchedEffect(calls.isNotEmpty(), finished) {
        when {
            calls.isNotEmpty() -> seen = true
            finished != null -> {
                delay(ENDED_SCREEN_MS)
                onClose()
            }
            seen -> onClose()
        }
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
                    call.ended -> "CALL OVER"
                    call.ending -> "ENDING CALL"
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
                    call?.ended == true -> call.outcome
                    call?.ending == true -> "ENDING"
                    call?.active == true -> formatDuration(seconds)
                    call?.ringing == true -> "RINGING"
                    call?.holding == true -> "ON HOLD"
                    else -> "CALLING"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = if (over) 24.sp else 34.sp,
                color = if (call?.active == true || over) Palette.Ink else Palette.Faint,
            )
            Spacer(Modifier.height(6.dp))
            MonoLabel(
                when {
                    call == null -> "ENDED"
                    call.ended -> endSummary(call)
                    call.ringing -> "INCOMING"
                    call.holding -> "ON HOLD"
                    call.active -> "CONNECTED"
                    else -> ""
                },
                size = 8,
                color = Palette.Faint,
            )
        }

        // The ADD CALL control must be reachable with one active call — that is how a
        // second call is placed. Per-call rows below are shown only when there are two.
        if (calls.isNotEmpty() && !over) {
            OtherCalls(
                calls = calls,
                primaryId = primary?.id,
                adding = adding,
                addNumber = addNumber,
                onAddNumber = { addNumber = it },
                onToggleAdding = { adding = it },
                onAddDone = {
                    addNumber = ""
                    adding = false
                },
            )
        }

        // Nothing to mute or key into once the call is over.
        if (!over) Row(
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

        if (keys && !over) {
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
            if (over) {
                Pill(
                    label = "DONE",
                    filled = false,
                    modifier = Modifier.fillMaxWidth(),
                ) { onClose() }
            } else if (call?.ringing == true) {
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

/** Every call except the one on the main card, plus the add-a-call field. */
@Composable
private fun OtherCalls(
    calls: List<LiveCall>,
    primaryId: Long?,
    adding: Boolean,
    addNumber: String,
    onAddNumber: (String) -> Unit,
    onToggleAdding: (Boolean) -> Unit,
    onAddDone: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .glassBlock(Palette.Accents[2], 18.dp, 1f)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel(
                if (calls.size > 1) "${calls.size} CALLS" else "SECOND CALL",
                size = 8,
                color = Palette.Faint,
            )
            if (calls.size > 1) {
                MonoLabel(
                    "HOLD OTHERS",
                    size = 8,
                    color = Palette.Ink,
                    modifier = Modifier.clickable(onClickLabel = "Hold all other calls") {
                        TrinityInCallService.holdOther()
                    },
                )
            }
        }
        if (calls.size > 1) {
            Spacer(Modifier.height(8.dp))
            calls.filterNot { it.id == primaryId }.forEach { other ->
                OtherCallRow(other)
                Spacer(Modifier.height(6.dp))
            }
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = addNumber,
                    onValueChange = onAddNumber,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Palette.Ink,
                    ),
                    cursorBrush = SolidColor(Palette.Ink),
                    modifier = Modifier
                        .weight(1f)
                        .glassBlock(Palette.Paper, 12.dp, 0.8f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (addNumber.isEmpty()) MonoLabel("number to add", size = 8, color = Palette.Faint)
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                TinyAction("CALL") {
                    if (addNumber.isNotBlank()) TrinityInCallService.addCall(addNumber)
                    onAddDone()
                }
                Spacer(Modifier.width(6.dp))
                TinyAction("CANCEL") { onAddDone() }
            }
        } else {
            MonoLabel(
                "+ ADD CALL",
                size = 8,
                color = Palette.Ink,
                modifier = Modifier.clickable(onClickLabel = "Add another call") { onToggleAdding(true) },
            )
        }
    }
}

/** One non-primary call: who it is, what it is doing, and what can be done to it. */
@Composable
private fun OtherCallRow(other: LiveCall) {
    Row(
        Modifier
            .fillMaxWidth()
            .glassBlock(Palette.Paper, 14.dp, 0.8f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                other.name ?: formatNumber(other.number),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Ink,
                maxLines = 1,
            )
            MonoLabel(
                when {
                    other.ringing -> "RINGING"
                    other.active -> "ACTIVE"
                    other.holding -> "ON HOLD"
                    else -> "CALLING"
                },
                size = 7,
                color = Palette.Faint,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (other.ringing) {
                TinyAction("DECLINE") { TrinityInCallService.decline(other.id) }
                TinyAction("ANSWER") { TrinityInCallService.answer(other.id) }
            } else {
                TinyAction("SWAP") { TrinityInCallService.swap() }
                TinyAction("MERGE") { TrinityInCallService.merge() }
                TinyAction("END") { TrinityInCallService.hangUp(other.id) }
            }
        }
    }
}

@Composable
private fun TinyAction(label: String, onClick: () -> Unit) {
    MonoLabel(
        label,
        color = Palette.Ink,
        size = 7,
        modifier = Modifier
            .border(1.dp, Palette.Line, RoundedCornerShape(4.dp))
            .clickable(onClickLabel = label) { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

private fun initials(name: String?): String =
    (name ?: "")
        .trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "#" }

/** The line under a finished call's outcome: how long it ran and who hung up. */
private fun endSummary(call: LiveCall): String = listOfNotNull(
    if (call.talkSeconds > 0) formatDuration(call.talkSeconds) else null,
    call.endedBy,
).joinToString(" · ")

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
