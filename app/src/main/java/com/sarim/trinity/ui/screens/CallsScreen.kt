package com.sarim.trinity.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneMissed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.R
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.data.ContactChannels
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.ScreenHeader
import com.sarim.trinity.ui.components.Selection
import com.sarim.trinity.ui.components.SelectionAction
import com.sarim.trinity.ui.components.SelectionBar
import com.sarim.trinity.ui.components.SelectionMark
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.dayKey
import com.sarim.trinity.ui.dayLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.theme.Palette

/** A run of calls from the same number, collapsed into one row. */
private data class CallGroup(
    val entry: CallEntry,
    val count: Int,
    /** Every log row behind this line, so deleting the run deletes all of it. */
    val ids: List<Long> = listOf(entry.id),
) {
    val missed: Boolean get() = entry.missed
}

@Composable
fun CallsScreen(
    state: TrinityState,
    onOpenDialpad: () -> Unit,
    onOpenDetail: (CallEntry) -> Unit,
    onBlocking: () -> Unit,
    onSettings: () -> Unit,
) {
    // Re-read the phone's log every time this tab comes back into view, so a call
    // that just ended is already at the top.
    LaunchedEffect(Unit) { state.refresh() }

    val rules = state.blockStore.rules.value
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // WhatsApp is offered on every call row only where there is an app to take it.
    val whatsapp = remember { ContactChannels.installed(context) }
    var query by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf<String?>(null) }
    val selection = remember { Selection<CallGroup> { it.entry.id } }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    val blockedCount = state.calls.count { it.rule != null }
    val missedCount = state.calls.count { it.missed }
    val needle = query.trim()
    val needleDigits = needle.filter { it.isDigit() }
    val visible = state.calls
        .filter { rules.hideMatchedCalls || it.rule == null }
        .filter { entry ->
            // A name query must actually match the name: comparing against an empty
            // digit string matched every row, so the field looked broken.
            needle.isEmpty() ||
                entry.displayName.contains(needle, ignoreCase = true) ||
                (needleDigits.isNotEmpty() &&
                    entry.number.filter { it.isDigit() }.contains(needleDigits))
        }
    val groups = collapse(visible)
    val allGroups = groups.flatMap { it.second }
    val picked = allGroups.filter { selection.contains(it) }

    // Back drops the selection before it leaves the tab.
    androidx.activity.compose.BackHandler(enabled = selection.active) { selection.clear() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Calls",
            meta = "${plural(state.calls.size, "CALL")} · $missedCount MISSED · $blockedCount MATCHED RULES",
            actionLabel = "BLOCK ${rules.ruleCount}",
            onAction = onBlocking,
            onSettings = onSettings,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(46.dp)
                .glassBlock(Palette.Accents[2], 23.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel("SEARCH", size = 8, color = Palette.Faint)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Palette.Ink,
                ),
                cursorBrush = SolidColor(Palette.Ink),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) MonoLabel("name or number", size = 9, color = Palette.Faint)
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                MonoLabel("CLEAR", color = Palette.Ink, size = 8, modifier = Modifier.clickable { query = "" })
            }
        }

        if (copied != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Wash)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                MonoLabel("COPIED · ${copied}", color = Palette.Ink, size = 8)
            }
            Hairline()
        }

        if (groups.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    if (query.isBlank()) "No call history yet." else "No calls match \"$query\".",
                    color = Palette.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (query.isBlank()) "Calls show up here, newest first." else "Try a name or part of the number.",
                    color = Palette.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = if (selection.active) 132.dp else 96.dp,
                ),
            ) {
            groups.forEach { (day, items) ->
                item(key = "day-$day") {
                    DayHeader(
                        date = items.firstOrNull()?.entry?.date ?: 0L,
                        calls = items.sumOf { it.count },
                        numbers = items.size,
                    )
                }
                items(items.size) { index ->
                    val group = items[index]
                    CallRow(
                        group = group,
                        selecting = selection.active,
                        selected = selection.contains(group),
                        onClick = {
                            if (selection.active) selection.toggle(group)
                            else state.call(group.entry.number, group.entry.name)
                        },
                        onLongPress = { selection.start(group) },
                        onDetail = { onOpenDetail(group.entry) },
                        onWhatsApp = if (whatsapp) {
                            {
                                ContactChannels.chatIntent(group.entry.number)?.let { intent ->
                                    runCatching {
                                        context.startActivity(
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // The dial pad action floats over the glass rows, not on a bar of its own.
        // While calls are picked the selection bar sits in its place instead.
        if (selection.active) {
            SelectionBar(
                count = picked.size,
                actions = buildList {
                    if (picked.size == 1) {
                        add(SelectionAction("Info") { onOpenDetail(picked.first().entry) })
                    }
                    add(
                        SelectionAction("Copy") {
                            clipboard.setText(
                                AnnotatedString(picked.joinToString("\n") { it.entry.number }),
                            )
                            copied = if (picked.size == 1) {
                                formatNumber(picked.first().entry.number)
                            } else {
                                "${picked.size} NUMBERS"
                            }
                            selection.clear()
                        },
                    )
                    add(
                        SelectionAction("Block") {
                            picked.forEach { state.addRule(it.entry.number) }
                            selection.clear()
                        },
                    )
                    add(SelectionAction("Delete") { confirmBulkDelete = true })
                    if (picked.size < allGroups.size) {
                        add(SelectionAction("All") { selection.pickAll(allGroups) })
                    }
                },
                onClear = { selection.clear() },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
                    .height(52.dp)
                    .glassBlock(
                        androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        26.dp,
                        3f,
                    )
                    .clickable { onOpenDialpad() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(9.dp))
                MonoLabel(
                    "DIAL PAD",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 10,
                )
            }
        }
        }
    }

    if (confirmBulkDelete && picked.isNotEmpty()) {
        val ids = picked.flatMap { it.ids }
        InkSheet(
            title = if (ids.size == 1) "DELETE CALL" else "DELETE ${ids.size} CALLS",
            actions = listOf(
                SheetAction("Delete from the log", "CANNOT BE UNDONE") {
                    confirmBulkDelete = false
                    state.deleteCalls(ids) { selection.clear() }
                },
                SheetAction("Keep them") { },
            ),
            onDismiss = { confirmBulkDelete = false },
        )
    }
}

@Composable
private fun DayHeader(date: Long, calls: Int, numbers: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(dayLabel(date), color = Palette.Ink, size = 8)
        MonoLabel(
            if (numbers == calls) plural(calls, "CALL")
            else "${plural(calls, "CALL")} · ${plural(numbers, "NUMBER")}",
            color = Palette.Faint,
            size = 8,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CallRow(
    group: CallGroup,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDetail: () -> Unit,
    onWhatsApp: (() -> Unit)?,
) {
    val entry = group.entry
    val tint = Palette.Accents[
        (entry.initials.hashCode() and 0x7FFFFFFF) % Palette.Accents.size
    ]
    // One glass block per call, same as messages.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(72.dp)
            .glassBlock(tint, 22.dp, if (selected) 2.4f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(entry.initials, filled = entry.missed)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name ?: formatNumber(entry.number),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.rule != null) Palette.Muted else Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.rule != null) {
                    Spacer(Modifier.width(7.dp))
                    MonoLabel("BLOCKED", color = Palette.Ink, size = 7)
                }
                entry.simLabel?.let { tag ->
                    Spacer(Modifier.width(7.dp))
                    MonoLabel(tag, color = Palette.Faint, size = 7)
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        entry.missed -> Icons.Outlined.PhoneMissed
                        entry.outgoing -> Icons.Outlined.CallMade
                        else -> Icons.Outlined.CallReceived
                    },
                    contentDescription = when {
                        entry.missed -> "Missed call"
                        entry.outgoing -> "Outgoing call"
                        else -> "Incoming call"
                    },
                    tint = Palette.Faint,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    if (group.count > 1) "${entry.kind} · ${group.count} calls" else entry.detail,
                    fontSize = 11.sp,
                    color = Palette.Muted,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            MonoLabel(relativeStamp(entry.date), color = Palette.Faint, size = 9)
            if (selecting) {
                Spacer(Modifier.height(6.dp))
                SelectionMark(selected)
            } else {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MonoLabel(
                        "INFO",
                        color = Palette.Ink,
                        size = 7,
                        modifier = Modifier
                            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                            .clickable { onDetail() }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                    if (onWhatsApp != null) {
                        // The mark itself, not the letters: a call row should read as
                        // WhatsApp at a glance.
                        Box(
                            Modifier
                                .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                                .clickable { onWhatsApp() }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_whatsapp),
                                contentDescription = "Message on WhatsApp",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CallEntry.kind: String
    get() = when {
        missed -> "Missed"
        outgoing -> "Outgoing"
        else -> "Incoming"
    }

/** Newest-first runs of the same number become one row, keeping the latest call. */
private fun collapse(entries: List<CallEntry>): List<Pair<Long, List<CallGroup>>> {
    val byDay = LinkedHashMap<Long, MutableList<CallGroup>>()
    for (entry in entries) {
        val day = dayKey(entry.date)
        val bucket = byDay.getOrPut(day) { mutableListOf() }
        val last = bucket.lastOrNull()
        if (last != null && sameTarget(last.entry, entry)) {
            bucket[bucket.lastIndex] = last.copy(
                count = last.count + 1,
                ids = last.ids + entry.id,
            )
        } else {
            bucket += CallGroup(entry, 1)
        }
    }
    return byDay.entries.map { it.key to it.value.toList() }
}

/** "1 CALL" / "2 CALLS", so the header never reads "1 calls". */
private fun plural(count: Int, word: String): String =
    if (count == 1) "$count $word" else "$count ${word}S"

private fun sameTarget(a: CallEntry, b: CallEntry): Boolean {
    val digitsA = a.number.filter { it.isDigit() }
    val digitsB = b.number.filter { it.isDigit() }
    return digitsA.isNotEmpty() && digitsA == digitsB
}
