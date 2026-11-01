package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cached streams must never outlive their window or answer for a different set of sources. */
class ResolvedStreamCacheTest {
    private var now = 1_000_000L
    private val cache = ResolvedStreamCache { now }
    private val episode = MediaIdentity(tmdbId = 1399, tmdbType = "tv", title = "Show", season = 1, episode = 2)
    private val found = ServerResolution(
        servers = listOf(VideoServer("s", "p", "P", "S", "https://example.com/a.m3u8", StreamQuality.Q1080)),
        completedProviders = 1,
        totalProviders = 1,
        isComplete = true
    )

    @Test
    fun answersForTheSameEpisodeAndSourcesWithinTheWindow() {
        cache.put(episode, "p@1", found)
        now += ResolvedStreamCache.TTL_MS - 1
        assertEquals(found, cache.get(episode, "p@1"))
    }

    @Test
    fun expiresAfterTheWindow() {
        cache.put(episode, "p@1", found)
        now += ResolvedStreamCache.TTL_MS
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun anotherEpisodeOrChangedSourcesMiss() {
        cache.put(episode, "p@1", found)
        assertNull(cache.get(episode.copy(episode = 3), "p@1"))
        assertNull(cache.get(episode, "p@2"))
        // The mismatch also dropped it.
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun keepsOnlyCompleteSearchesThatFoundSomething() {
        cache.put(episode, "p@1", found.copy(isComplete = false))
        assertNull(cache.get(episode, "p@1"))
        cache.put(episode, "p@1", found.copy(servers = emptyList()))
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun forgetDropsAFailedEpisode() {
        cache.put(episode, "p@1", found)
        cache.forget(episode)
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun failingOneMirrorPreservesOtherCandidatesWithOriginalTtl() {
        val extra = VideoServer("s2", "p2", "P2", "S2", "https://example.com/b.m3u8", StreamQuality.Q720)
        val multiple = found.copy(servers = found.servers + extra)
        cache.put(episode, "p@1", multiple)
        now += 20_000L
        cache.forgetServer(episode, "s")
        assertEquals(listOf(extra), cache.get(episode, "p@1")?.servers)
        // Evicting a broken source must not extend the TTL of the remaining URL.
        now += ResolvedStreamCache.TTL_MS - 20_000L
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun evictsEntryWhenItsOnlyCandidateFails() {
        cache.put(episode, "p@1", found)
        cache.forgetServer(episode, "s")
        assertNull(cache.get(episode, "p@1"))
    }

    @Test
    fun staysBounded() {
        repeat(ResolvedStreamCache.MAX_ENTRIES + 1) { index -> cache.put(episode.copy(episode = index), "p@1", found) }
        assertNull(cache.get(episode.copy(episode = 0), "p@1"))
        assertEquals(found, cache.get(episode.copy(episode = ResolvedStreamCache.MAX_ENTRIES), "p@1"))
    }

    @Test
    fun aClockGoingBackwardsIsNotTrusted() {
        cache.put(episode, "p@1", found)
        now -= 1
        assertNull(cache.get(episode, "p@1"))
    }
}
