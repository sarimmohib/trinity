package com.sarim.trinity.ui.qwen_screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.RuleHit
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.qwen_components.ArchitectAvatar
import com.sarim.trinity.ui.qwen_components.ChromeLabel
import com.sarim.trinity.ui.qwen_components.EditorialHeader
import com.sarim.trinity.ui.qwen_components.EmptyState
import com.sarim.trinity.ui.qwen_components.NoticeBar
import com.sarim.trinity.ui.qwen_components.Rule
import com.sarim.trinity.ui.qwen_theme.QwenPalette
import com.sarim.trinity.ui.relativeStamp

@Composable
fun QwenMessagesScreen(
    state: TrinityState,
    onOpenThread: (Conversation) -> Unit,
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

    Column(Modifier.fillMaxSize().background(QwenPalette.Bone)) {
        EditorialHeader(
            title = "Messages",
            meta = "${visible.size} CONVERSATIONS · $hiddenCount FILTERED",
            actionLabel = "RULES ${rules.ruleCount}",
            onAction = onBlocking,
            onSettings = onSettings,
        )

        if (!state.isDefaultSmsApp()) {
            NoticeBar(
                text = "NOT DEFAULT SMS APP · BLOCKING CANNOT STOP DELIVERY",
                actionLabel = "MAKE DEFAULT",
                onClick = { state.requestSmsRole() }
            )
        }

        if (hiddenCount > 0) {
            NoticeBar(
                text = "$hiddenCount HIDDEN BY ${rules.ruleCount} RULES",
                actionLabel = "REVIEW",
                onClick = onReview,
                icon = Icons.Outlined.FilterAlt
            )
        }

        if (visible.isEmpty()) {
            EmptyState("No conversations yet.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(visible, key = { it.threadId }) { conversation ->
                QwenConversationRow(conversation) { onOpenThread(conversation) }
                Rule(indent = true)
            }
        }
    }
}

@Composable
private fun QwenConversationRow(conversation: Conversation, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.Top
    ) {
        ArchitectAvatar(conversation.initials, active = conversation.unread > 0)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = QwenPalette.Ink,
                    fontWeight = if (conversation.unread > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.rule?.kind == RuleHit.Kind.KEYWORD) {
                    Spacer(Modifier.width(8.dp))
                    ChromeLabel(text = "KEYWORD", color = QwenPalette.Muted, bold = true)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                conversation.snippet.ifBlank { "(no text)" },
                style = MaterialTheme.typography.bodyMedium,
                color = QwenPalette.Muted,
                maxLines = 2,
                lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            ChromeLabel(relativeStamp(conversation.date), color = QwenPalette.Faint)
            if (conversation.unread > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(24.dp)
                        .background(QwenPalette.Ink),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        conversation.unread.toString(),
                        color = QwenPalette.Paper,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}
