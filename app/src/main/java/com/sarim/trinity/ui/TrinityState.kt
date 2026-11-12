package com.sarim.trinity.ui

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.sarim.trinity.data.BlockMatcher
import com.sarim.trinity.data.BlockStore
import com.sarim.trinity.data.CallEntry
import com.sarim.trinity.data.CallsRepository
import com.sarim.trinity.data.ContactPhotos
import com.sarim.trinity.data.ContactWriter
import com.sarim.trinity.data.ContactsRepository
import com.sarim.trinity.data.Conversation
import com.sarim.trinity.data.Message
import com.sarim.trinity.data.MessageState
import com.sarim.trinity.data.MessagesRepository
import com.sarim.trinity.data.PendingMessage
import com.sarim.trinity.data.Person
import com.sarim.trinity.data.QuarantineStore
import com.sarim.trinity.data.Scope
import com.sarim.trinity.data.SimCard
import com.sarim.trinity.data.Sims
import com.sarim.trinity.data.SmsStore
import com.sarim.trinity.data.ThreadPrefs
import com.sarim.trinity.data.VCards
import com.sarim.trinity.sms.MessageNotifier
import com.sarim.trinity.sms.SmsSender
import com.sarim.trinity.sms.holdsSmsRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One holder for everything the screens read. Deliberately plain: no DI, no
 * database, no background sync — the phone's own stores are the source of truth
 * and are re-read when a screen is opened.
 */
class TrinityState(private val context: Context, private val scope: CoroutineScope) {

    val blockStore = BlockStore.get(context)
    private val contactsRepo = ContactsRepository(context)
    private val messagesRepo = MessagesRepository(context)
    private val callsRepo = CallsRepository(context)
    private val sims = Sims(context)
    private val quarantine = QuarantineStore(context)
    private val threadPrefs = ThreadPrefs(context)
    private val simPrefs =
        context.applicationContext.getSharedPreferences("trinity.sims", Context.MODE_PRIVATE)

    var contacts by mutableStateOf<List<Person>>(emptyList())
        private set
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var calls by mutableStateOf<List<CallEntry>>(emptyList())
        private set
    var blockedMessages by mutableStateOf<List<Message>>(emptyList())
        private set
    var held by mutableStateOf<List<QuarantineStore.Entry>>(emptyList())
        private set
    var messages by mutableStateOf<List<Message>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    /** True when this device has no telephony provider at all, so there is nothing to show. */
    var smsProviderMissing by mutableStateOf(false)
        private set

    /** Unread across every conversation, shown in the list header. */
    var unreadTotal by mutableStateOf(0)
        private set

    /** The thread currently open, so a send knows where its pending row belongs. */
    var openThreadId by mutableStateOf<Long?>(null)
        private set

    /** Messages on their way out, kept in memory until the provider agrees to keep them. */
    var pending by mutableStateOf<List<PendingMessage>>(emptyList())
        private set

    private var nextLocalId = 1L

    /** Messages the user chose to reveal once; session-only by design. */
    var revealed by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Digits on the dial pad, held here so leaving the screen does not lose them. */
    var dialpadNumber by mutableStateOf("")
        private set

    fun typeDigits(value: String) {
        dialpadNumber = value.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
    }

    /** False on a device (or WSA) with no telephony at all — say so instead of pretending. */
    fun canPlaceCalls(): Boolean {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return false
        return telephony.phoneType != TelephonyManager.PHONE_TYPE_NONE
    }

    /** Every SIM this phone can dial from; empty on single-SIM and SIM-less devices. */
    var simCards by mutableStateOf<List<SimCard>>(emptyList())
        private set

    /** Chosen SIM for the next call; null means "whatever the phone defaults to". */
    var selectedSubscription by mutableStateOf<Int?>(null)
        private set

    /** The phone's default voice subscription, used to preselect a chip. */
    var defaultSubscription by mutableStateOf<Int?>(null)
        private set

    /** True when the line is picked for every message instead of once. */
    var askEachTime by mutableStateOf(simPrefs.getBoolean("ask_every_time", false))
        private set

