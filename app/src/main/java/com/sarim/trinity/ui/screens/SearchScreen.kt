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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.data.MessageHit
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Avatar
import com.sarim.trinity.ui.components.EmptyNote
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.PersonAvatar
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.formatNumber
import com.sarim.trinity.ui.relativeStamp
import com.sarim.trinity.ui.theme.Palette

/**
 * One field over calls, texts and contacts. Results and recent queries live in state, so
 * this screen only renders them and reports the callbacks — the debounce is in state too.
 */
@Composable
fun SearchScreen(
    state: TrinityState,
    onBack: () -> Unit,
    onOpenCall: (CallEntry) -> Unit,
    onOpenThread: (threadId: Long, address: String, highlightId: Long?) -> Unit,
    onOpenContact: (Person) -> Unit,
) {
    val query = state.searchQuery
    val results = state.searchResults
    var recentsOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.Bone)) {
        // ScreenHeader has no back slot, so this is the same back-arrow header the other
        // pushed screens use, with the title on the left and meta cost on the right.
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Palette.Ink,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClickLabel = "Back") { onBack() },
            )
            Spacer(Modifier.width(14.dp))
            Text(
                "Search",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Ink,
                modifier = Modifier.weight(1f),
            )
            if (state.recentSearches.isNotEmpty()) {
                MonoLabel(
                    "RECENT",
                    color = Palette.Ink,
                    size = 8,
                    modifier = Modifier
                        .border(1.dp, Palette.Line, RoundedCornerShape(3.dp))
                        .clickable(onClickLabel = "Recent searches") { recentsOpen = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
        Hairline()

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(48.dp)
                .glassBlock(accent(2), 23.dp, 1.2f)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = Palette.Faint,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { state.updateSearch(it) },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Palette.Ink,
                ),
                cursorBrush = SolidColor(Palette.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.rememberSearch(query) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            MonoLabel("name, number or word", size = 9, color = Palette.Faint)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                MonoLabel(
                    "CLEAR",
                    color = Palette.Ink,
                    size = 8,
                    modifier = Modifier.clickable(onClickLabel = "Clear search") {
                        state.updateSearch("")
                    },
                )
            }
        }
        Hairline()

        val hasResults = results.calls.isNotEmpty() ||
            results.messages.isNotEmpty() ||
            results.contacts.isNotEmpty() ||
            results.related.isNotEmpty()

        when {
            query.isBlank() -> {
                if (state.recentSearches.isNotEmpty()) {
                    MonoLabel(
                        "RECENT SEARCHES",
                        color = Palette.Faint,
                        size = 9,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                    state.recentSearches.forEach { recent ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(onClickLabel = "Search again for $recent") {
                                    state.updateSearch(recent)
                                    state.rememberSearch(recent)
                                }
                                .padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = Palette.Faint,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(recent, fontSize = 13.sp, color = Palette.Ink)
                        }
                        Hairline()
                    }
                } else {
                    EmptyNote("Search calls, texts and contacts from one field.")
                }
            }
            !hasResults -> {
                EmptyNote("Nothing found for \"$query\". Try a name, number or a word from a text.")
            }
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (results.calls.isNotEmpty()) {
                            item(key = "section-calls") {
                                SectionLabel("CALLS", results.calls.size)
                            }
                            items(results.calls, key = { "call-${it.id}" }) { entry ->
                                CallHitRow(entry, query) { onOpenCall(entry) }
                            }
                        }
                        if (results.messages.isNotEmpty()) {
                            item(key = "section-messages") {
                                SectionLabel("MESSAGES", results.messages.size)
                            }
                            items(results.messages, key = { "msg-${it.message.id}" }) { hit ->
                                MessageHitRow(hit, query) {
                                    onOpenThread(hit.threadId, hit.message.address, hit.message.id)
                                }
                            }
                        }
                        if (results.related.isNotEmpty()) {
                            item(key = "section-related") {
                                SectionLabel(
                                    "RELATED",
                                    results.related.size,
                                    "ON THIS DEVICE · SIMILAR WORDS",
                                )
                            }
                            items(results.related, key = { "rel-${it.message.id}" }) { hit ->
                                MessageHitRow(hit, query) {
                                    onOpenThread(hit.threadId, hit.message.address, hit.message.id)
                                }
                            }
                        }
                        if (results.contacts.isNotEmpty()) {
                            item(key = "section-contacts") {
                                SectionLabel("CONTACTS", results.contacts.size)
                            }
                            items(results.contacts, key = { "person-${it.id}" }) { person ->
                                ContactHitRow(person, query) { onOpenContact(person) }
                            }
                        }
                        item(key = "end") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }

    if (recentsOpen) {
        InkSheet(
            title = "RECENT SEARCHES",
            actions = state.recentSearches.map { recent ->
                SheetAction(recent) {
                    state.updateSearch(recent)
                    state.rememberSearch(recent)
                }
            },
            onDismiss = { recentsOpen = false },
        )
    }
}

/** The accent list is theme-dependent; never index past its end. */
private fun accent(index: Int) = Palette.Accents[index % Palette.Accents.size]

@Composable
private fun SectionLabel(title: String, count: Int, note: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonoLabel(title, color = Palette.Ink, size = 9)
            MonoLabel(count.toString(), color = Palette.Faint, size = 9)
        }
        if (note != null) {
            Spacer(Modifier.height(3.dp))
            MonoLabel(note, color = Palette.Faint, size = 7)
        }
    }
}

@Composable
private fun CallHitRow(entry: CallEntry, query: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .heightIn(min = 56.dp)
            .glassBlock(accent(2), 18.dp, 0.9f)
            .clickable(onClickLabel = "Open call details for ${entry.displayName}") { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(entry.initials, filled = entry.missed, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(entry.displayName, query),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                highlight(formatNumber(entry.number), query),
                fontSize = 11.sp,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        MonoLabel(relativeStamp(entry.date), color = Palette.Faint, size = 8)
    }
}

@Composable
private fun MessageHitRow(hit: MessageHit, query: String, onClick: () -> Unit) {
    val message = hit.message
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .heightIn(min = 56.dp)
            .glassBlock(accent(0), 18.dp, 0.9f)
            .clickable(onClickLabel = "Open thread with ${message.address}") { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar("#", filled = false, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(hit.highlighted ?: message.body, query),
                fontSize = 13.sp,
                color = Palette.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            MonoLabel(
                "${if (message.isMms) "MMS" else "SMS"} · ${formatNumber(message.address)}",
                color = Palette.Faint,
                size = 8,
            )
        }
        Spacer(Modifier.width(8.dp))
        MonoLabel(relativeStamp(message.date), color = Palette.Faint, size = 8)
    }
}

@Composable
private fun ContactHitRow(person: Person, query: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .heightIn(min = 56.dp)
            .glassBlock(accent(1), 18.dp, 0.9f)
            .clickable(onClickLabel = "Open contact ${person.name}") { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonAvatar(person.initials, person.photoUri, size = 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                highlight(person.name, query),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                highlight(formatNumber(person.number), query),
                fontSize = 11.sp,
                color = Palette.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        MonoLabel(person.section.toString(), color = Palette.Faint, size = 9)
    }
}

/** A search hit reads as inverted ink — no colour, still unmistakable. */
@Composable
private fun highlight(text: String, query: String): AnnotatedString {
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
            withStyle(SpanStyle(background = Palette.Ink, color = Palette.Paper)) {
                append(text.substring(found, found + query.length))
            }
            index = found + query.length
        }
    }
}
