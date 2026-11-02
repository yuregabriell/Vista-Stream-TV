package com.ivor.openstream.data.streaming.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VidkingSeedRequestTest {
    @Test
    fun seedHandshakeIncludesPlaybackOriginAndReferer() {
        val request = vidkingSeedRequest("https://example.org/", 603)
        assertEquals("https://example.org/seed?mediaId=603", request.url.toString())
        assertEquals("https://www.vidking.net", request.header("Origin"))
        assertEquals("https://www.vidking.net/", request.header("Referer"))
        assertEquals("application/json", request.header("Accept"))
        assertTrue(request.url.isHttps)
    }
}
