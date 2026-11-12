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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.absoluteStamp
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.theme.Palette

/**
 * Everything the rules caught: texts Trinity stopped before they reached the
 * inbox, and messages already in the inbox that match a rule.
 */
@Composable
fun HeldScreen(state: TrinityState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("HELD", size = 9)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Held messages", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "None of these reached your inbox.",
                fontSize = 12.sp,
                color = Palette.Muted,
                lineHeight = 18.sp,
            )
        }
        Hairline()

        LazyColumn(Modifier.fillMaxSize()) {
            if (state.held.isNotEmpty()) {
                item { SectionLabel("STOPPED BEFORE DELIVERY · ${state.held.size}") }
                items(state.held.size) { index ->
                    val entry = state.held[index]
                    HeldCard(
                        title = entry.address,
                        body = entry.body,
                        stamp = absoluteStamp(entry.date),
                        rule = "${entry.ruleKind} · ${entry.ruleValue}",
                        primaryLabel = "DISCARD",
                        onPrimary = { state.discardHeld(entry) },
                        secondaryLabel = "ALLOW SENDER",
                        onSecondary = { state.allowSender(entry.address) },
                    )
                }
            }
            if (state.blockedMessages.isNotEmpty()) {
                item { SectionLabel("IN THE INBOX BUT MATCHED · ${state.blockedMessages.size}") }
                items(state.blockedMessages.size) { index ->
                    val message = state.blockedMessages[index]
                    HeldCard(
                        title = message.address,
                        body = message.body,
                        stamp = absoluteStamp(message.date),
                        rule = "${message.rule?.label.orEmpty()} · ${message.rule?.value.orEmpty()}",
                        primaryLabel = "REVEAL ONCE",
                        onPrimary = { state.reveal(message.id) },
                        secondaryLabel = "ALLOW SENDER",
                        onSecondary = { state.allowSender(message.address) },
                    )
                }
            }
            if (state.held.isEmpty() && state.blockedMessages.isEmpty()) {
                item {
                    Box(Modifier.padding(24.dp)) {
                        Text("Nothing is being held right now.", fontSize = 12.sp, color = Palette.Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp)) {
        MonoLabel(text, size = 8, color = Palette.Faint)
    }
}

@Composable
private fun HeldCard(
    title: String,
    body: String,
    stamp: String,
    rule: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .background(Palette.Wash, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Ink, RoundedCornerShape(10.dp))
            .padding(13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = Palette.Ink,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                MonoLabel(title, color = Palette.Ink, size = 9)
            }
            MonoLabel(stamp, color = Palette.Muted, size = 8)
        }
        Spacer(Modifier.height(7.dp))
        Text(body, fontSize = 13.sp, color = Palette.Ink, lineHeight = 19.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Matched: $rule",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedMono(primaryLabel, onPrimary)
            OutlinedMono(secondaryLabel, onSecondary)
        }
    }
}

@Composable
private fun OutlinedMono(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(28.dp)
            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(label, color = Palette.Ink, size = 8)
    }
}
