package com.ivor.openstream.data.streaming.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StremioTorrentIndexTest {
    @Test fun stremioZeroBasedIndexMapsToTorrServerFileId() {
        assertEquals(1, stremioToTorrServerIndex(0))
        assertEquals(2, stremioToTorrServerIndex(1))
        assertNull(stremioToTorrServerIndex(-1))
        assertNull(stremioToTorrServerIndex(10000))
    }

    @Test fun choosesLargestVideoNotLargestNonVideoFile() {
        val status = """
            {
              "file_stats": [
                {"id": 1, "path": "readme.txt", "length": 1000000000},
                {"id": 2, "path": "Episode1.mkv", "length": 42000000},
                {"id": 3, "path": "extras/Trailer.mp4", "length": 200000}
              ]
            }
        """.trimIndent()
        assertEquals(2, largestTorrServerVideoIndex(status))
    }

    @Test fun rejectsEmptyOrUnrecognizedTorrentMetadata() {
        assertNull(largestTorrServerVideoIndex("not-json"))
        assertNull(largestTorrServerVideoIndex("""{"file_stats":[{"id":1,"path":"setup.exe","length":12000}]}"""))
    }
}
