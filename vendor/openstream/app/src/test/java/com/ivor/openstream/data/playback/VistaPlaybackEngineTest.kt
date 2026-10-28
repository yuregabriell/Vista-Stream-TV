package com.ivor.openstream.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaPlaybackEngineTest {
    private fun sample(text: String) = text.toByteArray(Charsets.UTF_8)

    @Test fun diagnosesHlsWithoutAnM3u8Suffix() {
        val report = VistaPlaybackEngine.diagnose(
            "https://media.example/watch?sig=secret", null, 200, "application/octet-stream",
            sample("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nchunk.ts")
        )
        assertEquals(VistaPlaybackEngine.Format.HLS, report.format)
        assertEquals(VistaPlaybackEngine.Status.READY, report.status)
    }

    @Test fun rejectedSignedUrlIsNotReportedPlayable() {
        val report = VistaPlaybackEngine.diagnose(
            "https://media.example/source?sig=hidden", "application/x-mpegURL",
            403, "text/plain", ByteArray(0)
        )
        assertFalse(report.playableCandidate)
        assertEquals(403, report.httpCode)
        assertFalse(report.explanation.contains("hidden"))
    }

    @Test fun returnsFailureForHtmlVideoPlaceholder() {
        val report = VistaPlaybackEngine.diagnose(
            "https://media.example/video", null, 200, "application/octet-stream",
            sample("<!DOCTYPE html><html>Video unavailable</html>")
        )
        assertEquals(VistaPlaybackEngine.Status.INVALID_RESPONSE, report.status)
        assertFalse(report.playableCandidate)
    }

    @Test fun extractsMp4FromBytesBeforeIncorrectMime() {
        val report = VistaPlaybackEngine.diagnose(
            "https://media.example/stream", "application/vnd.apple.mpegurl", 206,
            "application/vnd.apple.mpegurl",
            byteArrayOf(0,0,0,24,102,116,121,112,105,115,111,109)
        )
        assertEquals(VistaPlaybackEngine.Format.MP4, report.format)
    }

    @Test fun unknownBinarySourceIsTriedWithoutGuessingMp4() {
        val report = VistaPlaybackEngine.diagnose(
            "https://media.example/opaque", null, 200, "application/octet-stream",
            byteArrayOf(1,2,3,4,5)
        )
        assertEquals(VistaPlaybackEngine.Format.UNKNOWN, report.format)
        assertTrue(report.playableCandidate)
    }

    @Test fun firstHlsSegmentRecognizedWithoutMasterVariant() {
        val media = "#EXTM3U\\n#EXT-X-TARGETDURATION:5\\n#EXTINF:5,\\nchunk1.ts?token=abc"
        assertEquals("chunk1.ts?token=abc", VistaPlaybackEngine.firstHlsSegment(media))
        val master = "#EXTM3U\\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\\nvariant.m3u8"
        assertEquals(null, VistaPlaybackEngine.firstHlsSegment(master))
    }

    @Test fun serverErrorsHaveSafeDiagnosisWithoutUrl() {
        val report = VistaPlaybackEngine.diagnose(
            "https://private.example/a?token=topsecret", null, 503, null, ByteArray(0)
        )
        assertEquals(VistaPlaybackEngine.Status.UNAVAILABLE, report.status)
        assertFalse(report.explanation.contains("topsecret"))
    }
}
