package com.sarim.trinity.data

/**
 * Reads the address out of an sms:/smsto:/mms:/mmsto: link without touching any
 * Android class, so the parsing can be checked in a plain JVM unit test.
 */
object SmsLinks {

    private val schemes = listOf("sms", "smsto", "mms", "mmsto")

    /** Returns the destination address for an sms:/smsto:/mms:/mmsto: URI, or null. */
    fun address(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val text = uri.trim()
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val scheme = text.substring(0, colon)
        if (schemes.none { it.equals(scheme, ignoreCase = true) }) return null

        var part = text.substring(colon + 1)
        val query = part.indexOf('?')
        if (query >= 0) part = part.substring(0, query)
        val fragment = part.indexOf('#')
        if (fragment >= 0) part = part.substring(0, fragment)

        // A link may name several recipients; the composer starts with the first.
        val first = part.split(';', ',').firstOrNull().orEmpty()
        val decoded = first
            .replace("%2B", "+", ignoreCase = true)
            .replace("%20", " ", ignoreCase = true)
        return decoded.trim().takeIf { it.isNotEmpty() }
    }
}
