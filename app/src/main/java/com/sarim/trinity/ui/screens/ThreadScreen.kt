package com.sarim.trinity.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.Message
import com.sarim.trinity.data.MessageState
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.absoluteStamp
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.dayKey
import com.sarim.trinity.ui.dayLabel
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.smsCounter
import com.sarim.trinity.ui.components.SendButton
import com.sarim.trinity.ui.components.SimChip
import com.sarim.trinity.ui.components.SimSheet
import com.sarim.trinity.ui.theme.Palette
import kotlinx.coroutines.launch

/** A row in the thread: either a day rule or one message. */
private sealed interface ThreadRow {
    data class Day(val label: String, val key: Long) : ThreadRow
    data class Msg(val message: Message) : ThreadRow
}

@Composable
fun ThreadScreen(
    state: TrinityState,
    conversation: Conversation,
    onBack: () -> Unit,
    onBlocking: () -> Unit,
    onNewContact: (String) -> Unit,
    onForward: (String) -> Unit,
) {
    var draft by remember(conversation.threadId) { mutableStateOf(state.draftFor(conversation.threadId)) }
    var searching by remember { mutableStateOf(false) }
    var simSheet by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // A text waiting on a line choice, when "ask every time" is on.
    var askSend by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<Message?>(null) }
    var infoFor by remember { mutableStateOf<Message?>(null) }
    var confirmDeleteThread by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // Opening the thread marks it read and clears its notification.
    LaunchedEffect(conversation.threadId) { state.openThread(conversation) }

    val all = state.threadMessages(conversation.address)
    val shown = if (query.isBlank()) {
        all
    } else {
        all.filter {
            it.body.contains(query, ignoreCase = true) ||
                relativeStamp(it.date).contains(query, ignoreCase = true)
        }
    }
    val rows = remember(shown, query) {
        val out = ArrayList<ThreadRow>()
        var lastDay = Long.MIN_VALUE
        shown.forEach { message ->
            val key = dayKey(message.date)
            if (key != lastDay) {
                out += ThreadRow.Day(dayLabel(message.date), key)
                lastDay = key
            }
            out += ThreadRow.Msg(message)
        }
        out
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var stickToBottom by remember { mutableStateOf(true) }
    // Follow the newest message only while the user is already at the bottom, so
    // an arriving text never yanks a reader out of history.
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { !listState.canScrollForward }
            .collect { stickToBottom = it }
    }
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty() && stickToBottom) {
            listState.scrollToItem(rows.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ThreadHeader(
            conversation = conversation,
            muted = conversation.muted,
            searching = searching,
            onBack = {
                state.setDraft(conversation.threadId, draft)
                state.closeThread()
                onBack()
            },
            onSearch = { searching = !searching; if (!searching) query = "" },
            onMenu = { menuOpen = true },
            onBlocking = onBlocking,
        )

        if (searching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Bone)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Palette.Faint,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = Palette.Ink,
                        fontFamily = FontFamily.Monospace,
                    ),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("Search this conversation", fontSize = 12.sp, color = Palette.Faint)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                MonoLabel(
                    if (query.isBlank()) "TYPE" else "${shown.size} FOUND",
                    color = Palette.Faint,
                    size = 8,
                )
            }
            Hairline()
        }

        Box(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                Box(Modifier.padding(24.dp)) {
                    Text(
                        if (query.isNotBlank()) {
                            "Nothing in this conversation matches \"$query\"."
                        } else {
                            "No messages yet. Type below to start the conversation."
                        },
                        fontSize = 12.sp,
                        color = Palette.Muted,
                        lineHeight = 18.sp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.Bone),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(rows.size) { index ->
                        when (val row = rows[index]) {
                            is ThreadRow.Day -> DayRule(row.label)
                            is ThreadRow.Msg -> {
                                val message = row.message
                                val hidden = message.rule != null && !state.revealed.contains(message.id)
                                if (hidden) {
                                    HiddenMessageCard(
                                        message = message,
                                        onReveal = { state.reveal(message.id) },
                                        onAllow = { state.allowSender(conversation) },
                                    )
                                } else {
                                    MessageBubble(
                                        message = message,
                                        query = query,
                                        onLongPress = { messageMenu = message },
                                    )
                                    if (message.state == MessageState.FAILED) {
                                        FailedNote(
                                            reason = state.pending
                                                .firstOrNull { it.localId == -message.id }
                                                ?.reason,
                                            onRetry = {
                                                state.pending
                                                    .firstOrNull { it.localId == -message.id }
                                                    ?.let { state.retryPending(it) }
                                            },
                                            onDismiss = {
                                                state.pending
                                                    .firstOrNull { it.localId == -message.id }
                                                    ?.let { state.dismissPending(it) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val scrolledUp by remember { derivedStateOf { listState.canScrollForward } }
            if (scrolledUp && rows.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(18.dp)
                        .background(Palette.Ink, RoundedCornerShape(15.dp))
                        .clickable {
                            scope.launch {
                                listState.animateScrollToItem(rows.lastIndex)
                                stickToBottom = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel("LATEST", color = Palette.Paper, size = 8)
                }
            }
        }

        Composer(
            conversation = conversation,
            onCall = { state.call(conversation.address, conversation.name) },
            simLabel = state.sendSimLabel(),
            onSimClick = { simSheet = true },
            draft = draft,
            onDraftChange = {
                draft = it
                state.setDraft(conversation.threadId, it)
            },
            onSend = {
                val text = draft.trim()
                if (text.isNotBlank()) {
                    fun deliver(subscriptionId: Int? = state.selectedSubscription) {
                        state.sendMessage(conversation.address, text, subscriptionId)
                        draft = ""
                        state.setDraft(conversation.threadId, "")
                        stickToBottom = true
                    }
                    // "Ask every time" means exactly that: the line is chosen first.
                    if (state.askEachTime && state.simCards.isNotEmpty()) askSend = text
                    else deliver()
                }
            },
        )
    }

    askSend?.let { text ->
        InkSheet(
            title = "SEND FROM",
            actions = state.simCards.map { card ->
                SheetAction(
                    card.label,
                    if (card.subscriptionId == state.defaultSubscription) "DEFAULT" else null,
                ) {
                    askSend = null
                    state.sendMessage(conversation.address, text, card.subscriptionId)
                    draft = ""
                    state.setDraft(conversation.threadId, "")
                    stickToBottom = true
                }
            },
            onDismiss = { askSend = null },
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

    if (menuOpen) {
        InkSheet(
            title = (conversation.name ?: conversation.shownNumber).uppercase(),
            actions = listOfNotNull(
                SheetAction(if (conversation.muted) "Unmute" else "Mute") {
                    state.toggleMute(conversation.threadId)
                },
                SheetAction("Mark unread") { state.markUnread(conversation.threadId) },
                SheetAction("Search in conversation") { searching = true },
                if (conversation.name == null) {
                    SheetAction("Add to contacts") { onNewContact(conversation.address) }
                } else {
                    null
                },
                SheetAction("Delete conversation") { confirmDeleteThread = true },
            ),
            onDismiss = { menuOpen = false },
        )
    }

    if (confirmDeleteThread) {
        InkSheet(
            title = "DELETE CONVERSATION",
            actions = listOf(
                SheetAction("Delete every message", "CANNOT BE UNDONE") {
                    state.deleteThread(conversation.threadId) { onBack() }
                },
                SheetAction("Keep it") { },
            ),
            onDismiss = { confirmDeleteThread = false },
        )
    }

    messageMenu?.let { message ->
        InkSheet(
            title = "MESSAGE",
            actions = buildList {
                add(SheetAction("Copy text") { clipboard.setText(AnnotatedString(message.body)) })
                add(SheetAction("Forward") { onForward(message.body) })
                add(SheetAction("Details") { infoFor = message })
                if (!message.isPending) {
                    add(SheetAction("Delete message") { state.deleteMessage(message.id) })
                }
            },
            onDismiss = { messageMenu = null },
        )
    }

    infoFor?.let { message ->
        InkSheet(
            title = "DETAILS",
            actions = listOf(
                SheetAction(
                    "Delivered",
                    absoluteStamp(message.date).uppercase(),
                ) { },
                SheetAction(
                    "Direction",
                    if (message.outgoing) "OUTGOING" else "INCOMING",
                ) { },
                SheetAction("State", message.state.name) { },
                SheetAction("Stored as", "MESSAGE #${message.id}") { },
                SheetAction("Close") { },
            ),
            onDismiss = { infoFor = null },
        )
    }
}

@Composable
private fun ThreadHeader(
    conversation: Conversation,
    muted: Boolean,
    searching: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    onBlocking: () -> Unit,
) {
    Column {
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
                    .clickable { onBack() },
            )
            Spacer(Modifier.width(12.dp))
            Avatar(conversation.initials, filled = false, size = 38)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.name ?: conversation.shownNumber,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Ink,
                )
                val meta = buildString {
                    if (conversation.name != null) {
                        append(conversation.shownNumber)
                        append(" · ")
                    }
                    append(if (conversation.rule != null) "HELD SENDER" else "SMS")
                    if (muted) append(" · MUTED")
                }
                MonoLabel(meta, size = 8, color = Palette.Faint)
            }
            HeaderIcon(Icons.Outlined.Search, "Search", searching, onSearch)
            Spacer(Modifier.width(14.dp))
            HeaderIcon(Icons.Outlined.Shield, "Blocking", false, onBlocking)
            Spacer(Modifier.width(14.dp))
            HeaderIcon(Icons.Outlined.MoreHoriz, "More", false, onMenu)
        }
        Hairline()
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        contentDescription = label,
        tint = if (active) Palette.Ink else Palette.Muted,
        modifier = Modifier
            .size(19.dp)
            .clickable { onClick() },
    )
}

@Composable
private fun DayRule(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(Palette.LineSoft),
        )
        MonoLabel(label, color = Palette.Faint, size = 8, modifier = Modifier.padding(horizontal = 10.dp))
        Box(
            Modifier
                .height(1.dp)
                .weight(1f)
                .background(Palette.LineSoft),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: Message, query: String, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .width(272.dp)
                .background(
                    if (message.outgoing) Palette.Ink else Palette.Paper,
                    RoundedCornerShape(12.dp),
                )
                .border(
                    1.dp,
                    if (message.outgoing) Palette.Ink else Palette.Line,
                    RoundedCornerShape(12.dp),
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Text(
                highlight(message.body.ifBlank { "(no text)" }, query, message.outgoing),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = if (message.outgoing) Palette.Paper else Palette.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(relativeStamp(message.date))
                    when (message.state) {
                        MessageState.SENDING -> append(" · SENDING")
                        MessageState.FAILED -> append(" · NOT SENT")
                        else -> Unit
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = if (message.outgoing) Palette.Wash else Palette.Faint,
            )
        }
    }
}

/** A search hit reads as inverted ink — no colour, still unmistakable. */
@Composable
private fun highlight(text: String, query: String, outgoing: Boolean): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val found = text.indexOf(query, index, ignoreCase = true)
            if (found < 0) {
                append(text.substring(index))
                break
            }
            append(text.substring(index, found))
            withStyle(
                SpanStyle(
                    background = if (outgoing) Palette.Paper else Palette.Ink,
                    color = if (outgoing) Palette.Ink else Palette.Paper,
                ),
            ) {
                append(text.substring(found, found + query.length))
            }
            index = found + query.length
        }
    }
}

@Composable
private fun FailedNote(reason: String?, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("FAILED${reason?.let { " · $it" } ?: ""}", color = Palette.Muted, size = 8)
        Spacer(Modifier.width(10.dp))
        MonoLabel("RETRY", color = Palette.Ink, size = 8, modifier = Modifier.clickable { onRetry() })
        Spacer(Modifier.width(10.dp))
        MonoLabel("DISCARD", color = Palette.Faint, size = 8, modifier = Modifier.clickable { onDismiss() })
    }
}

@Composable
private fun Composer(
    conversation: Conversation,
    onCall: () -> Unit,
    simLabel: String?,
    onSimClick: () -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = draft.isNotBlank()
    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Calling from a thread is a thumb distance, not a trip to the top edge.
            Box(
                Modifier
                    .size(42.dp)
                    .border(1.dp, Palette.Line, RoundedCornerShape(21.dp))
                    .background(Palette.Paper, RoundedCornerShape(21.dp))
                    .clickable { onCall() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = "Call",
                    tint = Palette.Ink,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (simLabel != null) {
                Spacer(Modifier.width(8.dp))
                SimChip(simLabel) { onSimClick() }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .border(1.dp, Palette.Line, RoundedCornerShape(21.dp))
                    .background(Palette.Bone, RoundedCornerShape(21.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    textStyle = TextStyle(fontSize = 13.sp, color = Palette.Ink, lineHeight = 18.sp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    "Message ${conversation.name ?: conversation.shownNumber}",
                                    fontSize = 12.sp,
                                    color = Palette.Faint,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            SendButton(enabled = canSend, onClick = onSend)
        }
        if (draft.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.weight(1f))
                MonoLabel(smsCounter(draft), color = Palette.Faint, size = 7)
            }
        }
    }
}

@Composable
private fun HiddenMessageCard(
    message: Message,
    onReveal: () -> Unit,
    onAllow: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Wash, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.Ink, RoundedCornerShape(10.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = Palette.Ink,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
                MonoLabel("HIDDEN BY ${message.rule?.label ?: "RULE"}", color = Palette.Ink, size = 8)
            }
            MonoLabel(relativeStamp(message.date), color = Palette.Muted, size = 8)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Matched: \"${message.rule?.value.orEmpty()}\"",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Palette.Muted,
        )
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SheetButton("SHOW ONCE") { onReveal() }
            SheetButton("ALLOW SENDER") { onAllow() }
        }
    }
}

@Composable
private fun SheetButton(label: String, onClick: () -> Unit) {
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