    fun selectSim(subscriptionId: Int?) {
        selectedSubscription = subscriptionId
        askEachTime = false
        simPrefs.edit().putBoolean("ask_every_time", false).apply()
    }

    fun askEveryTime() {
        selectedSubscription = null
        askEachTime = true
        simPrefs.edit().putBoolean("ask_every_time", true).apply()
    }

    val requiredPermissions = listOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.WRITE_CALL_LOG,
    )

    fun hasCorePermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun isDefaultSmsApp(): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
        }
        return Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }

    /** Which "make me the default" surface is wanted. */
    enum class DefaultApp { Sms, Dialer }

    /**
     * Every way to reach the system dialog that hands Trinity a default role, best
     * first: the modern role request, the legacy change-default intent, then the
     * system pages that list default apps. Devices disagree about which one they
     * honour, so the caller walks the list instead of trusting a single intent.
     */
    fun defaultAppRequests(kind: DefaultApp): List<Intent> {
        val wanted = mutableListOf<Intent>()
        val role = if (kind == DefaultApp.Sms) RoleManager.ROLE_SMS else RoleManager.ROLE_DIALER
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null &&
            runCatching { roleManager.isRoleAvailable(role) }.getOrDefault(false)
        ) {
            // The role dialog takes the requesting package from the intent. Android
            // 14+ PermissionController closes itself with "Package name cannot be
            // null or empty" when it is absent, so state it explicitly instead of
            // trusting the system to infer it.
            runCatching { roleManager.createRequestRoleIntent(role) }.getOrNull()
                ?.putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
                ?.let { wanted += it }
        }
        if (kind == DefaultApp.Sms) {
            @Suppress("DEPRECATION")
            wanted += Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        } else {
            wanted += Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
        wanted += defaultAppsSettingsIntent()
        wanted += Intent(android.provider.Settings.ACTION_SETTINGS)
        wanted += Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )
        return wanted
    }

    /**
     * Opens the first request this device accepts. False only when nothing opened.
     * [launcher] is the Activity doing the asking: the system records the requesting
     * package from the caller, and a bare application context does not carry one.
     */
    fun requestDefaultApp(kind: DefaultApp, launcher: Context? = null): Boolean {
        val from = launcher ?: context
        return defaultAppRequests(kind).any { intent ->
            runCatching {
                if (from is Activity) from.startActivity(intent)
                else from.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }

    /** Whether Trinity already holds the role behind [kind]. */
    fun holdsDefault(kind: DefaultApp): Boolean =
        if (kind == DefaultApp.Sms) isDefaultSmsApp() else isDefaultDialer()

    /** True when something on this device can actually open [intent]. */
    fun canHandle(intent: Intent?): Boolean =
        intent != null &&
            runCatching { context.packageManager.queryIntentActivities(intent, 0) }.getOrNull().orEmpty().isNotEmpty()

    /** The system page that lists default apps, for when a role dialog will not open. */
    fun defaultAppsSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(android.provider.Settings.ACTION_SETTINGS)
        }

    fun refresh() {
        scope.launch {
            loading = true
            val rules = blockStore.rules.value
            smsProviderMissing = !messagesRepo.hasProvider()
            val people = contactsRepo.load()
            contacts = people
            val names = people.associate { it.number to it.name }
            conversations = withLocalRead(
                messagesRepo.conversations(names, rules, threadPrefs.mutedThreads()),
            )
            unreadTotal = conversations.sumOf { it.unread }
            simCards = sims.active()
            defaultSubscription = sims.defaultVoiceSubscriptionId()
            calls = callsRepo.load(rules = rules, names = names, cards = simCards)
            blockedMessages = messagesRepo.blockedMessages(names, rules)
            held = quarantine.all()
            openThreadId?.let { id -> messages = messagesRepo.messages(id, rules) }
            loading = false
        }
    }

    fun openThread(conversation: Conversation) {
        openThreadId = conversation.threadId
        messages = emptyList()
        // Rows kept in memory from a device where Trinity does not hold the SMS
        // role are replaced by whatever the provider actually has.
        pending = pending.filterNot { BlockMatcher.sameNumber(it.address, conversation.address) }
        scope.launch {
            val rules = blockStore.rules.value
            messagesRepo.markThreadRead(conversation.threadId)
            messages = messagesRepo.messages(conversation.threadId, rules)
            messages.maxOfOrNull { it.date }?.let { newest ->
                threadPrefs.markSeen(conversation.threadId, newest)
            }
            MessageNotifier.cancel(context, conversation.threadId)
            conversations = withLocalRead(
                messagesRepo.conversations(
                    contacts.associate { it.number to it.name },
                    rules,
                    threadPrefs.mutedThreads(),
                ),
            )
            unreadTotal = conversations.sumOf { it.unread }
        }
    }

    fun closeThread() {
        openThreadId = null
        messages = emptyList()
    }

    /** A conversation for a thread id, known or not — notifications open threads cold. */
    fun conversationFor(threadId: Long, address: String): Conversation =
        conversations.firstOrNull { it.threadId == threadId }
            ?: Conversation(
                threadId = threadId,
                address = address,
                name = contacts.firstOrNull { BlockMatcher.sameNumber(it.number, address) }?.name,
                snippet = "",
                date = 0L,
                unread = 0,
                rule = null,
            )

    fun draftFor(threadId: Long): String = threadPrefs.draft(threadId)

    fun setDraft(threadId: Long, text: String) = threadPrefs.setDraft(threadId, text)

    fun toggleMute(threadId: Long) {
        threadPrefs.setMuted(threadId, !threadPrefs.isMuted(threadId))
        refresh()
    }

    fun markRead(threadId: Long) {
        scope.launch {
            messages.maxOfOrNull { it.date }?.let { threadPrefs.markSeen(threadId, it) }
            messagesRepo.markThreadRead(threadId)
            MessageNotifier.cancel(context, threadId)
            refresh()
        }
    }

    fun markUnread(threadId: Long) {
        scope.launch {
            threadPrefs.clearSeen(threadId)
            messagesRepo.markThreadUnread(threadId)
            refresh()
        }
    }

    fun deleteMessage(id: Long) {
        scope.launch {
            messagesRepo.deleteMessage(id)
            refresh()
        }
    }

    fun saveContact(name: String, numbers: List<String>, email: String, onDone: (Boolean) -> Unit) {
        scope.launch {
            val id = withContext(Dispatchers.IO) { ContactWriter.save(context, name, numbers, email) }
            refresh()
            onDone(id != null)
        }
    }

    fun updateContact(
        person: Person,
        name: String,
        numbers: List<String>,
        email: String,
        onDone: (Boolean) -> Unit,
    ) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                ContactWriter.update(context, person, name, numbers, email)
            }
            refresh()
            onDone(ok)
        }
    }

    fun deleteContact(person: Person, onDone: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { ContactWriter.delete(context, person.id) }
            refresh()
            onDone()
        }
    }

    /** Result of the last contact file import or export, shown once in Settings. */
    var fileNote by mutableStateOf<String?>(null)
        private set

    fun exportContacts(uri: Uri) {
        scope.launch {
            val count = withContext(Dispatchers.IO) { VCards.export(context, uri, contacts) }
            fileNote = if (count > 0) "$count exported" else "Nothing to export"
            refresh()
        }
    }

    fun importContacts(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { VCards.import(context, uri) }
            refresh()
            fileNote = when {
                result.added > 0 && result.skipped > 0 ->
                    "${result.added} imported · ${result.skipped} skipped"
                result.added > 0 -> "${result.added} imported"
                else -> "No contacts in that file"
            }
        }
    }

    fun clearFileNote() {
        fileNote = null
    }

    /** How many calls the log holds for a number, for the contact screen. */
    fun callCountFor(number: String): Int =
        calls.count { BlockMatcher.sameNumber(it.number, number) }

    /** That contact's own slice of the call log, newest first. */
    fun callsFor(number: String): List<CallEntry> =
        calls.filter { BlockMatcher.sameNumber(it.number, number) }

    /** The SMS thread with this number, when there is one. */
    fun conversationForNumber(number: String): Conversation? =
        conversations.firstOrNull { BlockMatcher.sameNumber(it.address, number) }

    fun setContactPhoto(person: Person, source: Uri, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ContactPhotos.set(context, person, source) }
            refresh()
            onDone(ok)
        }
    }

    fun removeContactPhoto(person: Person, onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ContactPhotos.remove(context, person) }
            refresh()
            onDone(ok)
        }
    }

    fun deleteThread(threadId: Long, onDone: () -> Unit = {}) {
        scope.launch {
            messagesRepo.deleteThread(threadId)
            if (openThreadId == threadId) closeThread()
            refresh()
            onDone()
        }
    }

    /** Several conversations at once, from a long-press selection. */
    fun deleteThreads(threadIds: Collection<Long>, onDone: () -> Unit = {}) {
        if (threadIds.isEmpty()) return
        scope.launch {
            threadIds.forEach { id ->
                messagesRepo.deleteThread(id)
                MessageNotifier.cancel(context, id)
                if (openThreadId == id) closeThread()
            }
            refresh()
            onDone()
        }
    }

    fun markThreadsRead(threadIds: Collection<Long>) {
        if (threadIds.isEmpty()) return
        scope.launch {
            val now = System.currentTimeMillis()
            threadIds.forEach { id ->
                messagesRepo.markThreadRead(id)
                threadPrefs.markSeen(id, now)
                MessageNotifier.cancel(context, id)
            }
            refresh()
        }
    }

    fun setThreadsMuted(threadIds: Collection<Long>, muted: Boolean) {
        if (threadIds.isEmpty()) return
        threadIds.forEach { threadPrefs.setMuted(it, muted) }
        refresh()
    }

    /** Deletes picked messages; rows still on their way out are dropped instead. */
    fun deleteMessages(ids: Collection<Long>) {
        val unsent = ids.filter { it < 0 }.map { -it }.toSet()
        if (unsent.isNotEmpty()) pending = pending.filterNot { it.localId in unsent }
        val stored = ids.filter { it > 0 }
        if (stored.isEmpty()) return
        scope.launch {
            stored.forEach { messagesRepo.deleteMessage(it) }
            refresh()
        }
    }

    /**
     * Sends a text and reports back the thread it belongs to, so a brand-new
     * conversation can be opened the moment it is sent.
     */
    fun sendMessage(
        address: String,
        body: String,
        subscriptionId: Int? = selectedSubscription,
        onSent: (Long?) -> Unit = {},
    ) {
        val text = body.trim()
        if (text.isBlank()) return
        val local = PendingMessage(
            localId = nextLocalId++,
            address = address,
            body = text,
            date = System.currentTimeMillis(),
            state = MessageState.SENDING,
        )
        pending = pending + local
        SmsSender.send(context, address, text, subscriptionId) { error ->
            scope.launch {
                if (error == null) {
                    val heldRole = holdsSmsRole(context)
                    if (heldRole) SmsStore.insertSent(context, address, text, local.date)
                    pending = if (heldRole) {
                        pending.filterNot { it.localId == local.localId }
                    } else {
                        // Without the SMS role nothing is written to the provider, so the
                        // row stays visible here rather than vanishing on the next refresh.
                        pending.map {
                            if (it.localId == local.localId) it.copy(state = MessageState.SENT) else it
                        }
                    }
                    refresh()
                    onSent(SmsStore.threadIdFor(context, address))
                } else {
                    pending = pending.map {
                        if (it.localId == local.localId) {
                            it.copy(state = MessageState.FAILED, reason = error)
                        } else {
                            it
                        }
                    }
                }
            }
        }
    }

    fun retryPending(message: PendingMessage) {
        pending = pending.filterNot { it.localId == message.localId }
        sendMessage(message.address, message.body)
    }

    fun dismissPending(message: PendingMessage) {
        pending = pending.filterNot { it.localId == message.localId }
    }

    /** Every message in the open thread, with messages still on their way appended. */
    fun threadMessages(address: String): List<Message> {
        val extra = pending
            .filter { BlockMatcher.sameNumber(it.address, address) }
            .map { it.asMessage() }
        return (messages + extra).sortedBy { it.date }
    }

    fun reveal(messageId: Long) {
        revealed = revealed + messageId
    }

    fun allowSender(conversation: Conversation) =
        allowSender(conversation.address, conversation.name)

    /**
     * Drops any name or number rule on this sender, vouches for them so keyword
     * rules stop applying, and clears the texts already held from them.
     */
    fun allowSender(address: String, name: String? = null) {
        val label = name ?: contacts.firstOrNull { it.number == address }?.name
        label?.let { blockStore.removeName(it) }
        blockStore.removeNumber(address)
        blockStore.addAllowed(address)
        quarantine.removeFor(address)
        refresh()
    }

    fun disallowSender(address: String) {
        blockStore.removeAllowed(address)
        refresh()
    }

    fun send(address: String, body: String) {
        sendMessage(address, body)
    }

    /**
     * Clears the badge on conversations read here. The provider refuses the read
     * flag without the SMS role, and a just-read thread must not bounce back on
     * the next refresh either way.
     */
    private fun withLocalRead(list: List<Conversation>): List<Conversation> {
        val seen = threadPrefs.seenThreads()
        return list.map { conversation ->
            val seenAt = seen[conversation.threadId] ?: 0L
            if (seenAt >= conversation.date) conversation.copy(unread = 0) else conversation
        }
    }

    /** Which SIM outgoing texts use, or null when the phone offers no choice. */
    fun sendSimLabel(): String? {
        if (simCards.isEmpty()) return null
        if (askEachTime) return "SIM · ASK"
        val chosen = simCards.firstOrNull { it.subscriptionId == selectedSubscription }
            ?: simCards.firstOrNull { it.subscriptionId == defaultSubscription }
            ?: simCards.singleOrNull()
        return chosen?.label ?: "SIM"
    }

    fun call(number: String, name: String? = null, subscriptionId: Int? = selectedSubscription) {
        val wanted = subscriptionId ?: defaultSubscription
        val card = simCards.firstOrNull { it.subscriptionId == wanted }
            ?: simCards.singleOrNull()
        // Telecom owns the call from here: it binds Trinity's InCallService, which is
        // what draws the in-call screen, so nothing of ours has to stay open.
        sims.placeCall(number, card)
    }

    fun messagePerson(number: String) {
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun addRule(raw: String, scope: Scope = Scope.Both) {
        if (raw.any { it.isDigit() } && raw.count { it.isDigit() } >= 5) {
            blockStore.addNumber(raw, scope)
        } else {
            blockStore.addName(raw, scope)
        }
        refresh()
    }

    fun addKeyword(raw: String) {
        blockStore.addKeyword(raw)
        refresh()
    }

    fun removeName(value: String) {
        blockStore.removeName(value)
        refresh()
    }

    fun removeNumber(value: String) {
        blockStore.removeNumber(value)
        refresh()
    }

    fun removeKeyword(value: String) {
        blockStore.removeKeyword(value)
        refresh()
    }

    fun setHideMatchedCalls(enabled: Boolean) {
        blockStore.setHideMatchedCalls(enabled)
        refresh()
    }

    /** True only when the OS says Trinity is the phone app. */
    fun isDefaultDialer(): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && roleManager != null) {
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            false
        }
    }

    /** How many of Trinity's permissions the user has actually granted. */
    fun grantedPermissionCount(): Int = requiredPermissions.count {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun clearHeld() {
        quarantine.clear()
        refresh()
    }

    fun resetRules() {
        blockStore.clearAll()
        refresh()
    }

    fun discardHeld(entry: QuarantineStore.Entry) {
        quarantine.removeAt(entry.date, entry.body)
        refresh()
    }

    fun deleteCall(id: Long) {
        scope.launch {
            callsRepo.delete(id)
            refresh()
        }
    }

    fun deleteCalls(ids: Collection<Long>, onDone: () -> Unit = {}) {
        if (ids.isEmpty()) return
        scope.launch {
            ids.forEach { callsRepo.delete(it) }
            refresh()
            onDone()
        }
    }

    fun deleteContacts(people: Collection<Person>, onDone: () -> Unit = {}) {
        if (people.isEmpty()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                people.forEach { ContactWriter.delete(context, it.id) }
            }
            refresh()
            onDone()
        }
    }

    fun clearCalls() {
        scope.launch {
            callsRepo.deleteAll()
            refresh()
        }
    }
}
