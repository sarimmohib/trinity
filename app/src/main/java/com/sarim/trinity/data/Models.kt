package com.sarim.trinity.data

/** One stored number with the label the address book gave it. */
data class PhoneEntry(val number: String, val label: String = "MOBILE")

/** A row in the contacts list. */
data class Person(
    val id: Long,
    val name: String,
    val number: String,
    val rawContactId: Long = -1L,
    val numbers: List<PhoneEntry> = emptyList(),
    val emails: List<String> = emptyList(),
    /** The provider's photo for this contact, when one is set. */
    val photoUri: String? = null,
) {
    val initials: String
        get() = name.trim().split(" ", "  ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "#" }

    /** Every number this person has, with a single-number fallback. */
    val allNumbers: List<PhoneEntry>
        get() = numbers.ifEmpty { listOf(PhoneEntry(number)) }

    /** The letter this person files under; "#" for names that start with neither. */
    val section: Char
        get() = name.trim().firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
}

/** One SMS conversation. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val rule: RuleHit?,
    /** True when the last thing said in the thread was said by the user. */
    val lastOutgoing: Boolean = false,
    val muted: Boolean = false,
) {
    /** Numbers are grouped for reading; stored digits are never rewritten. */
    val shownNumber: String get() = formatDisplayNumber(address)

    val displayName: String get() = name ?: address
    val initials: String
        get() = (name ?: address).trim().split(" ", "  ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "#" }
}

/** Where a message is in its journey. Outgoing messages move sending → sent or failed. */
enum class MessageState { RECEIVED, SENDING, SENT, FAILED }

/** One message inside a conversation. */
data class Message(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val rule: RuleHit?,
    val state: MessageState = if (outgoing) MessageState.SENT else MessageState.RECEIVED,
) {
    val isPending: Boolean get() = state == MessageState.SENDING || state == MessageState.FAILED
}

/** A message the user just sent, before the provider has agreed to keep it. */
data class PendingMessage(
    val localId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val state: MessageState,
    val reason: String? = null,
) {
    /** Pending rows borrow the negative id space so they never collide with provider ids. */
    fun asMessage(): Message = Message(
        id = -localId,
        address = address,
        body = body,
        date = date,
        outgoing = true,
        rule = null,
        state = state,
    )
}

/**
 * Grouping digits is a display concern, so it lives with the data model rather
 * than in one screen: "03007712008" reads as "0300 771 2008".
 */
internal fun formatDisplayNumber(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isEmpty()) return trimmed
    if (digits.length <= 4) return if (trimmed.startsWith("+")) "+$digits" else digits
    val head = digits.dropLast(7)
    val tail = digits.takeLast(7)
    val grouped = if (head.isEmpty()) {
        "${tail.take(3)} ${tail.drop(3)}"
    } else {
        "$head ${tail.take(3)} ${tail.drop(3)}"
    }
    return if (trimmed.startsWith("+")) "+$grouped" else grouped
}

/** One call-log entry. */
data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val outgoing: Boolean,
    val missed: Boolean,
    val date: Long,
    val durationSeconds: Long,
    val rule: RuleHit?,
    /** Which SIM carried it, on a dual-SIM phone. Null when there is nothing to say. */
    val simLabel: String? = null,
) {
    val displayName: String get() = name ?: number

    /** A number has no initials, so unknowns get the same mark the dial pad uses. */
    val initials: String
        get() = name?.let { saved ->
            saved.trim().split(" ", "  ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercase() }
                .ifBlank { "#" }
        } ?: "#"
    val detail: String
        get() {
            val kind = when {
                missed -> "Missed"
                outgoing -> "Outgoing"
                else -> "Incoming"
            }
            if (durationSeconds <= 0L) return "$kind · no answer"
            return "$kind · ${durationSeconds / 60}m ${durationSeconds % 60}s"
        }
}

/** What blocked a message or call, if anything. */
data class RuleHit(
    val kind: Kind,
    val value: String,
) {
    enum class Kind { NAME, NUMBER, KEYWORD }

    val label: String
        get() = when (kind) {
            Kind.NAME -> "NAME RULE"
            Kind.NUMBER -> "NUMBER RULE"
            Kind.KEYWORD -> "KEYWORD RULE"
        }
}
