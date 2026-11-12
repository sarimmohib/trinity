package com.sarim.trinity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.Person
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.glassBlock
import com.sarim.trinity.ui.screens.BlockingScreen
import com.sarim.trinity.ui.screens.CallDetailScreen
import com.sarim.trinity.ui.screens.CallsScreen
import com.sarim.trinity.ui.screens.ContactsScreen
import com.sarim.trinity.ui.screens.DialpadScreen
import com.sarim.trinity.ui.screens.HeldScreen
import com.sarim.trinity.ui.screens.MessagesScreen
import com.sarim.trinity.ui.screens.NewContactScreen
import com.sarim.trinity.ui.screens.NewMessageScreen
import com.sarim.trinity.ui.screens.PersonScreen
import com.sarim.trinity.ui.screens.SettingsScreen
import com.sarim.trinity.ui.screens.ThreadScreen
import com.sarim.trinity.sms.MessageNotifier
import com.sarim.trinity.ui.theme.Palette
import com.sarim.trinity.ui.theme.ThemePrefs
import com.sarim.trinity.ui.theme.TrinityTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A tel: hand-off from another app, waiting to be shown on the dial pad. */
    private val dialRequests = MutableStateFlow<String?>(null)

    /** A notification tapped: which conversation to open. */
    private val threadRequests = MutableStateFlow<Pair<Long, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MessageNotifier.ensureChannels(this)
        ThemePrefs.load(this)
        uptake(intent)
        setContent {
            TrinityTheme {
                TrinityApp(dialRequests, threadRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        uptake(intent)
    }

    private fun uptake(intent: Intent?) {
        if (intent == null) return
        val threadId = intent.getLongExtra(MessageNotifier.EXTRA_THREAD, -1L)
        if (threadId >= 0) {
            threadRequests.value = threadId to intent.getStringExtra(MessageNotifier.EXTRA_ADDRESS).orEmpty()
            // A tapped conversation is a read conversation, whatever screen it opens on.
            intent.removeExtra(MessageNotifier.EXTRA_THREAD)
        }
        val action = intent.action
        if (action != Intent.ACTION_DIAL && action != Intent.ACTION_VIEW && action != Intent.ACTION_CALL) return
        val uri = intent.data ?: return
        val digits = uri.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
        dialRequests.value = digits
    }
}

private sealed interface Route {
    data class Tab(val index: Int) : Route
    data class Thread(val conversation: Conversation) : Route
    data class Contact(val person: Person) : Route
    data class NewContact(val number: String = "", val editing: Person? = null) : Route
    data class NewMessage(val to: String = "", val body: String = "") : Route
    data object Blocking : Route
    data object Held : Route
    data object Dialpad : Route
    data object Settings : Route
    data class CallDetail(val entry: CallEntry) : Route
}

@Composable
private fun TrinityApp(
    dialRequests: MutableStateFlow<String?>,
    threadRequests: MutableStateFlow<Pair<Long, String>?>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { TrinityState(context, scope) }

    var granted by remember { mutableStateOf(state.hasCorePermissions()) }
    var route by remember { mutableStateOf<Route>(Route.Tab(1)) }

    // Back mirrors the way in: the stack is pushed on every step inward, so the
    // gesture undoes the last one (Held → Blocking → tab) instead of guessing.
    val backStack = remember { mutableStateListOf<Route>() }

    fun go(next: Route) {
        if (next is Route.Tab) {
            backStack.clear()
        } else if (next != route) {
            backStack.add(route)
        }
        route = next
    }

    fun parentOf(current: Route): Route = when (current) {
        is Route.CallDetail, Route.Dialpad -> Route.Tab(0)
        is Route.Contact, is Route.NewContact -> Route.Tab(2)
        else -> Route.Tab(1)
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            route = backStack.removeAt(backStack.lastIndex)
        } else {
            route = parentOf(route)
        }
    }

    LaunchedEffect(Unit) {
        dialRequests.collect { number ->
            if (!number.isNullOrBlank()) {
                state.typeDigits(number)
                go(Route.Dialpad)
            }
        }
    }

    LaunchedEffect(Unit) {
        threadRequests.collect { request ->
            if (request != null) {
                state.refresh()
                go(Route.Thread(state.conversationFor(request.first, request.second)))
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = state.hasCorePermissions()
    }

    LaunchedEffect(granted) {
        if (granted) state.refresh()
    }

    if (!granted) {
        PermissionGate(
            onGrant = { launcher.launch(state.requiredPermissions.toTypedArray()) },
        )
        return
    }

    BackHandler(enabled = route !is Route.Tab) { goBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bone)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            when (val current = route) {
                is Route.Tab -> when (current.index) {
                    0 -> CallsScreen(
                        state = state,
                        onOpenDialpad = { go(Route.Dialpad) },
                        onOpenDetail = { go(Route.CallDetail(it)) },
                        onBlocking = { go(Route.Blocking) },
                        onSettings = { go(Route.Settings) },
                    )
                    2 -> ContactsScreen(
                        state = state,
                        onOpenPerson = { go(Route.Contact(it)) },
                        onNewContact = { go(Route.NewContact()) },
                        onSettings = { go(Route.Settings) },
                    )
                    else -> MessagesScreen(
                        state = state,
                        onOpenThread = { go(Route.Thread(it)) },
                        onNewMessage = { go(Route.NewMessage()) },
                        onNewContact = { go(Route.NewContact(it)) },
                        onBlocking = { go(Route.Blocking) },
                        onReview = { go(Route.Held) },
                        onSettings = { go(Route.Settings) },
                    )
                }

                is Route.Thread -> ThreadScreen(
                    state = state,
                    conversation = current.conversation,
                    onBack = { go(Route.Tab(1)) },
                    onBlocking = { go(Route.Blocking) },
                    onNewContact = { go(Route.NewContact(it)) },
                    onForward = { body -> go(Route.NewMessage(body = body)) },
                )

                is Route.NewMessage -> NewMessageScreen(
                    state = state,
                    initialTo = current.to,
                    initialBody = current.body,
                    onBack = { go(Route.Tab(1)) },
                    onSent = { threadId, address ->
                        state.refresh()
                        go(Route.Thread(state.conversationFor(threadId ?: -1L, address)))
                    },
                )

                is Route.Contact -> PersonScreen(
                    state = state,
                    person = state.contacts.firstOrNull { it.id == current.person.id } ?: current.person,
                    onBack = { go(Route.Tab(2)) },
                    onEdit = { go(Route.NewContact(editing = it)) },
                    onMessage = { go(Route.NewMessage(to = it)) },
                    onOpenThread = { go(Route.Thread(it)) },
                    onOpenCall = { go(Route.CallDetail(it)) },
                    onOpenCalls = { go(Route.Tab(0)) },
                )

                is Route.CallDetail -> CallDetailScreen(
                    state = state,
                    entry = current.entry,
                    onBack = { go(Route.Tab(0)) },
                    onNewContact = { go(Route.NewContact(it)) },
                )

                Route.Blocking -> BlockingScreen(
                    state = state,
                    onBack = { go(Route.Tab(1)) },
                    onHeld = { go(Route.Held) },
                )

                Route.Held -> HeldScreen(state = state, onBack = { go(Route.Blocking) })

                Route.Dialpad -> DialpadScreen(
                    state = state,
                    onBack = { go(Route.Tab(0)) },
                    onNewContact = { go(Route.NewContact(it)) },
                )

                is Route.NewContact -> NewContactScreen(
                    state = state,
                    initialNumber = current.number,
                    editing = current.editing,
                    onBack = { go(Route.Tab(2)) },
                    onSaved = {
                        state.refresh()
                        go(Route.Tab(2))
                    },
                )

                Route.Settings -> SettingsScreen(
                    state = state,
                    onBack = { go(Route.Tab(1)) },
                    onBlocking = { go(Route.Blocking) },
                    onHeld = { go(Route.Held) },
                )
            }
        }

        if (route is Route.Tab) {
            TrinityTabBar(
                selected = (route as Route.Tab).index,
                onSelect = { go(Route.Tab(it)) },
            )
        }
    }
}

