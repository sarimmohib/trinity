package com.sarim.trinity.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.mms.MmsComposer
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoButton
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.SendButton
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.components.SimChip
import com.sarim.trinity.ui.components.SimSheet
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.smsCounter
import com.sarim.trinity.ui.theme.Palette
import java.util.Calendar

/**
 * A new conversation. Any number of recipients, chosen from the address book or
 * typed straight in; the body is the same composer the thread uses, so drafts,
 * counters and send rules behave identically.
 */
@Composable
fun NewMessageScreen(
    state: TrinityState,
    initialTo: String = "",
    initialBody: String = "",
    onBack: () -> Unit,
    onSent: (Long?, String) -> Unit,
) {
    var recipients by remember { mutableStateOf(listOfNotNull(initialTo.takeIf { it.isNotBlank() })) }
    var body by remember { mutableStateOf(initialBody) }
    var query by remember { mutableStateOf("") }
    var confirmDiscard by remember { mutableStateOf(false) }
    var simSheet by remember { mutableStateOf(false) }
    // True when the text is waiting on a line choice, because "ask every time" is on.
    var askSend by remember { mutableStateOf(false) }
    var mmsStatus by remember { mutableStateOf<String?>(null) }
    // Set once the user has picked both a date and a time; drives the confirm button.
    var scheduleAt by remember { mutableStateOf<Long?>(null) }
    var scheduleNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val mediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        MmsComposer.send(context, recipients, uri, body.ifBlank { null }, state.selectedSubscription) { error ->
            if (error == null) {
                body = ""
                mmsStatus = "PHOTO SENT"
                val first = recipients.firstOrNull()
                if (first != null) onSent(null, first)
            } else {
                mmsStatus = error
            }
        }
    }

    val needle = query.trim()
    val digits = needle.filter { it.isDigit() }
    // Every contact that matches, not the first six. A name query used to compare
    // against an empty digit string, which matched everyone, and the cap hid the rest.
    val matches = if (needle.isEmpty()) {
        emptyList()
    } else {
        state.contacts.filter { person ->
            person.name.contains(needle, ignoreCase = true) ||
                person.emails.any { it.contains(needle, ignoreCase = true) } ||
                person.allNumbers.any { it.number.contains(needle, ignoreCase = true) } ||
                (digits.length >= 2 && person.allNumbers.any { entry ->
                    entry.number.filter { c -> c.isDigit() }.contains(digits)
                })
        }
    }
    val canSend = recipients.isNotEmpty() && body.isNotBlank()

    fun send(subscriptionId: Int? = state.selectedSubscription) {
        if (!canSend) return
        val first = recipients.first()
        val text = body
        // One text per recipient; the first one's thread is opened afterwards.
        recipients.drop(1).forEach { state.sendMessage(it, text, subscriptionId) }
        state.sendMessage(first, text, subscriptionId) { threadId -> onSent(threadId, first) }
        body = ""
    }

    fun scheduleTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    fun scheduleDay(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
    }

    // The platform pickers need a themed Activity context, which Compose supplies here.
    // They are deliberately plain: no new dependency and no Material date-picker wiring.
    fun pickSchedule() {
        val now = Calendar.getInstance()
        val date = DatePickerDialog(
            context,
            { _, year, month, day ->
                val time = TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val picked = Calendar.getInstance().apply {
                            set(year, month, day, hour, minute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        scheduleAt = picked.timeInMillis
                        scheduleNote = null
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true, // 24-hour clock, like the rest of the app's stamps.
                )
                time.show()
            },
            now.get(Calendar.YEAR),
            now.get(Calendar.MONTH),
            now.get(Calendar.DAY_OF_MONTH),
        )
        date.datePicker.minDate = System.currentTimeMillis() - 1000L
        date.show()
    }

    fun commitSchedule() {
        val whenAt = scheduleAt ?: return
        if (!canSend) return
        val text = body
        recipients.forEach { state.scheduleMessage(it, text, whenAt) }
        body = ""
        scheduleAt = null
        scheduleNote = "SCHEDULED FOR ${scheduleTime(whenAt)}"
    }

    // Back key follows the same rule as the close arrow: ask before losing text.
    androidx.activity.compose.BackHandler(enabled = true) {
        if (recipients.isNotEmpty() || body.isNotBlank()) confirmDiscard = true else onBack()
    }

    fun addRecipient(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        if (recipients.none { it.filter { c -> c.isDigit() } == clean.filter { c -> c.isDigit() } }) {
            recipients = recipients + clean
        }
        query = ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Palette.Ink,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        if (recipients.isNotEmpty() || body.isNotBlank()) {
                            confirmDiscard = true
                        } else {
                            onBack()
                        }
                    },
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("New message", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                MonoLabel(
                    if (recipients.isEmpty()) {
                        "NO RECIPIENTS YET"
                    } else {
                        "${recipients.size} RECIPIENT${if (recipients.size == 1) "" else "S"}"
                    },
                    size = 8,
                    color = Palette.Faint,
                )
            }
        }
        Hairline()

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            MonoLabel("TO", size = 8, color = Palette.Faint)
            Spacer(Modifier.height(8.dp))
            if (recipients.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recipients.forEach { recipient ->
                        Row(
                            Modifier
                                .height(30.dp)
                                .background(Palette.Wash, RoundedCornerShape(15.dp))
                                .border(1.dp, Palette.Line, RoundedCornerShape(15.dp))
                                .clickable { recipients = recipients - recipient }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoLabel(formatNumber(recipient), color = Palette.Ink, size = 9)
                            Spacer(Modifier.width(8.dp))
                            MonoLabel("×", color = Palette.Faint, size = 9)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .border(1.dp, Palette.Line, RoundedCornerShape(6.dp))
                    .background(Palette.Paper, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 13.sp, color = Palette.Ink),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Name or number", fontSize = 12.sp, color = Palette.Faint)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (digits.length >= 3) {
                    Spacer(Modifier.width(8.dp))
                    MonoLabel(
                        "ADD",
                        color = Palette.Ink,
                        size = 8,
                        modifier = Modifier.clickable { addRecipient(digits) },
                    )
                }
            }
        }

        if (matches.isNotEmpty() || (digits.length >= 3 && matches.isEmpty())) {
            Hairline()
            LazyColumn(Modifier.heightIn(max = 260.dp)) {
                if (matches.isEmpty() && digits.length >= 3) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clickable { addRecipient(digits) }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MonoLabel("USE ${formatNumber(digits)}", color = Palette.Ink, size = 9)
                        }
                        Hairline()
                    }
                }
                items(matches.size) { index ->
                    val person = matches[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { addRecipient(person.number) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(person.initials, filled = false, size = 34)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                person.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Palette.Ink,
                            )
                            MonoLabel(formatNumber(person.number), color = Palette.Faint, size = 8)
                        }
                    }
                    Hairline()
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Hairline()
        // The body takes the rest of the screen so Send is pinned to the very bottom.
        Column(Modifier.weight(1f).padding(horizontal = 24.dp, vertical = 14.dp)) {
            BasicTextField(
                value = body,
                onValueChange = { body = it },
                textStyle = TextStyle(fontSize = 14.sp, color = Palette.Ink, lineHeight = 20.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp),
                decorationBox = { inner ->
                    Box {
                        if (body.isEmpty()) {
                            Text(
                                "Write a message",
                                fontSize = 13.sp,
                                color = Palette.Faint,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MonoLabel(
                    if (body.isBlank()) "NO TEXT YET" else "READY",
                    color = Palette.Faint,
                    size = 8,
                )
                MonoLabel(smsCounter(body), color = Palette.Faint, size = 8)
            }
            if (mmsStatus != null) {
                Spacer(Modifier.height(6.dp))
                MonoLabel(mmsStatus.orEmpty(), color = Palette.Faint, size = 8)
            }
            scheduleNote?.let { note ->
                Spacer(Modifier.height(6.dp))
                MonoLabel(note, color = Palette.Faint, size = 8)
            }
        }

        // A chosen time gets its own confirm row so it cannot be mistaken for an
        // immediate send. WorkManager is inexact; the thread will say so too.
        val at = scheduleAt
        if (at != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoButton(
                    label = "SCHEDULE ${scheduleTime(at)}",
                    enabled = canSend,
                    onClick = { commitSchedule() },
                )
                Spacer(Modifier.width(10.dp))
                MonoLabel(scheduleDay(at), color = Palette.Faint, size = 8)
                Spacer(Modifier.weight(1f))
                MonoLabel(
                    "CLEAR",
                    color = Palette.Faint,
                    size = 8,
                    modifier = Modifier.clickable { scheduleAt = null },
                )
            }
        }

        // Send sits with the thumb, and the SIM it will leave from sits beside it.
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    mediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = recipients.isNotEmpty(),
            ) {
                Icon(Icons.Outlined.Image, contentDescription = "Attach a photo")
            }
            IconButton(
                onClick = { pickSchedule() },
                enabled = recipients.isNotEmpty(),
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = "Schedule this message")
            }
            val simLabel = state.sendSimLabel()
            if (simLabel != null) {
                SimChip(simLabel) { simSheet = true }
                Spacer(Modifier.width(12.dp))
            }
            Spacer(Modifier.weight(1f))
            SendButton(
                enabled = canSend,
                onClick = {
                    // "Ask every time" means the line is chosen now, not in Settings.
                    if (state.askEachTime && state.simCards.isNotEmpty()) askSend = true else send()
                },
            )
        }
    }

    if (askSend) {
        InkSheet(
            title = "SEND FROM",
            actions = state.simCards.map { card ->
                SheetAction(
                    card.label,
                    if (card.subscriptionId == state.defaultSubscription) "DEFAULT" else null,
                ) {
                    askSend = false
                    send(card.subscriptionId)
                }
            },
            onDismiss = { askSend = false },
        )
    }

    if (simSheet) {
        SimSheet(
            cards = state.simCards,
            selected = state.selectedSubscription,
            defaultId = state.defaultSubscription,
            askEachTime = state.askEachTime,
            onPick = { state.selectSim(it) },
            onAsk = { state.askEveryTime() },
            onDismiss = { simSheet = false },
        )
    }

    if (confirmDiscard) {
        InkSheet(
            title = "DISCARD",
            actions = listOf(
                SheetAction("Discard this message", "NOT SENT") { onBack() },
                SheetAction("Keep writing") { },
            ),
            onDismiss = { confirmDiscard = false },
        )
    }
}
