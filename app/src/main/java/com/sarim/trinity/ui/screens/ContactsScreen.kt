package com.sarim.trinity.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.ContactDuplicates
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.BottomAction
import com.sarim.trinity.ui.components.EmptyNote
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.PersonAvatar
import com.sarim.trinity.ui.components.Selection
import com.sarim.trinity.ui.components.SelectionAction
import com.sarim.trinity.ui.components.SelectionBar
import com.sarim.trinity.ui.components.SelectionMark
import com.sarim.trinity.ui.components.SheetAction
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
    onSearch: () -> Unit,
    onNewContact: () -> Unit,
    onSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rules by state.blockStore.rules.collectAsStateWithLifecycle()
    val selection = remember { Selection<Person> { it.id } }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // M6: groups are read-only. Membership is already on each Person, so the
    // filter is an in-memory check — Trinity never edits groups across accounts.
    val groups = state.contactGroups()
    var selectedGroup by remember { mutableStateOf<Long?>(null) }
    var showDuplicates by remember { mutableStateOf(false) }

    // A contact added anywhere else must be here when the tab comes back.
    LaunchedEffect(Unit) { state.refreshIfStale() }

    // Matched once per contact list and rule set, instead of for the whole book on
    // every recomposition and again for each row as it scrolls into view.
    val blockedIds = remember(state.contacts, rules) {
        state.contacts
            .filter { BlockMatcher.match(it.name, it.number, null, rules) != null }
            .mapTo(HashSet()) { it.id }
    }
    val blockedCount = blockedIds.size
    val needle = query.trim()
    // Group membership is read from the contact snapshot, so filtering never
    // touches the provider. null means the ALL chip: no restriction at all.
    val groupFiltered = remember(state.contacts, selectedGroup) {
        val id = selectedGroup
        if (id == null) state.contacts else state.contacts.filter { id in it.groupIds }
    }
    // Searched and sectioned once per contact list and query, not on every
    // recomposition (a selection change redoes the whole address book otherwise).
    val filtered = remember(groupFiltered, query) {
        val digits = query.filter { it.isDigit() }
        groupFiltered.filter { person ->
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
    }
    val sections = remember(filtered) { filtered.groupBy { it.section }.toSortedMap() }
    // Starred contacts are pinned above the alphabetical book. They stay in the
    // book too, so the same person appears once in each part of the list.
    val favourites = remember(filtered) { filtered.filter { it.starred } }
    val picked = if (selection.active) filtered.filter { selection.contains(it) } else emptyList()

    // Back drops the selection before it leaves the tab.
    androidx.activity.compose.BackHandler(enabled = selection.active) { selection.clear() }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                            .clickable(onClickLabel = "Find duplicate contacts") {
                                showDuplicates = true
                            }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MonoLabel(uiLabel("FIND DUPLICATES"), color = Palette.Ink, size = 8)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                            .clickable(onClickLabel = "Open unified search") { onSearch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search calls, texts and contacts",
                            tint = Palette.Ink,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                            .clickable(onClickLabel = "Open settings") { onSettings() },
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

        // Read-only group filter. ALL clears the restriction; every other chip
        // keeps only contacts whose groupIds contain that group's id.
        if (groups.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupChip("ALL", selectedGroup == null, "Show all contacts") {
                    selectedGroup = null
                }
                groups.forEach { group ->
                    GroupChip(
                        group.name,
                        selectedGroup == group.id,
                        "Filter contacts by ${group.name}",
                    ) { selectedGroup = group.id }
                }
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = if (selection.active) 128.dp else 92.dp,
                        ),
                    ) {
                        if (favourites.isNotEmpty()) {
                            stickyHeader(key = "header-favourites", contentType = "header") {
                                FavouritesHeader(favourites.size)
                            }
                            items(
                                favourites,
                                key = { "fav-${it.id}" },
                                contentType = { "person" },
                            ) { person ->
                                val blocked = person.id in blockedIds
                                PersonRow(
                                    person = person,
                                    blocked = blocked,
                                    selecting = selection.active,
                                    selected = selection.contains(person),
                                    onClick = {
                                        if (selection.active) selection.toggle(person)
                                        else onOpenPerson(person)
                                    },
                                    onLongPress = { selection.start(person) },
                                )
                            }
                        }
                        sections.forEach { (letter, people) ->
                            stickyHeader(key = "header-$letter", contentType = "header") {
                                SectionHeader(letter, people.size)
                            }
                            items(people, key = { it.id }, contentType = { "person" }) { person ->
                                val blocked = person.id in blockedIds
                                PersonRow(
                                    person = person,
                                    blocked = blocked,
                                    selecting = selection.active,
                                    selected = selection.contains(person),
                                    onClick = {
                                        if (selection.active) selection.toggle(person)
                                        else onOpenPerson(person)
                                    },
                                    onLongPress = { selection.start(person) },
                                )
                            }
                        }
                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Alphabet index: a thumb-reachable jump for a long book.
                    if (sections.size > 4) {
                        val starts = LinkedHashMap<Char, Int>()
                        // The pinned FAVOURITES header and its rows come before the
                        // first letter, so every alphabetical index shifts by them.
                        var running = if (favourites.isEmpty()) 0 else 1 + favourites.size
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
                                        .clickable(onClickLabel = "Jump to section $letter") {
                                            scope.launch { listState.scrollToItem(target) }
                                        }
                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Floating glass action over the list, no solid strip behind it. Picked
            // contacts get the selection bar in the same place.
            if (selection.active) {
                SelectionBar(
                    count = picked.size,
                    actions = buildList {
                        if (picked.size == 1) {
                            add(SelectionAction("Open") { onOpenPerson(picked.first()) })
                        }
                        add(
                            SelectionAction("Copy") {
                                val numbers = picked.flatMap { person ->
                                    person.allNumbers.map { it.number }
                                }
                                clipboard.setText(AnnotatedString(numbers.joinToString("\n")))
                                selection.clear()
                            },
                        )
                        add(
                            SelectionAction("Block") {
                                picked.forEach { person ->
                                    person.allNumbers.forEach { state.addRule(it.number) }
                                }
                                selection.clear()
                            },
                        )
                        add(SelectionAction("Delete") { confirmBulkDelete = true })
                        if (picked.size < filtered.size) {
                            add(SelectionAction("All") { selection.pickAll(filtered) })
                        }
                    },
                    onClear = { selection.clear() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else {
                BottomAction(
                    label = "NEW CONTACT",
                    icon = Icons.Outlined.Add,
                    onClick = onNewContact,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    if (confirmBulkDelete && picked.isNotEmpty()) {
        val doomed = picked
        InkSheet(
            title = if (doomed.size == 1) "DELETE CONTACT" else "DELETE ${doomed.size} CONTACTS",
            actions = listOf(
                SheetAction("Delete from contacts", "CANNOT BE UNDONE") {
                    confirmBulkDelete = false
                    state.deleteContacts(doomed) { selection.clear() }
                },
                SheetAction("Keep them") { },
            ),
            onDismiss = { confirmBulkDelete = false },
        )
    }

    if (showDuplicates) {
        val pairs = ContactDuplicates.candidates(state.contacts).take(20)
        InkSheet(
            title = "DUPLICATE CONTACTS",
            actions = pairs.flatMap { (a, b) ->
                val hint = if (ContactDuplicates.isStrong(a, b)) "SAME NUMBER" else "SAME NAME"
                listOf(
                    SheetAction("Keep ${a.name} · merge ${b.name}", hint) {
                        state.mergeContacts(a, b)
                        showDuplicates = false
                    },
                    SheetAction("Keep ${b.name} · merge ${a.name}", hint) {
                        state.mergeContacts(b, a)
                        showDuplicates = false
                    },
                )
            } + listOf(
                SheetAction(
                    if (pairs.isEmpty()) "No duplicates found" else "Close",
                ) { showDuplicates = false },
            ),
            onDismiss = { showDuplicates = false },
        )
    }
}

@Composable
private fun GroupChip(label: String, active: Boolean, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .background(if (active) Palette.Ink else Palette.Bone, RoundedCornerShape(4.dp))
            .border(1.dp, if (active) Palette.Ink else Palette.Line, RoundedCornerShape(4.dp))
            .clickable(onClickLabel = description) { onClick() }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        MonoLabel(label.uppercase(), size = 8, color = if (active) Palette.Paper else Palette.Ink)
    }
}

@Composable
private fun FavouritesHeader(count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.Bone)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MonoLabel("FAVOURITES", color = Palette.Ink, size = 9)
        Spacer(Modifier.width(8.dp))
        MonoLabel("$count", color = Palette.Faint, size = 8)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(
    person: Person,
    blocked: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    // One glass block per person, same as messages and calls.
    val tint = Palette.Accents[
        (person.initials.hashCode() and 0x7FFFFFFF) % Palette.Accents.size
    ]
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
        PersonAvatar(person.initials, person.photoUri, filled = false)
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
        if (selecting) {
            SelectionMark(selected)
        } else if (blocked) {
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
