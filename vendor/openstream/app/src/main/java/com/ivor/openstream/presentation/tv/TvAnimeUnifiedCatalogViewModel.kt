package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The TV Anime section and the general TV search share TMDB identities.
 * AniList media IDs do not resolve to TV episodes or the main stream sources.
 * This dedicated TV feed uses TMDB discover/search, but retains anime rails.
 */
data class TvAnimeUnifiedState(
    val sections: Map<String, List<KeylessTitle>> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val browseItems: List<KeylessTitle> = emptyList(),
    val browseType: String = "anime",
    val browseGenre: String = "",
    val browseQuery: String = "",
    val browseLoading: Boolean = false,
    val browseError: String? = null,
    val browseHasMore: Boolean = true
)

@HiltViewModel
class TvAnimeUnifiedCatalogViewModel @Inject constructor(
    private val tmdb: TmdbApi
) : ViewModel() {
    private val _state = MutableStateFlow(TvAnimeUnifiedState())
    val state: StateFlow<TvAnimeUnifiedState> = _state
    private var refreshedAt = 0L
    private var refreshJob: Job? = null
    private var browseJob: Job? = null
    private var browsePage = 0
    private var browseHasMore = true

    init { refresh() }

    fun refreshIfStale() {
        if (System.currentTimeMillis() - refreshedAt > 3L * 60 * 60 * 1_000L &&
            refreshJob?.isActive != true) refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                // TV discovery is deliberately limited to three pages initially;
                // the regular catalog has independent pagination.
                val pages = coroutineScope {
                    listOf(
                        async { discover("tv", 1) },
                        async { discover("tv", 2) },
                        async { discover("movie", 1) }
                    ).map { task -> runCatching { task.await() }.getOrDefault(emptyList()) }
                }
                val items = pages.flatten().distinctBy { it.sourceId }
                if (items.isNotEmpty()) {
                    refreshedAt = System.currentTimeMillis()
                    _state.value = _state.value.copy(
                        sections = mapOf("anime" to items),
                        loading = false, error = null
                    )
                } else {
                    _state.value = _state.value.copy(loading = false,
                        error = "Não foi possível carregar o catálogo de anime do TMDB.")
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false,
                    error = "Falha ao carregar o catálogo de anime.")
            }
        }
    }

    fun browse(type: String, query: String = "", genre: String = "") {
        if (type != "anime") return
        browseJob?.cancel()
        browsePage = 0
        browseHasMore = true
        _state.value = _state.value.copy(
            browseType = "anime", browseGenre = genre, browseQuery = query,
            browseItems = emptyList(), browseHasMore = true,
            browseError = null, browseLoading = false
        )
        loadMore()
    }

    fun loadMore() {
        if (browseJob?.isActive == true || !browseHasMore) return
        val requestedGenre = _state.value.browseGenre
        val requestedQuery = _state.value.browseQuery
        val next = browsePage + 1
        browseJob = viewModelScope.launch {
            _state.value = _state.value.copy(browseLoading = true, browseError = null)
            try {
                val fetched = coroutineScope {
                    listOf("tv", "movie").map { type ->
                        async {
                            runCatching {
                                if (requestedQuery.isBlank()) discover(type, next, requestedGenre)
                                else search(type, requestedQuery, next, requestedGenre)
                            }.getOrDefault(emptyList())
                        }
                    }.flatMap { it.await() }
                }
                val old = if (next == 1) emptyList() else _state.value.browseItems
                browsePage = next
                // Search pages can be sparse after filtering; keep a finite horizon.
                browseHasMore = fetched.isNotEmpty() && next < 20
                _state.value = _state.value.copy(
                    browseItems = (old + fetched).distinctBy { it.sourceId },
                    browseHasMore = browseHasMore, browseLoading = false
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    browseLoading = false,
                    browseError = "Falha ao carregar resultados de anime.")
            }
        }
    }

    private suspend fun discover(type: String, page: Int, genre: String = ""): List<KeylessTitle> {
        val filter = mutableMapOf(
            "page" to page.toString(),
            "sort_by" to "popularity.desc",
            "with_original_language" to "ja",
            "with_genres" to if (genreCode(type, genre) != null)
                "16,${genreCode(type, genre)}" else "16",
            "language" to "pt-BR"
        )
        val response = if (type == "movie") tmdb.discoverMovieWith(filter)
            else tmdb.discoverTvWith(filter)
        return response.results.mapNotNull { toTitle(it, type) }
    }

    private suspend fun search(type: String, query: String, page: Int, genre: String): List<KeylessTitle> {
        // Use the exact same TMDB search endpoints as the global TV search.
        val response = if (type == "movie") tmdb.searchMovie(query, page)
            else tmdb.searchTv(query, page)
        val genreId = genreCode(type, genre)
        return response.results.filter {
            it.originalLanguage.equals("ja", ignoreCase = true) &&
                16 in it.genreIds.orEmpty() &&
                (genreId == null || genreId in it.genreIds.orEmpty())
        }.mapNotNull { toTitle(it, type) }
    }

    private fun toTitle(item: AnimeDto, type: String): KeylessTitle? {
        if (item.id <= 0 || item.name.isBlank() || item.posterPath.isNullOrBlank()) return null
        val ids = item.genreIds.orEmpty()
        val genreNames = buildList {
            if (28 in ids || 10759 in ids) add("Action")
            if (12 in ids) add("Adventure")
            if (35 in ids) add("Comedy")
            if (18 in ids) add("Drama")
            if (14 in ids || 10765 in ids) add("Fantasy")
            if (10749 in ids) add("Romance")
            if (878 in ids) add("Sci-Fi")
            if (9648 in ids) add("Mystery")
            if (27 in ids) add("Horror")
        }
        return KeylessTitle(
            sourceId = "tmdb:$type:${item.id}",
            title = item.name,
            type = "anime",
            tmdbId = item.id,
            year = item.date.take(4).toIntOrNull(),
            poster = "https://image.tmdb.org/t/p/w500${item.posterPath}",
            background = item.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
            description = item.overview.orEmpty(),
            genres = genreNames,
            format = if (type == "movie") "MOVIE" else "TV"
        )
    }

    private fun genreCode(type: String, genre: String): Int? = when (genre) {
        "Action", "Adventure" -> if (type == "movie") 28 else 10759
        "Comedy" -> 35
        "Drama", "Romance", "Slice of Life", "Sports", "Music" -> 18
        "Fantasy", "Supernatural", "Sci-Fi" -> if (type == "movie") 14 else 10765
        "Mystery" -> 9648
        "Horror" -> if (type == "movie") 27 else 9648
        else -> null
    }
}
