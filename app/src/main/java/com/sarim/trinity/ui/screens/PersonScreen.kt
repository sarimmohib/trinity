package com.sarim.trinity.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.theme.Palette

@Composable
fun PersonScreen(
    state: TrinityState,
    person: Person,
    onBack: () -> Unit,
    onEdit: (Person) -> Unit,
    onMessage: (String) -> Unit,
    onOpenCalls: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val rules = state.blockStore.rules.value
    val hit = BlockMatcher.match(person.name, person.number, null, rules)
    val callCount = state.callCountFor(person.number)
    var confirmDelete by remember { mutableStateOf(false) }

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
            Column(Modifier.padding(24.dp)) {
                Avatar(person.initials, filled = true, size = 64)
                Spacer(Modifier.height(14.dp))
                Text(person.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Spacer(Modifier.height(4.dp))
                MonoLabel(formatNumber(person.number), size = 10)
                if (hit != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
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
            }
            Hairline()

            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActionTile("CALL", Icons.Outlined.Phone, Modifier.weight(1f)) {
                    state.call(person.number, person.name)
                }
                ActionTile("MESSAGE", Icons.Outlined.ChatBubbleOutline, Modifier.weight(1f)) {
                    onMessage(person.number)
                }
            }

            MonoLabel("NUMBERS · ${person.allNumbers.size}", size = 8, color = Palette.Faint, modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 6.dp))
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
                MonoLabel("EMAIL", size = 8, color = Palette.Faint, modifier = Modifier.padding(start = 24.dp, top = 12.dp, bottom = 6.dp))
                person.emails.forEach { address ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:$address")),
                                    )
                                }
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
            SimpleRow("Copy number") {
                clipboard.setText(AnnotatedString(person.allNumbers.joinToString(", ") { it.number }))
            }
            SimpleRow("Call history", if (callCount == 0) "NONE YET" else "$callCount CALLS") {
                if (callCount > 0) onOpenCalls()
            }
            SimpleRow("Share contact") {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${person.name} · ${person.allNumbers.joinToString(", ") { it.number }}",
                            )
                        },
                    )
                }
            }
            SimpleRow(
                if (hit != null) "Unblock this contact" else "Block this contact",
                if (hit != null) hit.label else "NAME AND NUMBER RULE",
            ) {
                if (hit != null) {
                    rules.names.filter { it.value.equals(hit.value, ignoreCase = true) || person.name.contains(it.value, true) }
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
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Ink, modifier = Modifier.weight(1f))
            if (hint != null) MonoLabel(hint, color = Palette.Faint, size = 8)
        }
        Hairline()
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(52.dp)
            .background(Palette.Ink, RoundedCornerShape(5.dp))
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Paper, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        MonoLabel(label, color = Palette.Paper, size = 9)
    }
}
