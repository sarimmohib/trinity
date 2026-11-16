package com.sarim.trinity.mms

/**
 * Recipient parsing for MMS. MMS address fields use both commas and semicolons as
 * separators (carriers are not consistent), so both are accepted here. Everything in
 * this object is pure JVM code — no provider or telephony calls — so the parsing and
 * group label can be unit-tested directly. Thread grouping itself is the system MMS
 * provider's `THREAD_ID`; this class does not try to duplicate it.
 */
object MmsAddresses {

    /** Split on `,` and `;`, trim, drop blanks and repeated addresses. */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy(::normalise)
    }

    /** Header label: empty, the number itself, or "N PEOPLE" for a group. */
    fun format(recipients: List<String>): String = when {
        recipients.isEmpty() -> ""
        recipients.size == 1 -> recipients.first()
        else -> "${recipients.size} PEOPLE"
    }

    /**
     * Case, spacing and phone punctuation do not change a thread. Numbers are reduced to
     * their national form so `0300 771-2008` and `+92 3007712008` are the same person —
     * carriers store MMS recipients in either shape. Non-numeric addresses (SIP ids,
     * for example) just lose case and punctuation.
     */
    private fun normalise(value: String): String {
        val clean = value.trim().lowercase().replace(Regex("[\\s\\-()]"), "")
        val digits = clean.filter { it.isDigit() }
        if (digits.length < 10) return clean
        val national = when {
            digits.startsWith("92") && digits.length > 10 -> digits.drop(2)
            digits.startsWith("0") && digits.length > 10 -> digits.drop(1)
            else -> digits
        }
        return if (national.length > 10) national.takeLast(10) else national
    }
}
