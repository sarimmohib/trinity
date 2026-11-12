package com.sarim.trinity.ui.screens
import com.sarim.trinity.ui.components.glassBlock

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.BottomAction
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.theme.Palette

/**
 * New contact, or the editor for an existing one — same fields, one save path.
 * Opened from the dial pad it arrives with the number already typed.
 */
@Composable
fun NewContactScreen(
    state: TrinityState,
    initialNumber: String = "",
    editing: Person? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var numbers by remember {
        mutableStateOf(
            editing?.allNumbers?.map { it.number }
                ?: listOfNotNull(initialNumber.takeIf { it.isNotBlank() }),
        )
    }
    var email by remember { mutableStateOf(editing?.emails?.firstOrNull().orEmpty()) }
    var blockToo by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val isEditing = editing != null
    val filled = name.isNotBlank() || numbers.any { it.isNotBlank() } || email.isNotBlank()
    val canSave = numbers.any { it.trim().isNotEmpty() } || name.isNotBlank()

    val digits = numbers.map { it.filter { c -> c.isDigit() } }.filter { it.length >= 5 }
    val duplicate = state.contacts.firstOrNull { other ->
        other.id != editing?.id && other.allNumbers.any { entry ->
            val value = entry.number.filter { c -> c.isDigit() }
            digits.any { value.isNotEmpty() && value.takeLast(8) == it.takeLast(8) }
        }
    }

    fun requestClose() {
        if (filled && !saved) confirmDiscard = true else onBack()
    }

    // The system back key has to honour the same guard as the close button, or a
    // half-typed contact disappears without a word.
    androidx.activity.compose.BackHandler(enabled = true) { requestClose() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MonoLabel("CONTACTS", size = 9)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { requestClose() },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (isEditing) "Edit contact" else "New contact",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Ink,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "Saves to the phone's address book.",
                fontSize = 12.sp,
                color = Palette.Muted,
                lineHeight = 18.sp,
            )
        }
        Hairline()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .glassBlock(Palette.Accents[1], 20.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    initials = name.trim().split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifEmpty { "?" },
                    filled = true,
                    size = 56,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { "No name yet" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (name.isBlank()) Palette.Faint else Palette.Ink,
                    )
                    MonoLabel(
                        numbers.firstOrNull { it.isNotBlank() } ?: "ADD A NUMBER",
                        size = 9,
                    )
                }
            }

            FieldRow("NAME", name, "First and last", keyboard = KeyboardType.Text) { name = it }

            numbers.forEachIndexed { index, value ->
                FieldRow(
                    label = if (index == 0) "PHONE" else "PHONE ${index + 1}",
                    value = value,
                    hint = "Mobile",
                    keyboard = KeyboardType.Phone,
                    filter = { input -> input.filter { it.isDigit() || it in "+*# -()" } },
                    onChange = { updated ->
                        numbers = numbers.toMutableList().also { it[index] = updated }
                    },
                    trailing = if (numbers.size > 1) {
                        {
                            MonoLabel("REMOVE", color = Palette.Faint, size = 8, modifier = Modifier.clickable {
                                numbers = numbers.toMutableList().also { it.removeAt(index) }
                            })
                        }
                    } else {
                        null
                    },
                )
            }
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .glassBlock(Palette.Accents[1], 20.dp)
                    .clickable { numbers = numbers + "" }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Palette.Ink, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(9.dp))
                MonoLabel("ADD ANOTHER NUMBER", color = Palette.Ink, size = 8)
            }

            FieldRow("EMAIL", email, "Optional", keyboard = KeyboardType.Email) { email = it }

            if (duplicate != null) {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .glassBlock(Palette.Accents[2], 18.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        "That number is already saved as ${duplicate.name}.",
                        fontSize = 11.sp,
                        color = Palette.Ink,
                        lineHeight = 16.sp,
                    )
                }
            }

            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(62.dp)
                    .glassBlock(Palette.Accents[2], 20.dp)
                    .clickable { blockToo = !blockToo }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Block this contact too",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Ink,
                    )
                    MonoLabel("ADDS A NAME AND NUMBER RULE AFTER SAVING", size = 8, color = Palette.Faint)
                }
                Toggle(blockToo) { blockToo = it }
            }

            if (failed) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        "The address book refused the write. Nothing was saved — check the contacts permission.",
                        fontSize = 11.sp,
                        color = Palette.Ink,
                        lineHeight = 16.sp,
                    )
                }
            }
            if (saved) {
                Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    MonoLabel(
                        if (isEditing) "SAVED · CHANGES WRITTEN" else "SAVED · CONTACT ADDED",
                        color = Palette.Ink,
                        size = 9,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        BottomAction(
            label = if (isEditing) "SAVE CHANGES" else "SAVE CONTACT",
            icon = Icons.Outlined.Add,
            enabled = canSave,
            hint = if (canSave) {
                null
            } else {
                "A NAME OR A NUMBER IS REQUIRED"
            },
            onClick = {
                fun afterSave(ok: Boolean) {
                    if (!ok) {
                        failed = true
                        return
                    }
                    failed = false
                    if (blockToo) {
                        if (name.isNotBlank()) state.addRule(name)
                        numbers.filter { it.isNotBlank() }.forEach { state.addRule(it) }
                    }
                    saved = true
                    onSaved()
                }
                val cleaned = numbers.filter { it.isNotBlank() }
                if (isEditing) {
                    state.updateContact(editing!!, name.trim(), cleaned, email.trim()) { afterSave(it) }
                } else {
                    state.saveContact(name.trim(), cleaned, email.trim()) { afterSave(it) }
                }
            },
        )
    }

    if (confirmDiscard) {
        InkSheet(
            title = "DISCARD",
            actions = listOf(
                SheetAction("Discard and leave", "NOTHING WRITTEN") { onBack() },
                SheetAction("Keep editing") { },
            ),
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    hint: String,
    keyboard: KeyboardType,
    filter: (String) -> String = { it },
    trailing: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .glassBlock(Palette.Accents[0], 18.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        MonoLabel(label, size = 8, color = Palette.Faint)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(hint, fontSize = 15.sp, color = Palette.Faint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = { onChange(filter(it)) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = Palette.Ink),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
    }
}
