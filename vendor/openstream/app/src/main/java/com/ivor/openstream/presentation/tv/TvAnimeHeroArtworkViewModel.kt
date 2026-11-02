package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.TmdbApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * AniList bannerImage is a very short/low-res banner, not a 16:9 TV backdrop.
 * Look up a TMDB full-sized backdrop for the title only when the hero is
 * highlighted, cache the result, and refuse fuzzy/wrong-title matches.
 *
 * A high-resolution poster is displayed on the right if no matching backdrop
 * exists. No poster is ever stretched over the entire TV.
 */
@HiltViewModel
class TvAnimeHeroArtworkViewModel @Inject constructor(
    private val tmdb: TmdbApi
) : ViewModel() {
    private val _artwork = MutableStateFlow<String?>(null)
    val artwork: StateFlow<String?> = _artwork

    private val known = LinkedHashMap<String, String?>()
    private var currentKey: String? = null
    private var job: Job? = null

    fun show(title: KeylessTitle) {
        val key = title.sourceId
        if (key == currentKey) return
        currentKey = key
        job?.cancel()
        _artwork.value = known[key]
        if (known.containsKey(key)) return

        job = viewModelScope.launch {
            try {
                val isMovie = title.format.equals("MOVIE", ignoreCase = true)
                val candidates = if (isMovie) {
                    tmdb.searchMovieForArtwork(title.title).results
                } else {
                    tmdb.searchTvForArtwork(title.title).results
                }
                val expected = listOfNotNull(title.title, title.alternateTitle).map(::normalize)
                val match = candidates.firstOrNull { candidate ->
                    val name = normalize(candidate.name)
                    expected.any { name == it } &&
                        candidate.backdropPath != null &&
                        (title.year == null || candidate.date.take(4).toIntOrNull()?.let {
                            year -> kotlin.math.abs(year - title.year) <= 2
                        } != false)
                }
                val url = match?.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                known[key] = url
                // Limit in-memory metadata; Coil owns the bitmap/disk caches.
                if (known.size > 75) known.remove(known.keys.first())
                if (currentKey == key) _artwork.value = url
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                if (currentKey == key) _artwork.value = null
                // Retry if the metadata gateway was temporarily unavailable.
            }
        }
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}
