package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconciles AniList catalogue entries with the TMDB identity used by the
 * main VistaStream TV title, season, source, and playback routes.
 *
 * AniList IDs must never be passed to a TMDB detail route. Otherwise anime
 * opens a different detail/player implementation and existing sources vanish.
 */
@HiltViewModel
class TvAnimeTitleNavigationViewModel @Inject constructor(
    private val tmdb: TmdbApi
) : ViewModel() {
    private val matches = LinkedHashMap<String, Pair<String, Int>>()

    suspend fun resolve(title: KeylessTitle): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val mediaType = if (title.format.equals("MOVIE", ignoreCase = true)) "movie" else "tv"
        title.tmdbId?.takeIf { it > 0 }?.let { return@withContext mediaType to it }
        matches[title.sourceId]?.let { return@withContext it }

        val names = listOfNotNull(title.title, title.alternateTitle)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy(::normalized)
        val expected = names.map(::normalized).toSet()
        try {
            for (name in names) {
                val candidates = if (mediaType == "movie") {
                    tmdb.searchMovieForArtwork(name).results
                } else {
                    tmdb.searchTvForArtwork(name).results
                }
                val match = candidates.firstOrNull { candidate ->
                    candidate.id > 0 &&
                        normalized(candidate.name) in expected &&
                        compatibleYear(title.year, candidate)
                }
                if (match != null) {
                    val route = mediaType to match.id
                    matches[title.sourceId] = route
                    if (matches.size > 120) matches.remove(matches.keys.first())
                    return@withContext route
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A temporary catalogue/gateway error should remain retryable.
        }
        null
    }

    private fun compatibleYear(expected: Int?, candidate: AnimeDto): Boolean {
        val actual = candidate.date.take(4).toIntOrNull()
        return expected == null || actual == null || kotlin.math.abs(actual - expected) <= 2
    }

    private fun normalized(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}
