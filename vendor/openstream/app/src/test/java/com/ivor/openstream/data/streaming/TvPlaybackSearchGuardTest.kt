package com.ivor.openstream.data.streaming

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvPlaybackSearchGuardTest {
    @Test fun playbackCancelsDetachedProviderJobsButAllowsLaterSearches() {
        TvPlaybackSearchGuard.endPlayback()
        val oldProvider = Job()
        TvPlaybackSearchGuard.track(oldProvider)
        assertTrue(oldProvider.isActive)
        TvPlaybackSearchGuard.beginPlayback()
        assertTrue(TvPlaybackSearchGuard.isPlaying())
        assertTrue(oldProvider.isCancelled)
        TvPlaybackSearchGuard.untrack(oldProvider)

        val queuedDuringPlayback = Job()
        TvPlaybackSearchGuard.track(queuedDuringPlayback)
        assertTrue(queuedDuringPlayback.isCancelled)

        TvPlaybackSearchGuard.endPlayback()
        assertFalse(TvPlaybackSearchGuard.isPlaying())
        val nextTitle = Job()
        TvPlaybackSearchGuard.track(nextTitle)
        assertTrue(nextTitle.isActive)
        TvPlaybackSearchGuard.untrack(nextTitle)
        nextTitle.cancel()
    }
}
