package com.ivor.openstream.presentation.tv

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvEpisodeAvailabilityTest {
    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun unreleasedEpisodeIsNotTreatedAsPlayable() {
        assertTrue(isUpcomingEpisode("2026-11-03", today))
        assertTrue(isUpcomingEpisode("2026-11-10", today))
    }

    @Test
    fun releasedEpisodeCanBeSelectedForPlayback() {
        assertFalse(isUpcomingEpisode("2026-10-27", today))
        assertFalse(isUpcomingEpisode("2026-11-01", today))
    }

    @Test
    fun missingOrInvalidAirDatesDoNotBlockPlayback() {
        assertFalse(isUpcomingEpisode(null, today))
        assertFalse(isUpcomingEpisode("", today))
        assertFalse(isUpcomingEpisode("unknown", today))
    }
}
