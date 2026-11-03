package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.domain.model.MediaIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioMagnetCompatibilityTest {
    private val hex = "7de3d30b6859287fa79bd0e203c648e80d2f5279"
    private val base32 = "PXR5GC3ILEUH7J432DRAHRSI5AGS6UTZ"

    @Test fun acceptsAndNormalizesBothBtihFormats() {
        assertEquals(hex, stremioBtih(hex.uppercase()))
        assertEquals(hex, stremioBtih(base32.lowercase()))
        assertEquals(hex, stremioBtihFromMagnet("magnet:?xt=urn:btih:" + base32))
        assertNull(stremioBtih("truncated"))
        assertNull(stremioBtih("Z".repeat(40)))
    }

    @Test fun filtersCorruptedTrackerStrings() {
        val trackers = stremioTrackerUrls(listOf(
            "tracker:udp://tracker.opentrackr.org:1337/announce",
            "tracker:udp://tracker.opentrackr.org:1337/announce",
            "tracker:httpstracker.example.org443announce",
            "dht:example.com"
        ))
        assertEquals(listOf("udp://tracker.opentrackr.org:1337/announce"), trackers)
        val magnet = stremioMagnet(base32, trackers)!!
        assertTrue(magnet.contains("xt=urn:btih:" + hex))
        assertTrue(magnet.contains("tr=udp%3A%2F%2Ftracker.opentrackr.org"))
        assertEquals(hex, stremioBtihFromMagnet(magnet))
    }

    @Test fun extractsTrackersFromOriginalMagnetQuery() {
        val trackers = stremioTrackerUrls(emptyList(),
            "magnet:?xt=urn:btih:" + hex +
                "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce")
        assertEquals(1, trackers.size)
        assertEquals("udp://tracker.opentrackr.org:1337/announce", trackers[0])
    }

    @Test fun mapsOnlyMatchingCatalogIdsAndEpisodes() {
        val series = MediaIdentity(
            tmdbId = 731, tmdbType = "tv", imdbId = "tt1234567",
            title = "Example Show", season = 1, episode = 4
        )
        assertEquals(listOf("tt1234567:1:4", "tmdb:731:1:4"),
            stremioCandidateIds(series, listOf("tt", "tmdb:")))
        assertEquals(listOf("tmdb:731:1:4"),
            stremioCandidateIds(series.copy(imdbId = null), listOf("tt", "tmdb:")))
        assertEquals(emptyList<String>(),
            stremioCandidateIds(series.copy(imdbId = null, tmdbId = 0), listOf("tt", "tmdb:")))
        assertEquals(listOf("tt1234567"),
            stremioCandidateIds(series.copy(tmdbType = "movie"), listOf("tt")))
        assertFalse(stremioCandidateIds(series.copy(episode = 0), listOf("tt")).isNotEmpty())
    }
}
