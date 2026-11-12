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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.BlockStore
import com.sarim.trinity.data.Scope
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.RuleChip
import com.sarim.trinity.ui.theme.Palette

@Composable
fun BlockingScreen(
    state: TrinityState,
    onBack: () -> Unit,
    onHeld: () -> Unit,
) {
    var segment by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    // New rules start covering both surfaces; the choice sticks for the next one.
    var scope by remember { mutableStateOf(Scope.Both) }
    val rules = state.blockStore.rules.value

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("RULES", size = 9)
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
            Text("Blocking", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
        }
        Hairline()

        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SegmentButton("NAMES · ${rules.names.size + rules.numbers.size}", segment == 0, Modifier.weight(1f)) { segment = 0 }
            SegmentButton("KEYWORDS · ${rules.keywords.size}", segment == 1, Modifier.weight(1f)) { segment = 1 }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            MonoLabel(if (segment == 0) "BLOCK A NAME OR NUMBER" else "BLOCK A WORD", size = 8, color = Palette.Faint)
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, Palette.Ink, RoundedCornerShape(5.dp))
                    .background(Palette.Paper, RoundedCornerShape(5.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (draft.isEmpty()) {
                        Text(
                            if (segment == 0) "Name, or a number" else "A word inside a message",
                            fontSize = 12.sp,
                            color = Palette.Faint,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Palette.Ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Palette.Ink, CircleShape)
                        .clickable {
                            if (draft.isNotBlank()) {
                                if (segment == 0) state.addRule(draft, scope) else state.addKeyword(draft)
                                draft = ""
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add rule",
                        tint = Palette.Paper,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (segment == 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(Scope.Both, Scope.Texts, Scope.Calls).forEach { option ->
                        ScopeButton(option, scope == option, Modifier.weight(1f)) { scope = option }
                    }
                }
            }
        }

        if (segment == 0) {
            SectionLabel("BLOCKED NAMES & NUMBERS")
            rules.names.forEach { rule ->
                RuleRow(rule.value, "NAME · ${rule.scope.label}") { state.removeName(rule.value) }
            }
            rules.numbers.forEach { rule ->
                RuleRow(rule.value, "NUMBER · ${rule.scope.label}") { state.removeNumber(rule.value) }
            }
            if (rules.names.isEmpty() && rules.numbers.isEmpty()) {
                EmptyRules("No names blocked yet.")
            }
        } else {
            SectionLabel("KEYWORD RULES · TEXTS")
            if (rules.keywords.isEmpty()) {
                EmptyRules("No keywords yet.")
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    rules.keywords.take(3).forEach { word ->
                        RuleChip(word, 0) { state.removeKeyword(word) }
                    }
                }
                if (rules.keywords.size > 3) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        rules.keywords.drop(3).forEach { word ->
                            RuleChip(word, 0) { state.removeKeyword(word) }
                        }
                    }
                }
            }
        }

        if (rules.allowed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            SectionLabel("ALLOWED SENDERS · ${rules.allowed.size}")
            rules.allowed.forEach { address ->
                RuleRow(address, "ALLOWED") { state.disallowSender(address) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Also hide matched calls", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Ink)
            }
            Toggle(rules.hideMatchedCalls) { state.setHideMatchedCalls(it) }
        }
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable { onHeld() }
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Held messages", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Ink)
                MonoLabel(
                    "${state.held.size} IN QUARANTINE · ${state.blockedMessages.size} IN THE INBOX",
                    size = 8,
                    color = Palette.Faint,
                )
            }
            MonoLabel("REVIEW", color = Palette.Ink, size = 8)
        }
        Hairline()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SegmentButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(34.dp)
            .background(if (active) Palette.Ink else Palette.Bone, RoundedCornerShape(4.dp))
            .border(
                1.dp,
                if (active) Palette.Ink else Palette.Line,
                RoundedCornerShape(4.dp),
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(label, color = if (active) Palette.Paper else Palette.Ink, size = 9)
    }
}

@Composable
private fun ScopeButton(scope: Scope, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(28.dp)
            .background(if (active) Palette.Ink else Palette.Bone, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) Palette.Ink else Palette.Line, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(scope.label, color = if (active) Palette.Paper else Palette.Ink, size = 8)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 8.dp)) {
        MonoLabel(text, size = 8, color = Palette.Faint)
    }
}

@Composable
private fun RuleRow(value: String, sub: String, onRemove: () -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (sub.startsWith("NAME")) Icons.Outlined.PersonOff else Icons.Outlined.Tag,
                contentDescription = null,
                tint = Palette.Faint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                MonoLabel(sub, size = 8, color = Palette.Faint)
            }
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove rule",
                tint = Palette.Muted,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onRemove() },
            )
        }
        Hairline()
    }
}

@Composable
private fun EmptyRules(text: String) {
    Box(Modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(text, fontSize = 12.sp, color = Palette.Muted, lineHeight = 18.sp)
    }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(44.dp)
            .height(26.dp)
            .background(if (on) Palette.Ink else Palette.Wash, CircleShape)
            .clickable { onChange(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .size(20.dp)
                .background(Palette.Paper, CircleShape),
        )
    }
}

/** Shared by the Blocking screen and Settings. */
fun ruleSummary(rules: BlockStore.Rules): String =
    "${rules.names.size} names · ${rules.numbers.size} numbers · ${rules.keywords.size} keywords"
