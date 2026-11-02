package com.ivor.openstream.data.streaming.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StremioExternalMediaTest {
    @Test fun directMp4ExternalUrlCanUseNativePlayer() {
        assertEquals(
            "https://example.com/my-video.mp4?signature=abc",
            directMediaExternalUrl("https://example.com/my-video.mp4?signature=abc")
        )
    }

    @Test fun directHlsExternalUrlCanUseNativePlayer() {
        assertEquals(
            "https://example.com/stream/master.m3u8",
            directMediaExternalUrl("https://example.com/stream/master.m3u8")
        )
    }

    @Test fun ordinaryPagesStayExternalAndNeverMasqueradeAsStreams() {
        assertNull(directMediaExternalUrl("https://example.com/watch/episode-1"))
        assertNull(directMediaExternalUrl("https://example.com/login"))
        assertNull(directMediaExternalUrl("https://example.com/embedded/player.html"))
    }

    @Test fun unsupportedProtocolsCannotLaunchLocalFilesOrScripts() {
        assertNull(directMediaExternalUrl("file:///sdcard/movie.mp4"))
        assertNull(directMediaExternalUrl("javascript:alert(1)"))
        assertNull(directMediaExternalUrl("magnet:?xt=urn:btih:example"))
    }
}
