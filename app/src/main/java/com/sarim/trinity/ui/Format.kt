package com.sarim.trinity.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clock = DateTimeFormatter.ofPattern("H:mm", Locale.getDefault())
private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** "9:24" today, "Yesterday", "Tue", then a short date. */
fun relativeStamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = moment.toLocalDate()
    return when {
        day == today -> moment.format(clock)
        day == today.minusDays(1) -> "Yesterday"
        day.isAfter(today.minusDays(7)) -> moment.format(weekday)
        else -> "${moment.dayOfMonth}/${moment.monthValue}"
    }
}

fun absoluteStamp(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val moment = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return moment.format(DateTimeFormatter.ofPattern("d MMM, H:mm", Locale.getDefault()))
}

/** TODAY / YESTERDAY / 6 Nov — the header a call list groups under. */
fun dayLabel(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        day == today -> "TODAY"
        day == today.minusDays(1) -> "YESTERDAY"
        day.year == today.year -> day.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        else -> day.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
}

/** A stable day key, used to split the log into day groups. */
fun dayKey(epochMillis: Long): Long =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

/**
 * How many SMS parts a body needs. Plain ASCII rides the 160-char 7-bit limit
 * (153 per part once it splits); anything else has to be UCS-2.
 */
fun smsParts(body: String): Int {
    if (body.isEmpty()) return 1
    val asciiOnly = body.all { it.code in 32..126 }
    val single = if (asciiOnly) 160 else 70
    val multi = if (asciiOnly) 153 else 67
    return if (body.length <= single) 1 else (body.length + multi - 1) / multi
}

/** "154/160 · 1 PART" — the counter a composer shows as the limit approaches. */
fun smsCounter(body: String): String {
    val asciiOnly = body.all { it.code in 32..126 }
    val single = if (asciiOnly) 160 else 70
    val parts = smsParts(body)
    val limit = if (parts == 1) single else (if (asciiOnly) 153 else 67) * parts
    return "${body.length}/$limit · $parts PART${if (parts == 1) "" else "S"}"
}

/**
 * Display grouping only — stored digits never change. "03007712008" reads as
 * "0300 771 2008"; a leading + is kept in front of the grouping.
 */
fun formatNumber(raw: String): String {
    val trimmed = raw.trim()
    // Service codes (*786#, *#06#) are not phone numbers: grouping them would drop the
    // stars and hashes, so show exactly what was typed.
    if (trimmed.any { it == '*' || it == '#' }) return trimmed
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
