package com.ivor.openstream.presentation.tv

import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.model.AnimeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TvAnimeCanonicalMatchTest {
    private val onePiece = KeylessTitle(
        sourceId = "anilist:21", title = "One Piece", type = "anime", year = 1999,
        alternateTitle = "One Piece", format = "TV"
    )

    @Test
    fun onePiece1999IsNotRoutedToThe2023LiveActionAdaptation() {
        val liveAction = AnimeDto(
            id = 101, tvName = "One Piece", firstAirDate = "2023-08-31",
            originalLanguage = "en", genreIds = listOf(18, 10759)
        )
        val animated = AnimeDto(
            id = 102, tvName = "One Piece", firstAirDate = "1999-10-20",
            originalLanguage = "ja", genreIds = listOf(16, 10759)
        )
        val matched = TvAnimeNames.chooseCandidate(
            onePiece, TvAnimeNames.searchNames(onePiece.title),
            listOf(liveAction, animated), isSeason = false
        )
        assertEquals(102, matched?.id)
    }

    @Test
    fun sameNameWrongYearDoesNotSilentlyOpenDifferentSeries() {
        val wrongYear = AnimeDto(
            id = 103, tvName = "One Piece", firstAirDate = "2023-08-31",
            originalLanguage = "ja", genreIds = listOf(16)
        )
        assertNull(TvAnimeNames.chooseCandidate(
            onePiece, listOf("One Piece"), listOf(wrongYear), isSeason = false
        ))
    }

    @Test
    fun explicitSeasonCanResolveToParentSeriesDespiteDifferentReleaseYear() {
        val sequel = KeylessTitle(
            sourceId = "anilist:2026", title = "Example Anime Season 2",
            type = "anime", year = 2026
        )
        val parent = AnimeDto(
            id = 104, tvName = "Example Anime", firstAirDate = "2019-03-15",
            originalLanguage = "ja", genreIds = listOf(16)
        )
        val names = TvAnimeNames.searchNames(sequel.title)
        assertTrue(names.contains("Example Anime"))
        assertEquals(104, TvAnimeNames.chooseCandidate(
            sequel, names, listOf(parent), isSeason = true
        )?.id)
    }
}
