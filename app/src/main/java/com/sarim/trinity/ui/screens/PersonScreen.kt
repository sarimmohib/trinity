package com.sarim.trinity.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.PhoneMissed
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.data.Channels
import com.sarim.trinity.data.ContactChannels
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.PersonAvatar
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.components.uiLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.theme.Palette

@Composable
fun PersonScreen(
    state: TrinityState,
    person: Person,
    onBack: () -> Unit,
    onEdit: (Person) -> Unit,
    onMessage: (String) -> Unit,
    onOpenThread: (Conversation) -> Unit,
    onOpenCall: (CallEntry) -> Unit,
    onOpenCalls: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val rules = state.blockStore.rules.value
    val hit = BlockMatcher.match(person.name, person.number, null, rules)
    val calls = state.callsFor(person.number)
    val thread = state.conversationForNumber(person.number)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    var photoFailed by remember { mutableStateOf(false) }

    // What the provider knows about reaching this person: WhatsApp identity and
    // whether the phone's Google contacts are actually syncing.
    var channels by remember(person.id) { mutableStateOf<Channels?>(null) }
    LaunchedEffect(person.id, person.photoUri) {
        channels = ContactChannels.read(context, person)
    }
    val info = channels

    fun open(intent: Intent?) {
        if (intent == null) return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun whatsappChat() = open(ContactChannels.chatIntent(person.number))
    fun whatsappCall() = open(ContactChannels.callIntent(person.number))

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked != null) {
            state.setContactPhoto(person, picked) { ok -> photoFailed = !ok }
        }
    }

    fun pickPhoto() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("CONTACT", size = 9)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit contact",
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onEdit(person) },
                )
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() },
                )
            }
        }
        Hairline()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { pickPhoto() },
                ) {
                    PersonAvatar(person.initials, person.photoUri, filled = true, size = 68)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        person.name,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    MonoLabel(formatNumber(person.number), size = 10)
                    Spacer(Modifier.height(4.dp))
                    MonoLabel(
                        when {
                            photoFailed -> "PHOTO NOT SAVED"
                            person.photoUri == null -> uiLabel("TAP PHOTO TO ADD ONE")
                            else -> uiLabel("TAP PHOTO TO CHANGE")
                        },
                        size = 7,
                        color = Palette.Faint,
                    )
                }
            }
            if (hit != null) {
                Row(
                    Modifier
                        .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                        .background(Palette.Wash, RoundedCornerShape(4.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Palette.Ink,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    MonoLabel("${hit.label} · ${hit.value}", color = Palette.Ink, size = 8)
                }
            }
            Hairline()

            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile("CALL", Icons.Outlined.Phone, Modifier.weight(1f)) {
                        state.call(person.number, person.name)
                    }
                    ActionTile("MESSAGE", Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f)) {
                        onMessage(person.number)
                    }
                }
                if (info?.whatsappInstalled != false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionTile(
                            "WHATSAPP",
                            Icons.Outlined.ChatBubbleOutline,
                            Modifier.weight(1f),
                            tint = MaterialTheme.colorScheme.tertiaryContainer,
                        ) { whatsappChat() }
                        ActionTile(
                            "WA CALL",
                            Icons.Outlined.PhoneInTalk,
                            Modifier.weight(1f),
                            tint = MaterialTheme.colorScheme.tertiaryContainer,
                        ) { whatsappCall() }
                    }
                }
            }

            SectionLabel("AVAILABILITY")
            SimpleRow(
                "WhatsApp",
                when {
                    info == null -> null
                    !info.whatsappInstalled -> "NOT INSTALLED"
                    info.onWhatsApp -> "AVAILABLE"
                    else -> "NO RECORD"
                },
                onClick = { if (info?.whatsappInstalled == true) whatsappChat() },
            )
            SimpleRow(
                "Google contacts sync",
                when {
                    info == null -> null
                    info.googleAccounts == 0 -> "NO GOOGLE ACCOUNT"
                    !info.googleSyncOn -> "OFF"
                    !info.autoSync -> "AUTO-SYNC OFF"
                    else -> "ON · ${info.googleAccounts} ACCOUNTS"
                },
                onClick = {
                    open(Intent(Settings.ACTION_SYNC_SETTINGS))
                },
            )
            SimpleRow(
                "Last call",
                calls.firstOrNull()?.let { relativeStamp(it.date) } ?: "NONE",
                onClick = { if (calls.isNotEmpty()) onOpenCalls() },
            )
            SimpleRow(
                "Last text",
                thread?.let { relativeStamp(it.date) } ?: "NONE",
                onClick = { thread?.let { onOpenThread(it) } },
            )

            SectionLabel(
                if (calls.isEmpty()) "CALL HISTORY" else "CALL HISTORY · ${calls.size}",
            )
            if (calls.isEmpty()) {
                MonoLabel(
                    uiLabel("No calls with this contact yet."),
                    size = 8,
                    color = Palette.Faint,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 12.dp),
                )
            } else {
                calls.take(4).forEach { entry ->
                    CallHistoryRow(entry, person.name) { onOpenCall(entry) }
                }
                if (calls.size > 4) {
                    SimpleRow("All calls", "${calls.size} TOTAL") { onOpenCalls() }
                }
            }

            MonoLabel(
                "NUMBERS · ${person.allNumbers.size}",
                size = 8,
                color = Palette.Faint,
                modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
            )
            val waDigits = whatsappJidDigits(info)
            person.allNumbers.forEach { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { state.call(entry.number, person.name) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatNumber(entry.number),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Palette.Ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        MonoLabel(entry.label, size = 8, color = Palette.Faint)
                    }
                    if (waDigits != null && ContactChannels.digits(entry.number) == waDigits) {
                        MonoLabel("WA", color = Palette.Ink, size = 7)
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = "Call ${entry.number}",
                        tint = Palette.Muted,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Hairline()
            }

            if (person.emails.isNotEmpty()) {
                MonoLabel(
                    "EMAIL",
                    size = 8,
                    color = Palette.Faint,
                    modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp),
                )
                person.emails.forEach { address ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                open(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$address")))
                            }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(address, fontSize = 13.sp, color = Palette.Ink, modifier = Modifier.weight(1f))
                        MonoLabel("MAIL", color = Palette.Faint, size = 8)
                    }
                    Hairline()
                }
            }

            Spacer(Modifier.height(14.dp))
            if (person.photoUri != null) {
                SimpleRow("Remove photo", "KEEPS THE CONTACT") { confirmRemovePhoto = true }
            }
            SimpleRow("Copy number") {
                clipboard.setText(AnnotatedString(person.allNumbers.joinToString(", ") { it.number }))
            }
            SimpleRow("Share contact") {
                open(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "${person.name} · ${person.allNumbers.joinToString(", ") { it.number }}",
                        )
                    },
                )
            }
            SimpleRow(
                if (hit != null) "Unblock this contact" else "Block this contact",
                if (hit != null) hit.label else "NAME AND NUMBER RULE",
            ) {
                if (hit != null) {
                    rules.names
                        .filter {
                            it.value.equals(hit.value, ignoreCase = true) ||
                                person.name.contains(it.value, true)
                        }
                        .forEach { state.removeName(it.value) }
                    person.allNumbers.forEach { state.removeNumber(it.number) }
                    state.removeName(person.name)
                } else {
                    state.addRule(person.name)
                    person.allNumbers.forEach { state.addRule(it.number) }
                }
            }
            SimpleRow("Delete contact", "CANNOT BE UNDONE") { confirmDelete = true }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        InkSheet(
            title = "DELETE CONTACT",
            actions = listOf(
                SheetAction("Delete ${person.name}", "CANNOT BE UNDONE") {
                    state.deleteContact(person) { onBack() }
                },
                SheetAction("Keep it") { },
            ),
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmRemovePhoto) {
        InkSheet(
            title = "REMOVE PHOTO",
            actions = listOf(
                SheetAction("Remove the photo") {
                    state.removeContactPhoto(person) { ok -> photoFailed = !ok }
                },
                SheetAction("Keep it") { },
            ),
            onDismiss = { confirmRemovePhoto = false },
        )
    }
}

