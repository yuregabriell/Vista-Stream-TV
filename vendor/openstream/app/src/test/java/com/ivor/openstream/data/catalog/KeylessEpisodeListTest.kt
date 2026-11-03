package com.ivor.openstream.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeylessEpisodeListTest {
    private fun title(episodes: Int? = null, format: String? = "TV"): KeylessTitle =
        KeylessTitle(sourceId = "anilist:21", title = "One Piece", type = "anime",
            episodes = episodes, format = format)

    @Test
    fun unknownEpisodeCountNeverInventsAnEpisode() {
        assertTrue(KeylessEpisodeList.forTitle(title(episodes = null), emptyList()).isEmpty())
    }

    @Test
    fun largeReportedCountIsNotTruncatedTo250() {
        val list = KeylessEpisodeList.forTitle(title(episodes = 1_120), emptyList())
        assertEquals(1_120, list.size)
        assertEquals(1_120, list.last().number)
    }

    @Test
    fun authoritativeSeasonEpisodesTakePriorityOverSyntheticCount() {
        val remote = listOf(KeylessEpisode(2, 2, "Second"), KeylessEpisode(2, 1, "First"),
            KeylessEpisode(2, 1, "First"))
        val list = KeylessEpisodeList.forTitle(title(episodes = 200), remote)
        assertEquals(2, list.size)
        assertEquals(1, list.first().number)
        assertEquals(2, list.last().season)
    }

    @Test
    fun animeMoviesDoNotGetFictitiousTvEpisodes() {
        assertTrue(KeylessEpisodeList.forTitle(title(episodes = 1, format = "MOVIE"), emptyList()).isEmpty())
    }
}
