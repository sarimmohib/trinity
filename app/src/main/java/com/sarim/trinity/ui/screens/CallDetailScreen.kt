package com.sarim.trinity.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.absoluteStamp
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.theme.Palette

/** One call, everything the log knows about it, and what you can do next. */
@Composable
fun CallDetailScreen(
    state: TrinityState,
    entry: CallEntry,
    onBack: () -> Unit,
    onNewContact: (String) -> Unit,
) {
    val kind = when {
        entry.missed -> "Missed"
        entry.outgoing -> "Outgoing"
        else -> "Incoming"
    }
    val duration = when {
        entry.durationSeconds <= 0L -> "no answer"
        else -> "${entry.durationSeconds / 60}m ${entry.durationSeconds % 60}s"
    }
    val blocked = entry.rule != null

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("CALLS", size = 9)
            Box(
                Modifier
                    .size(30.dp)
                    .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Palette.Ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Hairline()

        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(entry.initials, filled = entry.missed, size = 56)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.name ?: formatNumber(entry.number),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatNumber(entry.number),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Palette.Muted,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (blocked) {
                MonoLabel("BLOCKED · ${entry.rule?.label}", color = Palette.Ink, size = 8)
            }
        }
        Hairline()

        DetailRow("TYPE", kind)
        DetailRow("WHEN", absoluteStamp(entry.date))
        DetailRow("DURATION", duration)
        DetailRow("NUMBER", formatNumber(entry.number))
        entry.simLabel?.let { DetailRow("SIM", it) }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Action(
                label = "CALL BACK",
                icon = Icons.Outlined.Phone,
                filled = true,
            ) { state.call(entry.number, entry.name) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action(
                    label = "MESSAGE",
                    icon = Icons.Outlined.Message,
                    modifier = Modifier.weight(1f),
                ) { state.messagePerson(entry.number) }
                Action(
                    label = if (blocked) "UNBLOCK" else "BLOCK",
                    icon = Icons.Outlined.Shield,
                    modifier = Modifier.weight(1f),
                ) {
                    if (blocked) {
                        state.removeNumber(entry.number)
                        state.removeName(entry.name.orEmpty())
                    } else {
                        state.addRule(entry.number)
                    }
                    onBack()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action(
                    label = "SAVE CONTACT",
                    icon = Icons.Outlined.PersonAdd,
                    modifier = Modifier.weight(1f),
                ) { onNewContact(entry.number) }
                Action(
                    label = "DELETE",
                    icon = Icons.Outlined.Delete,
                    modifier = Modifier.weight(1f),
                ) {
                    state.deleteCall(entry.id)
                    onBack()
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(label, size = 8, color = Palette.Faint)
            Text(value, fontSize = 12.sp, color = Palette.Ink)
        }
        Hairline()
    }
}

@Composable
private fun Action(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (filled) Palette.Ink else Palette.Paper, RoundedCornerShape(5.dp))
            .border(1.dp, if (filled) Palette.Ink else Palette.Line, RoundedCornerShape(5.dp))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (filled) Palette.Paper else Palette.Ink,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        MonoLabel(label, color = if (filled) Palette.Paper else Palette.Ink, size = 8)
    }
}
