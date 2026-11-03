package com.ivor.openstream.presentation.player

import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourcePolicyTest {
    private fun server(id: String) = VideoServer(
        id = id, providerId = id, providerName = id, name = id,
        url = "https://example.org/$id.mp4", quality = StreamQuality.Q1080
    )

    @Test fun firstCandidateStartsWithoutWaitingForEveryProvider() {
        assertEquals("first", PlaybackSourcePolicy.select(
            listOf(server("first")), null, null, null, false
        )?.id)
    }

    @Test fun laterHigherRankedSourceDoesNotRestartFirstVideo() {
        assertEquals("first", PlaybackSourcePolicy.select(
            listOf(server("new"), server("first")), null, "first", null, true
        )?.id)
    }

    @Test fun explicitUserSelectionTakesPriority() {
        assertEquals("dub", PlaybackSourcePolicy.select(
            listOf(server("sub"), server("dub")), "dub", "sub", null, false
        )?.id)
    }

    @Test fun noEligibleResultDoesNotInventPlaybackUrl() {
        assertNull(PlaybackSourcePolicy.select(emptyList(), null, null, null, false))
    }
}
