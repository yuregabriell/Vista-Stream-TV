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
    private val tmdb: TmdbApi,
    private val matcher: TvAnimeCatalogMatcher
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
                // Artwork and title navigation must use the SAME canonical match.
                // Season titles (e.g. Aoashi Season 2) resolve to their base TV show.
                val match = matcher.resolve(title)
                val path = match?.backdropPath ?: match?.let {
                    runCatching {
                        if (it.mediaType == "movie") tmdb.getMovieDetails(it.tmdbId).backdropPath
                        else tmdb.getAnimeDetails(it.tmdbId).backdropPath
                    }.getOrNull()
                }
                val url = path?.let { "https://image.tmdb.org/t/p/original$it" }
                if (url != null) known[key] = url
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
