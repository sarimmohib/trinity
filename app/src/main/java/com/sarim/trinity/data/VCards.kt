package com.sarim.trinity.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Contacts in and out as a .vcf file, through whatever document the user picks.
 *
 * The address book stays the source of truth: a file is only a door. Export
 * writes vCard 3.0 with every number and email; import reads the same format and
 * hands each card to [ContactWriter], so imported people behave like any other.
 */
object VCards {

    data class ImportResult(val added: Int, val skipped: Int)

    data class Card(val name: String, val numbers: List<String>, val emails: List<String>)

    /** Writes every person to [uri]. Returns how many cards went out. */
    fun export(context: Context, uri: Uri, people: List<Person>): Int {
        val cards = people.filter { person ->
            person.name.isNotBlank() || person.allNumbers.any { it.number.isNotBlank() }
        }
        if (cards.isEmpty()) return 0
        val text = buildString {
            cards.forEach { person ->
                val parts = person.name.trim().split(' ').filter { it.isNotBlank() }
                val given = parts.firstOrNull().orEmpty()
                val family = parts.drop(1).joinToString(" ")
                append("BEGIN:VCARD\r\n")
                append("VERSION:3.0\r\n")
                append("N:${escape(family)};${escape(given)};;;\r\n")
                append("FN:${escape(person.name.trim())}\r\n")
                person.allNumbers
                    .map { it.number.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { number -> append("TEL;TYPE=${vcardType(person, number)}:$number\r\n") }
                person.emails
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { email -> append("EMAIL;TYPE=HOME:$email\r\n") }
                append("END:VCARD\r\n")
            }
        }
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return 0
        }.isSuccess
        return if (written) cards.size else 0
    }

    /** Reads every card in [uri] into the address book. */
    fun import(context: Context, uri: Uri): ImportResult {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                String(stream.readBytes(), Charsets.UTF_8)
            }
        }.getOrNull() ?: return ImportResult(0, 0)

        var added = 0
        var skipped = 0
        parse(text).forEach { card ->
            val id = ContactWriter.save(
                context = context,
                name = card.name,
                numbers = card.numbers,
                email = card.emails.firstOrNull().orEmpty(),
            )
            if (id != null && id > 0L) added++ else skipped++
        }
        return ImportResult(added, skipped)
    }

    fun parse(text: String): List<Card> {
        val lines = unfold(text)
        val cards = mutableListOf<Card>()
        var fullName = ""
        var partName = ""
        val numbers = mutableListOf<String>()
        val emails = mutableListOf<String>()
        var inside = false

        fun flush() {
            if (!inside) return
            val label = fullName.trim().ifBlank { partName.trim() }
            if (label.isNotBlank() || numbers.isNotEmpty()) {
                cards += Card(label, numbers.toList(), emails.toList())
            }
            fullName = ""
            partName = ""
            numbers.clear()
            emails.clear()
            inside = false
        }

        lines.forEach { line ->
            val upper = line.uppercase()
            when {
                upper.startsWith("BEGIN:VCARD") -> inside = true
                upper.startsWith("END:VCARD") -> flush()
                !inside -> Unit
                upper.startsWith("FN") && line.contains(':') -> {
                    val value = decode(line.substringAfter(':'))
                    if (value.isNotBlank()) fullName = value
                }
                upper.startsWith("N:") || upper.startsWith("N;") -> {
                    // N:Family;Given;Additional;Prefix;Suffix
                    val value = line.substringAfter(':')
                    val family = value.substringBefore(';')
                    val given = value.substringAfter(';').substringBefore(';')
                    partName = listOf(decode(given), decode(family))
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                }
                upper.startsWith("TEL") -> {
                    val value = decode(line.substringAfter(':', ""))
                    if (value.isNotBlank()) numbers += value
                }
                upper.startsWith("EMAIL") -> {
                    val value = decode(line.substringAfter(':', ""))
                    if (value.isNotBlank()) emails += value
                }
            }
        }
        // A card that was never closed still counts.
        flush()
        return cards
    }

    /** Joins folded lines and quoted-printable soft breaks back together. */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        text.split("\r\n", "\n", "\r").forEach { physical ->
            when {
                out.isEmpty() -> out += physical
                out.last().endsWith("=") -> {
                    out[out.lastIndex] = out.last().dropLast(1) + physical.trimStart()
                }
                physical.startsWith(" ") || physical.startsWith("\t") -> {
                    out[out.lastIndex] = out.last() + physical.trimStart()
                }
                else -> out += physical
            }
        }
        return out.filter { it.isNotBlank() }
    }

    private val quotedPrintable = Regex("=[0-9A-Fa-f]{2}")

    private fun decode(raw: String): String {
        val unquoted =
            if (quotedPrintable.containsMatchIn(raw)) decodeQuotedPrintable(raw) else raw
        return unquoted
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun decodeQuotedPrintable(raw: String): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < raw.length) {
            val current = raw[i]
            if (
                current == '=' && i + 2 < raw.length &&
                raw[i + 1].isHexDigit() && raw[i + 2].isHexDigit()
            ) {
                out.write(raw.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(current.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\n", "\\n")

    private fun vcardType(person: Person, number: String): String {
        val label = person.allNumbers
            .firstOrNull { it.number.trim() == number }
            ?.label
            ?.uppercase()
        return when (label) {
            "MOBILE" -> "CELL"
            "HOME" -> "HOME"
            "WORK" -> "WORK"
            "FAX" -> "FAX"
            "MAIN" -> "MAIN"
            else -> "VOICE"
        }
    }
}