@Composable
private fun TrinityTabBar(selected: Int, onSelect: (Int) -> Unit) {
    if (Palette.isMaterial) {
        // A floating bar rather than a docked one: rounded, a shade translucent, so
        // the page shows through its edges instead of ending in a hard seam.
        val scheme = MaterialTheme.colorScheme
        if (Palette.isExpressive) {
            // Modular: one glass block per tab, each carrying its own colour. No slab
            // and no gradient across the bar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    Triple("Calls", Icons.Outlined.Phone, 0),
                    Triple("Messages", Icons.Outlined.Message, 1),
                    Triple("Contacts", Icons.Outlined.Contacts, 2),
                ).forEach { (label, icon, index) ->
                    val active = selected == index
                    Column(
                        Modifier
                            .weight(1f)
                            .glassBlock(
                                Palette.Accents[index % Palette.Accents.size],
                                24.dp,
                                if (active) 1.25f else 0.55f,
                            )
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return
        }
        val shape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(shape)
                .background(Palette.Glass),
        ) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
            listOf(
                Triple("Calls", Icons.Outlined.Phone, 0),
                Triple("Messages", Icons.Outlined.Message, 1),
                Triple("Contacts", Icons.Outlined.Contacts, 2),
            ).forEach { (label, icon, index) ->
                NavigationBarItem(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    icon = {
                        Icon(
                            icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = if (Palette.isExpressive) {
                            scheme.primaryContainer
                        } else {
                            scheme.secondaryContainer
                        },
                        selectedIconColor = scheme.onSecondaryContainer,
                        selectedTextColor = scheme.onSurface,
                        unselectedIconColor = scheme.onSurfaceVariant,
                        unselectedTextColor = scheme.onSurfaceVariant,
                    ),
                )
            }
        }
        }
        return
    }

    Column {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .background(Palette.Paper)
                .padding(top = 10.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TabItem("CALLS", Icons.Outlined.Phone, selected == 0, Modifier.weight(1f)) { onSelect(0) }
            TabItem("MESSAGES", Icons.Outlined.Message, selected == 1, Modifier.weight(1f)) { onSelect(1) }
            TabItem("CONTACTS", Icons.Outlined.Contacts, selected == 2, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun TabItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // The active tab sits on a pale green wash: the paper look has a little colour
    // in it now, but only ever behind ink.
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (active) Modifier.background(Palette.GreenWash) else Modifier)
            .clickable { onClick() },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Palette.Ink else Palette.Faint,
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.height(5.dp))
        MonoLabel(label, color = if (active) Palette.Ink else Palette.Faint, size = 8)
        if (active) {
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .width(16.dp)
                    .height(2.dp)
                    .background(Palette.Green),
            )
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Bone)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(40.dp))
        MonoLabel("TRINITY", size = 9)
        Spacer(Modifier.height(6.dp))
        Text(
            "One app for calls,\nmessages and contacts",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Ink,
            lineHeight = 38.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Your rules stay on this device.",
            fontSize = 13.sp,
            color = Palette.Muted,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(26.dp))
        listOf(
            "Contacts" to "to block by name",
            "SMS" to "to read and hold texts",
            "Call log" to "to show recent calls",
        ).forEach { (title, why) ->
            Row(Modifier.padding(vertical = 10.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
                Spacer(Modifier.width(8.dp))
                Text(why, fontSize = 12.sp, color = Palette.Muted, modifier = Modifier.weight(1f))
            }
            Hairline()
        }
        Spacer(Modifier.height(26.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Palette.Ink, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                .clickable { onGrant() },
            contentAlignment = Alignment.Center,
        ) {
            MonoLabel("ALLOW ACCESS", color = Palette.Paper, size = 10)
        }
    }
}
