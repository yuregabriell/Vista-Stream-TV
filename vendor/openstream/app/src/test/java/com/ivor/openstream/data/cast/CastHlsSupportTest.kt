package com.ivor.openstream.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastHlsSupportTest {
    @Test fun recognizesStandardAndAppleHlsMimeTypes() {
        for (mime in listOf(
            "application/x-mpegURL",
            "application/vnd.apple.mpegurl",
            "APPLICATION/VND.APPLE.MPEGURL; charset=utf-8",
            "audio/x-mpegurl",
            "audio/mpegurl"
        )) {
            assertTrue("Failed HLS MIME: $mime", CastHlsSupport.isHlsMime(mime))
        }
        assertFalse(CastHlsSupport.isHlsMime("video/mp4"))
    }

    @Test fun recognizesSignedHlsUrlWithoutFileExtension() {
        assertTrue(CastHlsSupport.hasHlsHint(
            "https://example.org/watch/stream?session=123",
            "application/vnd.apple.mpegurl"
        ))
        assertTrue(CastHlsSupport.hasHlsHint(
            "https://example.org/live/index.m3u8?token=abc",
            null
        ))
        assertFalse(CastHlsSupport.hasHlsHint("https://example.org/video.mp4", null))
    }

    @Test fun findsMasterVariantWithBlankLinesAndComments() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-STREAM-INF:BANDWIDTH=5000000

            # Comment before first stream
            media/1080.m3u8?auth=xyz
            #EXT-X-STREAM-INF:BANDWIDTH=2500000
            media/720.m3u8
        """.trimIndent()
        assertEquals("media/1080.m3u8?auth=xyz", CastHlsSupport.masterVariant(master))
    }

    @Test fun detectsFragmentedMp4WithMapTag() {
        assertEquals(CastHlsSupport.SEGMENT_FMP4, CastHlsSupport.segmentFormat(
            "#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:8,\nseg001.m4s"
        ))
    }

    @Test fun detectsTsSegmentsButDoesNotGuessUnknownFormat() {
        assertEquals(CastHlsSupport.SEGMENT_MPEG_TS, CastHlsSupport.segmentFormat(
            "#EXTM3U\n#EXTINF:8,\nseg001.ts?token=abc"
        ))
        assertNull(CastHlsSupport.segmentFormat("#EXTM3U\n#EXTINF:8,\nsegment?id=123"))
        assertNull(CastHlsSupport.segmentFormat(null))
    }
}
