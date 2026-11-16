package com.sarim.trinity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsLinksTest {

    @Test
    fun address_parsesEverySupportedScheme() {
        assertEquals("03007712008", SmsLinks.address("sms:03007712008"))
        assertEquals("03007712008", SmsLinks.address("smsto:03007712008"))
        assertEquals("03007712008", SmsLinks.address("mms:03007712008"))
        assertEquals("03007712008", SmsLinks.address("mmsto:03007712008"))
    }

    @Test
    fun address_acceptsUppercaseScheme() {
        assertEquals("03007712008", SmsLinks.address("SMS:03007712008"))
    }

    @Test
    fun address_stripsBodyQuery() {
        assertEquals("03007712008", SmsLinks.address("sms:03007712008?body=Hello%20there"))
    }

    @Test
    fun address_stripsOtherQueryAndFragment() {
        assertEquals("03007712008", SmsLinks.address("sms:03007712008?foo=bar#frag"))
    }

    @Test
    fun address_decodesPercentEscapes() {
        assertEquals("+923007712008", SmsLinks.address("sms:%2B923007712008?body=hi"))
        assertEquals("+923007712008", SmsLinks.address("smsto:+923007712008"))
    }

    @Test
    fun address_returnsFirstRecipient() {
        assertEquals("03007712008", SmsLinks.address("sms:03007712008;03009998888"))
        assertEquals("03007712008", SmsLinks.address("mms:03007712008,03009998888"))
    }

    @Test
    fun address_rejectsUnknownScheme() {
        assertNull(SmsLinks.address("tel:03007712008"))
        assertNull(SmsLinks.address("http://example.com/sms:03007712008"))
    }

    @Test
    fun address_rejectsNullBlankAndEmpty() {
        assertNull(SmsLinks.address(null))
        assertNull(SmsLinks.address(""))
        assertNull(SmsLinks.address("   "))
        assertNull(SmsLinks.address("sms:"))
        assertNull(SmsLinks.address("no-colon-here"))
    }
}
