package com.sarim.trinity.ui.screens
import com.sarim.trinity.ui.components.glassBlock

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarim.trinity.BuildConfig
import com.sarim.trinity.ui.TrinityState
import com.sarim.trinity.ui.components.Hairline
import com.sarim.trinity.ui.components.InkSheet
import com.sarim.trinity.ui.components.MonoLabel
import com.sarim.trinity.ui.components.SheetAction
import com.sarim.trinity.ui.theme.Palette
import com.sarim.trinity.ui.theme.Brightness
import com.sarim.trinity.ui.theme.ThemePrefs

@Composable
fun SettingsScreen(
    state: TrinityState,
    onBack: () -> Unit,
    onBlocking: () -> Unit,
    onHeld: () -> Unit,
) {
    val context = LocalContext.current
    val rules = state.blockStore.rules.value
    val smsDefault = state.isDefaultSmsApp()
    val dialerDefault = state.isDefaultDialer()
    val granted = state.grantedPermissionCount()
    val total = state.requiredPermissions.size
    val selectedSim = state.simCards.firstOrNull { it.subscriptionId == state.selectedSubscription }
    val defaultSim = state.simCards.firstOrNull { it.subscriptionId == state.defaultSubscription }

    var confirm by remember { mutableStateOf<String?>(null) }
    var simSheet by remember { mutableStateOf(false) }
    var themeSheet by remember { mutableStateOf(false) }

    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { state.importContacts(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-vcard"),
    ) { uri -> uri?.let { state.exportContacts(it) } }

    // Role dialogs are opened directly, not through a result launcher: the screen
    // re-reads the role state on ON_RESUME anyway, and a direct start reports a
    // missing handler immediately instead of failing silently in the launcher.
    var roleNote by remember { mutableStateOf<String?>(null) }

    val askForRole = com.sarim.trinity.ui.components.rememberRoleAsker(state) { roleNote = it }

    // A role granted in the system dialog lands after this screen went to the
    // background; re-read on the way back so the row stops saying NOT SET.
    val activity = context as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) state.refresh()
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Palette.Ink,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() },
                )
            }
            Spacer(Modifier.height(3.dp))
            MonoLabel("TRINITY ${BuildConfig.VERSION_NAME}")
        }
        Hairline()

        SettingsSection("ROLES")
        SettingsRow(
            label = "Default SMS app",
            value = if (smsDefault) "ON" else "NOT SET",
            sub = if (smsDefault) "TRINITY FILES TEXTS" else "BLOCKING CANNOT STOP DELIVERY",
            icon = Icons.Outlined.Smartphone,
            onClick = { if (!smsDefault) askForRole(TrinityState.DefaultApp.Sms) },
        )
        SettingsRow(
            label = "Default phone app",
            value = if (dialerDefault) "ON" else "NOT SET",
            sub = if (dialerDefault) "TRINITY OWNS THE CALL SCREEN" else "USES THE STOCK CALL SCREEN",
            icon = Icons.Outlined.Phone,
            onClick = { if (!dialerDefault) askForRole(TrinityState.DefaultApp.Dialer) },
        )
        SettingsRow(
            label = "Send from",
            value = when {
                state.askEachTime -> "ASK EACH TIME"
                selectedSim != null -> selectedSim.label.uppercase()
                defaultSim != null -> "${defaultSim.label.uppercase()} (DEFAULT)"
                else -> "SYSTEM DEFAULT"
            },
            sub = if (state.simCards.isEmpty()) "NO SIMS DETECTED ON THIS DEVICE" else null,
            icon = Icons.Outlined.Security,
            onClick = { simSheet = true },
        )
        SettingsRow(
            label = "Permissions",
            value = "$granted/$total",
            sub = if (granted == total) "ALL GRANTED" else null,
            icon = Icons.Outlined.Shield,
            onClick = {
                open(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
        )

        roleNote?.let { note -> StaticRow(label = "Default app request", value = "", sub = note) }

        SettingsSection("BLOCKING")
        SettingsRow(
            label = "Name & number rules",
            value = "${rules.names.size + rules.numbers.size}",
            icon = Icons.Outlined.PersonOff,
            onClick = onBlocking,
        )
        SettingsRow(
            label = "Keyword rules",
            value = "${rules.keywords.size}",
            icon = Icons.Outlined.Tag,
            onClick = onBlocking,
        )
        SettingsRow(
            label = "Held messages",
            value = "${state.held.size + state.blockedMessages.size}",
            icon = Icons.Outlined.Archive,
            onClick = onHeld,
        )
        SettingsRow(
            label = "Allowed senders",
            value = "${rules.allowed.size}",
            icon = Icons.Outlined.PersonOff,
            onClick = onBlocking,
        )
        SettingsRow(
            label = "Hide matched calls",
            icon = Icons.Outlined.Shield,
            toggle = rules.hideMatchedCalls,
            onToggle = { state.setHideMatchedCalls(it) },
        )

        SettingsSection("MESSAGING")
        SettingsRow(
            label = "Notifications",
            value = "SYSTEM",
            icon = Icons.Outlined.Info,
            onClick = {
                open(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            },
        )

        SettingsSection("APPEARANCE")
        SettingsRow(
            label = "Appearance",
            value = when (ThemePrefs.brightness) {
                Brightness.Dark -> "DARK"
                Brightness.Light -> "LIGHT"
                Brightness.System -> "SYSTEM"
            },
            sub = "LIGHT OR DARK · GLASS BLOCKS",
            icon = Icons.Outlined.Palette,
            onClick = { themeSheet = true },
        )
        SettingsRow(
            label = "Text size",
            value = "SYSTEM",
            icon = Icons.Outlined.Info,
            onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
        SettingsRow(
            label = "TalkBack",
            value = "SYSTEM",
            icon = Icons.Outlined.Info,
            onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )

        SettingsSection("CONTACTS")
        SettingsRow(
            label = "Import from file",
            value = ".VCF",
            icon = Icons.Outlined.FileDownload,
            onClick = { importLauncher.launch(arrayOf("*/*")) },
        )
        SettingsRow(
            label = "Export to file",
            value = ".VCF",
            icon = Icons.Outlined.FileUpload,
            onClick = { exportLauncher.launch("trinity-contacts.vcf") },
        )
        state.fileNote?.let { note ->
            StaticRow(label = "Last file", value = "", sub = note.uppercase())
        }

        SettingsSection("DATA")
        StaticRow(
            label = "Storage",
            value = "",
            sub = "${state.conversations.size} CONVERSATIONS · ${state.calls.size} CALLS · " +
                "${state.contacts.size} CONTACTS · ${state.held.size} HELD",
        )
        SettingsRow(
            label = "Clear call history",
            value = "${state.calls.size}",
            icon = Icons.Outlined.Storage,
            onClick = { if (state.calls.isNotEmpty()) confirm = "calls" },
        )
        SettingsRow(
            label = "Clear held texts",
            value = "${state.held.size}",
            icon = Icons.Outlined.Archive,
            onClick = { if (state.held.isNotEmpty()) confirm = "held" },
        )
        SettingsRow(
            label = "Clear all rules",
            value = "${rules.ruleCount}",
            icon = Icons.Outlined.PersonOff,
            onClick = { if (rules.ruleCount > 0 || rules.allowed.isNotEmpty()) confirm = "rules" },
        )

        SettingsSection("ABOUT")
        StaticRow(label = "Version", value = BuildConfig.VERSION_NAME, sub = "DEBUG BUILD")
        StaticRow(
            label = "Replaces",
            value = "",
            sub = "PHONE · MESSAGES · CONTACTS",
        )

        Box(Modifier.padding(24.dp)) {
            Text(
                "Rules, held texts and drafts never leave this device.",
                fontSize = 10.sp,
                color = Palette.Faint,
                lineHeight = 16.sp,
            )
        }
    }

    if (themeSheet) {
        InkSheet(
            title = "APPEARANCE",
            actions = listOf(
                SheetAction(
                    "Follow the system · light or dark",
                    if (ThemePrefs.brightness == Brightness.System) "ON" else null,
                ) { ThemePrefs.setBrightness(context, Brightness.System) },
                SheetAction(
                    "Light",
                    if (ThemePrefs.brightness == Brightness.Light) "ON" else null,
                ) { ThemePrefs.setBrightness(context, Brightness.Light) },
                SheetAction(
                    "Dark",
                    if (ThemePrefs.brightness == Brightness.Dark) "ON" else null,
                ) { ThemePrefs.setBrightness(context, Brightness.Dark) },
            ),
            onDismiss = { themeSheet = false },
        )
    }

    if (simSheet) {
        InkSheet(
            title = "SEND FROM",
            actions = buildList {
                add(
                    SheetAction(
                        "Ask every time",
                        if (state.askEachTime) "ON" else null,
                    ) { state.askEveryTime() },
                )
                add(
                    SheetAction(
                        "System default",
                        if (state.simCards.isEmpty()) "NO SIMS" else null,
                    ) { state.selectSim(null) },
                )
                state.simCards.forEach { card ->
                    add(
                        SheetAction(
                            card.label,
                            if (card.subscriptionId == state.defaultSubscription) "DEFAULT" else null,
                        ) { state.selectSim(card.subscriptionId) },
                    )
                }
            },
            onDismiss = { simSheet = false },
        )
    }

    confirm?.let { key ->
        val title = when (key) {
            "calls" -> "CLEAR CALL HISTORY"
            "held" -> "CLEAR HELD TEXTS"
            else -> "CLEAR ALL RULES"
        }
        val action = when (key) {
            "calls" -> "Delete ${state.calls.size} call entries"
            "held" -> "Delete ${state.held.size} held texts"
            else -> "Delete ${rules.ruleCount} rules and ${rules.allowed.size} allowances"
        }
        InkSheet(
            title = title,
            actions = listOf(
                SheetAction(action, "CANNOT BE UNDONE") {
                    when (key) {
                        "calls" -> state.clearCalls()
                        "held" -> state.clearHeld()
                        else -> state.resetRules()
                    }
                },
                SheetAction("Keep everything") { },
            ),
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun SettingsSection(label: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        MonoLabel(label, size = 8, color = Palette.Faint)
    }
}

/** A fact, not a button: no chevron, nothing to tap, nothing pretending. */
@Composable
private fun StaticRow(label: String, value: String, sub: String? = null) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .fillMaxWidth()
            .height(58.dp)
            .glassBlock(Palette.Accents[1], 20.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                MonoLabel(sub, size = 8, color = Palette.Faint)
            }
        }
        if (value.isNotBlank()) {
            MonoLabel(value, size = 9, color = Palette.Muted)
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String? = null,
    sub: String? = null,
    icon: ImageVector? = null,
    toggle: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val tint = Palette.Accents[(label.hashCode() and 0x7FFFFFFF) % Palette.Accents.size]
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .fillMaxWidth()
            .height(62.dp)
            .glassBlock(tint, 20.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Palette.Muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            if (sub != null) {
                Spacer(Modifier.height(2.dp))
                MonoLabel(sub, size = 8, color = Palette.Faint)
            }
        }
        if (value != null) {
            MonoLabel(value, size = 9, color = Palette.Muted)
        }
        if (toggle != null && onToggle != null) {
            Spacer(Modifier.width(10.dp))
            Toggle(toggle, onToggle)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Palette.Faint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
