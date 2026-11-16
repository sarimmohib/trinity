package com.sarim.trinity.mms

import android.content.ContextWrapper
import com.sarim.trinity.mms.pdu.CharacterSets
import com.sarim.trinity.mms.pdu.EncodedStringValue
import com.sarim.trinity.mms.pdu.GenericPdu
import com.sarim.trinity.mms.pdu.PduBody
import com.sarim.trinity.mms.pdu.PduComposer
import com.sarim.trinity.mms.pdu.PduPart
import com.sarim.trinity.mms.pdu.PduParser
import com.sarim.trinity.mms.pdu.SendReq
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vendored AOSP codec is the highest-risk piece of M1. These tests compose a PDU
 * and parse it back without Android framework services, so a broken codec fails here
 * rather than on a phone. No provider, telephony or network is involved.
 */
class PduRoundTripTest {

    /** PduComposer only needs getContentResolver(), and parts here carry inline data. */
    private class FakeContext : ContextWrapper(null)

    @Test
    fun sendReqWithTextAndImagePartsRoundTrips() {
        val imageBytes = byteArrayOf(0x37, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val textBytes = "Hello from Trinity".toByteArray(Charsets.UTF_8)

        val textPart = PduPart().apply {
            contentType = "text/plain".toByteArray(Charsets.US_ASCII)
            charset = CharacterSets.UTF_8
            setName("text.txt".toByteArray(Charsets.US_ASCII))
            setData(textBytes)
        }
        val imagePart = PduPart().apply {
            contentType = "image/png".toByteArray(Charsets.US_ASCII)
            setName("photo.png".toByteArray(Charsets.US_ASCII))
            setData(imageBytes)
        }
        val body = PduBody().apply {
            addPart(textPart)
            addPart(imagePart)
        }
        val request = SendReq().apply {
            to = arrayOf(EncodedStringValue("03001234567".toByteArray(Charsets.US_ASCII)))
            subject = EncodedStringValue("A photo".toByteArray(Charsets.UTF_8))
            setBody(body)
        }

        val encoded = PduComposer(FakeContext(), request).make()
        assertNotNull("PduComposer returned null", encoded)
        assertTrue("Empty PDU", encoded!!.isNotEmpty())

        val parsed = PduParser(encoded, true).parse()
        assertTrue("Expected a SendReq", parsed is SendReq)
        parsed as SendReq
        assertEquals("03001234567", parsed.to.firstOrNull()?.string)
        assertEquals("A photo", parsed.subject?.string)

        val parsedBody = parsed.body
        assertNotNull(parsedBody)
        assertEquals(2, parsedBody.partsNum)
        assertArrayEquals(textBytes, parsedBody.getPart(0).data)
        assertArrayEquals(imageBytes, parsedBody.getPart(1).data)
    }

    @Test
    fun parseRejectsTruncatedPdu() {
        val result: GenericPdu? = PduParser(byteArrayOf(0x8c.toByte(), 0x80.toByte()), true).parse()
        assertEquals(null, result)
    }
}
