package com.sarim.trinity.ui.screens
import com.sarim.trinity.ui.components.glassBlock

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
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
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.theme.Palette

private val keyRows = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

/** Height of one key row. Never below 64 dp, so a key is always a legal target. */
private val keyRowHeight = 74.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialpadScreen(
    state: TrinityState,
    initialNumber: String = "",
    onBack: () -> Unit,
    onNewContact: (String) -> Unit,
) {
    // The digits live in TrinityState so leaving the pad and coming back keeps them.
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
    val emergency = BlockMatcher.isEmergency(number)

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

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("New call", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
            Box(
                Modifier
                    .size(34.dp)
                    .glassBlock(Palette.Accents[0], 17.dp, 1.2f)
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

        if (copied) Notice("COPIED · LONG-PRESS A NUMBER TO COPY IT")

        if (blocked != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Wash)
                    .clickable { state.removeNumber(blocked.value) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = Palette.Ink,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Blocked by ${blocked.kind.name.lowercase()} rule",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                MonoLabel("UNBLOCK", color = Palette.Ink, size = 8)
            }
        }

        // Everything between the field and the fixed keypad. Suggestions scroll here;
        // when there is nothing to suggest the space simply stays quiet.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (recentMatches.isNotEmpty()) {
                SectionLabel("RECENT CALLS")
                recentMatches.forEach { entry ->
                    SuggestionRow(
                        title = entry.name ?: formatNumber(entry.number),
                        detail = if (entry.missed) "MISSED · ${relativeStamp(entry.date)}"
                        else "${entry.kind} · ${relativeStamp(entry.date)}",
                    ) { state.typeDigits(number + entry.number) }
                }
            }
            if (contactMatches.isNotEmpty()) {
                SectionLabel("CONTACTS")
                contactMatches.forEach { person ->
                    SuggestionRow(
                        title = person.name,
                        detail = formatNumber(person.number),
                    ) { state.typeDigits(number + person.number) }
                }
            }
            if (lastPasted != null) {
                Spacer(Modifier.height(4.dp))
                MonoLabel(
                    "PASTED · CLEANED TO DIGITS",
                    size = 7,
                    color = Palette.Faint,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }

        Hairline()

        // Who you are about to call, and the digits with their backspace — right
        // above the keypad, where the thumb already is.
        // Fixed heights: the contact name, the warning and the digits all swap inside
        // the same 72 dp block, so the keypad below never moves mid-typing.
        Column(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.height(20.dp), contentAlignment = Alignment.CenterStart) {
                if (known != null) {
                    Text(
                        known.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    MonoLabel(
                        when {
                            emergency -> "EMERGENCY · RULES DO NOT APPLY"
                            number.isEmpty() -> "TYPE OR PASTE A NUMBER"
                            else -> "DIALLING"
                        },
                        size = 8,
                        color = Palette.Faint,
                    )
                }
            }
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (number.isEmpty()) "—" else formatNumber(number),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (number.isEmpty()) Palette.Faint else Palette.Ink,
                    maxLines = 1,
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
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(26.dp)
                        .combinedClickable(
                            onClick = {
                                if (number.isNotEmpty()) state.typeDigits(number.dropLast(1))
                            },
                            onLongClick = { state.typeDigits("") },
                        ),
                )
            }
        }
        Hairline()

        if (!state.canPlaceCalls()) {
            Notice("NO TELEPHONY ON THIS DEVICE · A CALL CANNOT CONNECT")
        }

        // The keypad is fixed, not weighted: keys keep one size whether or not
        // suggestions are showing.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            keyRows.forEach { rowKeys ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(keyRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowKeys.forEach { (digit, letters) ->
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .padding(2.dp)
                                .glassBlock(
                                    Palette.Accents[(digit[0].code) % Palette.Accents.size],
                                    18.dp,
                                    0.8f,
                                )
                                .combinedClickable(
                                    onClick = { append(digit) },
                                    onLongClick = { append(if (digit == "0") "+" else digit) },
                                ),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                digit,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = Palette.Ink,
                            )
                            if (letters.isNotEmpty()) {
                                MonoLabel(letters, size = 7, color = Palette.Faint)
                            }
                        }
                    }
                }
            }
        }

        val canCall = number.isNotBlank()
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 16.dp)) {
            // Nothing here grows or shrinks while typing: a strip used to appear at the
            // fifth digit and lift the keypad out from under the thumb.
            // Dual-SIM: pick the line before dialling. Hidden entirely on one SIM.
            if (state.simCards.size >= 2) {
                val chosen = state.selectedSubscription ?: state.defaultSubscription
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.simCards.forEach { card ->
                        val active = chosen == card.subscriptionId
                        Row(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .glassBlock(
                                    if (active) Palette.Green else Palette.Paper,
                                    17.dp,
                                    if (active) 3f else 0.5f,
                                )
                                .clickable {
                                    state.selectSim(if (active) null else card.subscriptionId)
                                },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoLabel(
                                card.tag,
                                color = if (active) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Ink,
                                size = 8,
                            )
                            card.carrier?.takeIf { it.isNotBlank() }?.let { carrier ->
                                Spacer(Modifier.width(6.dp))
                                MonoLabel(
                                    carrier.uppercase(),
                                    color = if (active) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Faint,
                                    size = 7,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .glassBlock(
                        if (canCall) Palette.Green else Palette.Paper,
                        27.dp,
                        if (canCall) 3f else 0.5f,
                    )
                    .clickable { if (canCall) state.call(number) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = if (canCall) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Faint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                MonoLabel(
                    "CALL",
                    color = if (canCall) androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer else Palette.Faint,
                    size = 11,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedAction("SAVE CONTACT", Icons.Outlined.PersonAdd, Modifier.weight(1f)) {
                    if (digits.isNotEmpty()) onNewContact(number)
                }
                OutlinedAction("PASTE", Icons.Outlined.ContentPaste, Modifier.weight(1f)) {
                    clipboard.getText()?.text?.let { pasted ->
                        lastPasted = pasted
                        state.typeDigits(
                            number + pasted.filter { it.isDigit() || it == '+' || it == '*' || it == '#' },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 4.dp),
    ) {
        MonoLabel(text, color = Palette.Faint, size = 7)
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Wash)
            .padding(horizontal = 24.dp, vertical = 7.dp),
    ) {
        MonoLabel(text, color = Palette.Ink, size = 8)
    }
}

@Composable
private fun SuggestionRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable { onClick() }
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        MonoLabel(detail, color = Palette.Faint, size = 8)
    }
}

@Composable
private fun OutlinedAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(40.dp)
            .glassBlock(Palette.Accents[2], 20.dp, 1.2f)
            .clickable { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        MonoLabel(label, color = Palette.Ink, size = 8)
    }
}

private val CallEntry.kind: String
    get() = when {
        missed -> "Missed"
        outgoing -> "Outgoing"
        else -> "Incoming"
    }
