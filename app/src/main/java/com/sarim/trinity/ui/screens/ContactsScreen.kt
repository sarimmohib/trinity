package com.sarim.trinity.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.BottomAction
import com.sarim.trinity.ui.components.EmptyNote
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.components.uiLabel
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.theme.Palette
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    state: TrinityState,
    onOpenPerson: (Person) -> Unit,
    onNewContact: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rules = state.blockStore.rules.value

    // A contact added anywhere else must be here when the tab comes back.
    LaunchedEffect(Unit) { state.refresh() }

    val blockedCount = state.contacts.count {
        BlockMatcher.match(it.name, it.number, null, rules) != null
    }
    val digits = query.filter { it.isDigit() }
    val needle = query.trim()
    val filtered = state.contacts.filter { person ->
        when {
            query.isBlank() -> true
            person.name.contains(needle, ignoreCase = true) -> true
            digits.isNotEmpty() && person.allNumbers.any {
                it.number.filter { c -> c.isDigit() }.contains(digits)
            } -> true
            person.emails.any { it.contains(needle, ignoreCase = true) } -> true
            else -> false
        }
    }
    val sections = filtered.groupBy { it.section }.toSortedMap()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Contacts",
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                    color = Palette.Ink,
                )
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
            Spacer(Modifier.height(5.dp))
            MonoLabel(uiLabel("${state.contacts.size} SAVED · $blockedCount BLOCKED"))
        }
        Hairline()

        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .glassBlock(Palette.Accents[2], 23.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Palette.Faint,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Name, number or email", fontSize = 12.sp, color = Palette.Faint)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = Palette.Ink,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MonoLabel(
                    if (query.isBlank()) "${sections.size} SECTIONS" else "${filtered.size} FOUND",
                    color = Palette.Faint,
                    size = 8,
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                state.contacts.isEmpty() -> EmptyNote("No contacts yet.")

                filtered.isEmpty() -> EmptyNote("No contacts match \"$needle\".")

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 92.dp),
                    ) {
                        sections.forEach { (letter, people) ->
                            stickyHeader(key = "header-$letter") {
                                SectionHeader(letter, people.size)
                            }
                            items(people, key = { it.id }) { person ->
                                val blocked =
                                    BlockMatcher.match(person.name, person.number, null, rules) != null
                                PersonRow(person, blocked) { onOpenPerson(person) }
                            }
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Alphabet index: a thumb-reachable jump for a long book.
                    if (sections.size > 4) {
                        val starts = LinkedHashMap<Char, Int>()
                        var running = 0
                        sections.forEach { (letter, people) ->
                            starts[letter] = running
                            running += 1 + people.size
                        }
                        Column(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            sections.keys.forEach { letter ->
                                val target = starts[letter] ?: 0
                                Text(
                                    letter.toString(),
                                    fontSize = 9.sp,
                                    color = Palette.Faint,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch { listState.scrollToItem(target) }
                                        }
                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Floating glass action over the list, no solid strip behind it.
            BottomAction(
                label = "NEW CONTACT",
                icon = Icons.Outlined.Add,
                onClick = onNewContact,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun SectionHeader(letter: Char, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel(letter.toString(), color = Palette.Ink, size = 9)
        Spacer(Modifier.width(8.dp))
        MonoLabel("$count", color = Palette.Faint, size = 8)
    }
}

@Composable
private fun PersonRow(person: Person, blocked: Boolean, onClick: () -> Unit) {
    // One glass block per person, same as messages and calls.
    val tint = Palette.Accents[
        (person.initials.hashCode() and 0x7FFFFFFF) % Palette.Accents.size
    ]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(72.dp)
            .glassBlock(tint, 22.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person.initials, filled = false)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (blocked) Palette.Muted else Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val extra = person.allNumbers.size - 1
            MonoLabel(
                formatNumber(person.number) + if (extra > 0) "  +$extra MORE" else "",
                size = 9,
                color = Palette.Faint,
            )
        }
        if (blocked) {
            MonoLabel("BLOCKED", color = Palette.Ink, size = 8)
        } else {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Palette.Faint,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
