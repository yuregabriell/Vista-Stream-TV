package com.ivor.openstream.data.streaming

import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedStreamCacheProgressiveTest {
    private val identity = MediaIdentity(500, "movie", title = "Film")
    private fun server(id: String) = VideoServer(
        id = id, providerId = id, providerName = id,
        name = id, url = "https://example.org/$id.mp4",
        quality = StreamQuality.Q1080
    )

    @Test fun partialResultsAreImmediatelyReusableButNotMarkedComplete() {
        val cache = ResolvedStreamCache { 100L }
        cache.put(identity, "sources", ServerResolution(servers = listOf(server("first")), isComplete = false))
        val replay = cache.get(identity, "sources")!!
        assertEquals("first", replay.servers.first().id)
        assertFalse(replay.isComplete)
    }

    @Test fun partialResultCannotOverwriteCompletedDiscovery() {
        val cache = ResolvedStreamCache { 100L }
        cache.put(identity, "sources", ServerResolution(servers = listOf(server("a"), server("b")), isComplete = true))
        cache.put(identity, "sources", ServerResolution(servers = listOf(server("a")), isComplete = false))
        assertTrue(cache.get(identity, "sources")!!.isComplete)
        assertEquals(2, cache.get(identity, "sources")!!.servers.size)
    }

    @Test fun aDifferentAddonConfigurationDoesNotReuseStaleSources() {
        val cache = ResolvedStreamCache { 100L }
        cache.put(identity, "before", ServerResolution(servers = listOf(server("old"))))
        assertEquals(null, cache.get(identity, "after"))
    }
}
