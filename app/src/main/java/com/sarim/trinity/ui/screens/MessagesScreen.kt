package com.sarim.trinity.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.RuleHit
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.BottomAction
import com.sarim.trinity.ui.components.EmptyNote
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.Selection
import com.sarim.trinity.ui.components.SelectionAction
import com.sarim.trinity.ui.components.SelectionBar
import com.sarim.trinity.ui.components.SelectionMark
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.components.uiLabel
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.theme.Palette

@Composable
fun MessagesScreen(
    state: TrinityState,
    onOpenThread: (Conversation) -> Unit,
    onNewMessage: () -> Unit,
    onNewContact: (String) -> Unit,
    onBlocking: () -> Unit,
    onReview: () -> Unit,
    onSettings: () -> Unit,
) {
    val rules = state.blockStore.rules.value
    val hidden = state.conversations.filter {
        it.rule?.kind == RuleHit.Kind.NAME || it.rule?.kind == RuleHit.Kind.NUMBER
    }
    val visible = state.conversations.filter {
        it.rule == null || it.rule.kind == RuleHit.Kind.KEYWORD
    }
    val hiddenCount = hidden.size + state.blockedMessages.size

    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Long press picks rows; tapping then adds or drops them instead of opening.
    val selection = remember { Selection<Conversation> { it.threadId } }
    val picked = visible.filter { selection.contains(it) }
    var confirmBulkDelete by remember { mutableStateOf(false) }

    // The banner walks every route to the SMS role, and lands on the default-apps
    // page when the device's own role dialog closes itself on arrival.
    val askForSmsRole = com.sarim.trinity.ui.components.rememberRoleAsker(state) { _ -> }

    // Coming back to the tab always re-reads the provider: new texts, new counts.
    LaunchedEffect(Unit) { state.refresh() }

    // Back drops the selection before it leaves the tab.
    androidx.activity.compose.BackHandler(enabled = selection.active) { selection.clear() }

    Column(Modifier.fillMaxSize()) {
        MessagesHeader(
            conversationCount = visible.size,
            unread = state.unreadTotal,
            filtered = hiddenCount,
            heldCount = state.held.size,
            ruleCount = rules.ruleCount,
            onBlocking = onBlocking,
            onSettings = onSettings,
        )

        if (!state.isDefaultSmsApp()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Wash)
                    .clickable { askForSmsRole(TrinityState.DefaultApp.Sms) }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Blocking can't stop delivery yet",
                    fontSize = 11.sp,
                    color = Palette.Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                MonoLabel(uiLabel("MAKE DEFAULT"), color = Palette.Ink, size = 8)
            }
            Hairline()
        }

        if (hiddenCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Palette.Wash)
                    .clickable { onReview() }
                    .padding(horizontal = 24.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.FilterAlt,
                    contentDescription = null,
                    tint = Palette.Ink,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "$hiddenCount hidden",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Ink,
                    modifier = Modifier.weight(1f),
                )
                MonoLabel(uiLabel("REVIEW"), color = Palette.Ink, size = 8)
            }
            Hairline()
        }

        // The list takes the room left over, so the primary action below it is
        // always at the bottom of the screen — even when there is nothing to list.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.smsProviderMissing -> EmptyNote(
                    "This device has no SMS store. Nothing to read here.",
                )

                visible.isEmpty() -> EmptyNote("No conversations yet.")

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    // Room for the bar while rows are picked, for the button otherwise.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        bottom = if (selection.active) 128.dp else 92.dp,
                    ),
                ) {
                    items(visible, key = { it.threadId }) { conversation ->
                        SwipeableConversationRow(
                            conversation = conversation,
                            selecting = selection.active,
                            selected = selection.contains(conversation),
                            onOpen = {
                                if (selection.active) selection.toggle(conversation)
                                else onOpenThread(conversation)
                            },
                            onLongPress = { selection.start(conversation) },
                            onSwipeDelete = { confirmDelete = conversation },
                        )
                        if (!Palette.isExpressive) Hairline()
                    }
                }
            }

            // It floats over the list: rows keep scrolling under the glass, so
            // nothing sits on a solid strip of its own. While rows are picked the
            // bar above takes its place, with what can be done to them.
            if (selection.active) {
                SelectionBar(
                    count = picked.size,
                    actions = buildList {
                        if (picked.size == 1) {
                            add(SelectionAction("Actions") { menuFor = picked.first() })
                        }
                        add(
                            SelectionAction("Read") {
                                state.markThreadsRead(picked.map { it.threadId })
                                selection.clear()
                            },
                        )
                        val anyUnmuted = picked.any { !it.muted }
                        add(
                            SelectionAction(if (anyUnmuted) "Mute" else "Unmute") {
                                state.setThreadsMuted(picked.map { it.threadId }, anyUnmuted)
                                selection.clear()
                            },
                        )
                        add(SelectionAction("Delete") { confirmBulkDelete = true })
                        if (picked.size < visible.size) {
                            add(SelectionAction("All") { selection.pickAll(visible) })
                        }
                    },
                    onClear = { selection.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                BottomAction(
                    label = "NEW MESSAGE",
                    icon = Icons.Outlined.Add,
                    onClick = onNewMessage,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    menuFor?.let { conversation ->
        InkSheet(
            title = if (conversation.name != null) {
                conversation.name!!.uppercase()
            } else {
                conversation.shownNumber.uppercase()
            },
            actions = buildList {
                if (conversation.unread > 0) {
                    add(SheetAction("Mark read") { state.markRead(conversation.threadId) })
                } else {
                    add(SheetAction("Mark unread") { state.markUnread(conversation.threadId) })
                }
                add(
                    SheetAction(if (conversation.muted) "Unmute" else "Mute") {
                        state.toggleMute(conversation.threadId)
                    },
                )
                add(SheetAction("Copy number") { clipboard.setText(AnnotatedString(conversation.address)) })
                if (conversation.name == null) {
                    add(SheetAction("Add to contacts") { onNewContact(conversation.address) })
                }
                add(SheetAction("Call") { state.call(conversation.address, conversation.name) })
                add(SheetAction("Block sender", "NUMBER RULE") { state.addRule(conversation.address) })
                add(SheetAction("Delete conversation") { confirmDelete = conversation })
            },
            onDismiss = { menuFor = null },
        )
    }

    confirmDelete?.let { conversation ->
        InkSheet(
            title = "DELETE CONVERSATION",
            actions = listOf(
                SheetAction(
                    "Delete every message",
                    "CANNOT BE UNDONE",
                ) { state.deleteThread(conversation.threadId) },
                SheetAction("Keep it") { },
            ),
            onDismiss = { confirmDelete = null },
        )
    }

    if (confirmBulkDelete && picked.isNotEmpty()) {
        val threads = picked.map { it.threadId }
        InkSheet(
            title = if (threads.size == 1) "DELETE CONVERSATION" else "DELETE ${threads.size} CONVERSATIONS",
            actions = listOf(
                SheetAction("Delete every message", "CANNOT BE UNDONE") {
                    confirmBulkDelete = false
                    state.deleteThreads(threads) { selection.clear() }
                },
                SheetAction("Keep them") { },
            ),
            onDismiss = { confirmBulkDelete = false },
        )
    }
}

@Composable
private fun MessagesHeader(
    conversationCount: Int,
    unread: Int,
    filtered: Int,
    heldCount: Int,
    ruleCount: Int,
    onBlocking: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    "Messages",
                    style = MaterialTheme.typography.displaySmall,
                    color = Palette.Ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BoxAction("BLOCK $ruleCount", onBlocking)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                            .clickable { onSettings() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Palette.Ink,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoLabel(uiLabel("$conversationCount CONVERSATIONS"))
                // Unread is the one thing worth a second colour in the paper look.
                if (unread > 0) MonoLabel(uiLabel(" · $unread UNREAD"), color = Palette.Blue)
                MonoLabel(uiLabel(" · $filtered FILTERED"))
                if (heldCount > 0) MonoLabel(uiLabel(" · $heldCount HELD"))
            }
        }
        Hairline()
    }
}

@Composable
private fun BoxAction(label: String, onClick: () -> Unit, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .background(if (filled) Palette.Ink else Palette.Paper, RoundedCornerShape(3.dp))
            .border(1.dp, if (filled) Palette.Ink else Palette.Line, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(uiLabel(label), color = if (filled) Palette.Paper else Palette.Ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableConversationRow(
    conversation: Conversation,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeDelete: () -> Unit,
) {
    // While rows are being picked a sideways drag would fight the selection, so
    // the row is drawn without the swipe wrapper for the duration.
    if (selecting) {
        ConversationRow(conversation, selected, true, onOpen, onLongPress)
        return
    }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                false // the row springs back: deleting is confirmed on the sheet
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            // Only while the row is really being dragged: a see-through row would
            // otherwise show DELETE underneath it at rest.
            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(Palette.Wash)
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel("DELETE", color = Palette.Ink, size = 9)
                }
            }
        },
    ) {
        ConversationRow(conversation, false, false, onOpen, onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    // Expressive gives every conversation its own glass block, so the list is a stack
    // of pieces on the page instead of one solid slab over it.
    val expressive = Palette.isExpressive
    val tint = Palette.Accents[
        (conversation.initials.hashCode() and 0x7FFFFFFF) % Palette.Accents.size
    ]
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (expressive) {
                    Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .height(72.dp)
                        .glassBlock(tint, 22.dp, if (selected) 2.4f else 1f)
                } else {
                    Modifier.height(78.dp).background(Palette.Paper)
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = if (expressive) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.initials, filled = conversation.unread > 0)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.name ?: conversation.shownNumber,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.rule?.kind == RuleHit.Kind.KEYWORD) {
                    Spacer(Modifier.width(7.dp))
                    MonoLabel("KEYWORD", color = Palette.Ink, size = 7)
                }
                if (conversation.muted) {
                    Spacer(Modifier.width(7.dp))
                    MonoLabel("MUTED", color = Palette.Faint, size = 7)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    if (conversation.lastOutgoing) append("You: ")
                    append(conversation.snippet.ifBlank { "(no text)" })
                },
                fontSize = 12.sp,
                color = if (conversation.unread > 0) Palette.Ink else Palette.Muted,
                fontWeight = if (conversation.unread > 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            MonoLabel(
                relativeStamp(conversation.date),
                color = if (conversation.unread > 0) Palette.Ink else Palette.Faint,
                size = 9,
            )
            if (conversation.unread > 0) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .size(20.dp)
                        .background(Palette.Ink, CircleShape),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        conversation.unread.toString(),
                        color = Palette.Paper,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (selecting) {
            Spacer(Modifier.width(10.dp))
            SelectionMark(selected)
        }
    }
}
