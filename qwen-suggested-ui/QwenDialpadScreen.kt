package com.sarim.trinity.ui.qwen_screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.qwen_components.ArchitectButton
import com.sarim.trinity.ui.qwen_components.ChromeLabel
import com.sarim.trinity.ui.qwen_components.NoticeBar
import com.sarim.trinity.ui.qwen_components.Rule
import com.sarim.trinity.ui.qwen_theme.QwenPalette
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.relativeStamp

private val keyRows = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QwenDialpadScreen(
    state: TrinityState,
    initialNumber: String = "",
    onBack: () -> Unit,
    onNewContact: (String) -> Unit,
) {
    LaunchedEffect(initialNumber) {
        if (initialNumber.isNotBlank()) state.typeDigits(initialNumber)
    }
    val number = state.dialpadNumber
    var lastPasted by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val rules = state.blockStore.rules.value
    val digits = number.filter { it.isDigit() }
    val blocked = BlockMatcher.match(null, number, null, rules)

    val known = if (digits.length >= 5) {
        state.contacts.firstOrNull { BlockMatcher.sameNumber(it.number, digits) }
    } else null
    val recentMatches = if (digits.isEmpty()) emptyList() else state.calls
        .filter { it.number.filter { c -> c.isDigit() }.contains(digits) }
        .take(4)
    val contactMatches = if (digits.isEmpty()) emptyList() else state.contacts
        .filter { person ->
            person.number.filter { it.isDigit() }.contains(digits) && person.name != known?.name
        }
        .take(3)

    fun append(text: String) {
        state.typeDigits(number + text)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Column(Modifier.fillMaxSize().background(QwenPalette.Bone)) {
        // Header
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dial", style = MaterialTheme.typography.displaySmall, color = QwenPalette.Ink)
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = QwenPalette.Ink,
                    modifier = Modifier.size(32.dp).clickable { onBack() }
                )
            }
        }
        Rule(heavy = true)

        // Number display
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChromeLabel(
                text = if (known != null) known.name else if (number.isEmpty()) "ENTER NUMBER" else "DIALLING",
                color = if (known != null) QwenPalette.Ink else QwenPalette.Muted,
                bold = true
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (number.isEmpty()) "—" else formatNumber(number),
                    fontFamily = FontFamily.Serif,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (number.isEmpty()) QwenPalette.Faint else QwenPalette.Ink,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                if (number.isNotEmpty()) {
                                    clipboard.setText(AnnotatedString(number))
                                    copied = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                        ),
                )
                Icon(
                    Icons.Outlined.Backspace,
                    contentDescription = "Delete",
                    tint = QwenPalette.Muted,
                    modifier = Modifier
                        .size(28.dp)
                        .combinedClickable(
                            onClick = {
                                if (number.isNotEmpty()) state.typeDigits(number.dropLast(1))
                            },
                            onLongClick = { state.typeDigits("") },
                        ),
                )
            }
        }
        Rule(heavy = false)

        if (copied) NoticeBar(text = "COPIED · LONG-PRESS A NUMBER TO COPY IT")

        if (blocked != null) {
            NoticeBar(
                text = "BLOCKED BY ${blocked.kind.name.lowercase()} RULE",
                actionLabel = "UNBLOCK",
                onClick = { state.removeNumber(blocked.value) },
                icon = Icons.Outlined.Shield
            )
        }

        // Suggestions scrollable area
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (recentMatches.isNotEmpty()) {
                ChromeLabel(
                    "RECENT CALLS",
                    color = QwenPalette.Faint,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                recentMatches.forEach { entry ->
                    QwenSuggestionRow(
                        title = entry.name ?: formatNumber(entry.number),
                        detail = if (entry.missed) "MISSED · ${relativeStamp(entry.date)}"
                        else "${entry.kind} · ${relativeStamp(entry.date)}",
                    ) { state.typeDigits(number + entry.number) }
                }
                Rule(indent = true)
            }
            if (contactMatches.isNotEmpty()) {
                ChromeLabel(
                    "CONTACTS",
                    color = QwenPalette.Faint,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
                contactMatches.forEach { person ->
                    QwenSuggestionRow(
                        title = person.name,
                        detail = formatNumber(person.number),
                    ) { state.typeDigits(number + person.number) }
                }
                Rule(indent = true)
            }
            if (lastPasted != null) {
                Spacer(Modifier.height(4.dp))
                ChromeLabel(
                    "PASTED · CLEANED TO DIGITS",
                    color = QwenPalette.Faint,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        if (!state.canPlaceCalls()) {
            NoticeBar(text = "NO TELEPHONY ON THIS DEVICE · A CALL CANNOT CONNECT")
        }

        // The Keypad: Large, sharp rectangles with serif numbers
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keyRows.forEach { rowKeys ->
                Row(
                    Modifier.fillMaxWidth().height(72.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKeys.forEach { (digit, letters) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .border(1.dp, QwenPalette.LineSoft)
                                .combinedClickable(
                                    onClick = { append(digit) },
                                    onLongClick = { append(if (digit == "0") "+" else digit) }
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                digit,
                                fontFamily = FontFamily.Serif,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Normal,
                                color = QwenPalette.Ink,
                            )
                            if (letters.isNotEmpty()) {
                                ChromeLabel(letters, color = QwenPalette.Faint)
                            }
                        }
                    }
                }
            }
        }

        // Call Button: Full width, heavy ink block
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            val canCall = number.isNotBlank()
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(if (canCall) QwenPalette.Ink else QwenPalette.Wash)
                    .clickable { if (canCall) state.call(number) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = if (canCall) QwenPalette.Paper else QwenPalette.Faint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    ChromeLabel(
                        "CALL",
                        color = if (canCall) QwenPalette.Paper else QwenPalette.Faint,
                        bold = true
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ArchitectButton(
                    label = "SAVE CONTACT",
                    onClick = { if (digits.isNotEmpty()) onNewContact(number) },
                    filled = false,
                    modifier = Modifier.weight(1f)
                )
                ArchitectButton(
                    label = "PASTE",
                    onClick = {
                        clipboard.getText()?.text?.let { pasted ->
                            lastPasted = pasted
                            state.typeDigits(
                                number + pasted.filter { it.isDigit() || it == '+' || it == '*' || it == '#' },
                            )
                        }
                    },
                    filled = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QwenSuggestionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = QwenPalette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        ChromeLabel(detail, color = QwenPalette.Faint)
    }
}

private val CallEntry.kind: String
    get() = when {
        missed -> "Missed"
        outgoing -> "Outgoing"
        else -> "Incoming"
    }
