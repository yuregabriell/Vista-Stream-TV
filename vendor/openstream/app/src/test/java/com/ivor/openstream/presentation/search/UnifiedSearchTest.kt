package com.ivor.openstream.presentation.search

import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.model.AnimeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchTest {
    private val anime = AnimeDto(
        id = 1, tvName = "One Piece", mediaType = "tv",
        originalLanguage = "ja", genreIds = listOf(16, 10759)
    )
    private val film = AnimeDto(
        id = 2, movieTitle = "Afinação", mediaType = "movie",
        originalLanguage = "en", genreIds = listOf(35)
    )
    private val liveAction = AnimeDto(
        id = 3, tvName = "One Piece", mediaType = "tv",
        originalLanguage = "en", genreIds = listOf(10759)
    )

    @Test fun allIsDefaultAndIncludesMoviesSeriesAndAnimes() {
        val state = SearchUiState(results = listOf(anime, film, liveAction))
        assertEquals(SearchFilter.ALL, state.filter)
        assertEquals(3, state.visibleResults.size)
    }

    @Test fun animeFilterDoesNotReturnLiveActionOrNormalFilms() {
        val state = SearchUiState(
            filter = SearchFilter.ANIME, results = listOf(anime, film, liveAction)
        )
        assertEquals(listOf(anime), state.visibleResults)
    }

    @Test fun animeResultsFromAniListSupplementInsteadOfDuplicateTmdb() {
        val candidates = listOf(
            KeylessTitle("anilist:21", "One Piece", "anime"),
            KeylessTitle("anilist:502", "Missing TMDB Anime", "anime")
        )
        val state = SearchUiState(
            query = "anime", filter = SearchFilter.ALL,
            results = listOf(anime), animeMatches = candidates
        )
        assertEquals(1, state.visibleAnimeMatches.size)
        assertEquals("Missing TMDB Anime", state.visibleAnimeMatches.first().title)
        assertTrue(state.visibleResults.any { it.id == 1 })
    }

    @Test fun normalMovieFilterExcludesAnime() {
        val state = SearchUiState(
            filter = SearchFilter.MOVIES, results = listOf(anime, film, liveAction)
        )
        assertEquals(listOf(film), state.visibleResults)
        assertFalse(state.visibleAnimeMatches.isNotEmpty())
    }
}