/** Digits behind a WhatsApp identity, for marking the number that has one. */
private fun whatsappJidDigits(info: Channels?): String? =
    info?.whatsappJid?.substringBefore('@')?.filter { it.isDigit() }

@Composable
private fun SectionLabel(text: String) {
    MonoLabel(
        uiLabel(text),
        size = 8,
        color = Palette.Faint,
        modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun SimpleRow(label: String, hint: String? = null, onClick: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clickable { onClick() }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (hint != null) MonoLabel(hint, color = Palette.Faint, size = 8)
        }
        Hairline()
    }
}

@Composable
private fun CallHistoryRow(entry: CallEntry, name: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                entry.missed -> Icons.Outlined.PhoneMissed
                entry.outgoing -> Icons.Outlined.CallMade
                else -> Icons.Outlined.CallReceived
            },
            contentDescription = null,
            tint = Palette.Muted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name ?: formatNumber(entry.number),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            MonoLabel(entry.detail, size = 8, color = Palette.Faint)
        }
        MonoLabel(relativeStamp(entry.date), color = Palette.Faint, size = 8)
    }
    Hairline()
}

@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(56.dp)
            .glassBlock(tint, 18.dp, 2.4f)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        MonoLabel(label, color = scheme.onPrimaryContainer, size = 9)
    }
}
