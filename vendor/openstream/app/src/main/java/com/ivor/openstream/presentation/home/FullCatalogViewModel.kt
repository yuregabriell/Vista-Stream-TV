package com.ivor.openstream.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.TmdbResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pages load only when needed; we do not download 20,000 posters into memory. */
data class FullCatalogItem(
    val key: String,
    val title: String,
    val poster: String?,
    val year: String?,
    val mediaType: String,
    val tmdbId: Int? = null,
    val anilist: KeylessTitle? = null
)

data class FullCatalogState(
    val category: String = "all",
    val query: String = "",
    val year: Int? = null,
    val items: List<FullCatalogItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val moviesTotal: Int = 0,
    val seriesTotal: Int = 0,
    val animeTmdbTotal: Int = 0,
    val hasAniList: Boolean = false
)

@HiltViewModel
class FullCatalogViewModel @Inject constructor(
    private val tmdb: TmdbApi,
    private val anilistClient: KeylessCatalogClient
) : ViewModel() {
    private val _state = MutableStateFlow(FullCatalogState())
    val state: StateFlow<FullCatalogState> = _state.asStateFlow()
    private var job: Job? = null

    fun setFilters(category: String, query: String = "", year: Int? = null) {
        if (category !in listOf("all", "movie", "tv", "anime")) return
        val clean = query.trim().take(100)
        val safeYear = year?.takeIf { it in 1900..2100 }
        val current = _state.value
        if (current.category == category && current.query == clean &&
            current.year == safeYear && (current.items.isNotEmpty() || current.loading)) return
        job?.cancel()
        _state.value = FullCatalogState(category = category, query = clean, year = safeYear)
        loadMore()
    }

    fun loadMore() {
        val previous = _state.value
        if (previous.loading || !previous.hasMore || previous.page >= 500) return
        val nextPage = previous.page + 1
        _state.value = previous.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val incoming = fetchPage(previous.category, previous.query, previous.year, nextPage)
                val current = _state.value
                if (current.category != previous.category || current.query != previous.query ||
                    current.year != previous.year) return@launch
                _state.value = current.copy(
                    items = (current.items + incoming.items).distinctBy { it.key },
                    page = nextPage, hasMore = incoming.hasMore && nextPage < 500,
                    loading = false,
                    moviesTotal = incoming.moviesTotal,
                    seriesTotal = incoming.seriesTotal,
                    animeTmdbTotal = incoming.animeTmdbTotal,
                    hasAniList = current.hasAniList || incoming.hasAniList
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false, error = e.localizedMessage ?: "Catálogo indisponível."
                )
            }
        }
    }

    private data class Page(
        val items: List<FullCatalogItem>,
        val hasMore: Boolean,
        val moviesTotal: Int = 0,
        val seriesTotal: Int = 0,
        val animeTmdbTotal: Int = 0,
        val hasAniList: Boolean = false
    )

    private fun AnimeDto.asItem(type: String) = FullCatalogItem(
        key = type + ":" + id,
        title = name,
        poster = posterPath?.let { "https://image.tmdb.org/t/p/w342" + it },
        year = date.take(4).takeIf { it.length == 4 },
        mediaType = type,
        tmdbId = id
    )

    private fun KeylessTitle.asItem() = FullCatalogItem(
        key = "anilist:" + (anilistId ?: sourceId),
        title = title, poster = poster, year = year?.toString(),
        mediaType = "anime", anilist = this
    )

    private suspend fun requestTmdb(
        type: String, page: Int, query: String, year: Int?, anime: Boolean
    ): TmdbResponse<AnimeDto> {
        if (query.isNotBlank())
            return if (type == "movie") tmdb.searchMovie(query, page)
                else tmdb.searchTv(query, page)
        val params = mutableMapOf("page" to page.toString(),
            "sort_by" to "popularity.desc", "include_adult" to "false")
        if (year != null) params[
            if (type == "movie") "primary_release_year" else "first_air_date_year"
        ] = year.toString()
        if (anime) {
            params["with_original_language"] = "ja"
            params["with_genres"] = "16"
        }
        return if (type == "movie") tmdb.discoverMovieWith(params)
        else tmdb.discoverTvWith(params)
    }

    private suspend fun fetchPage(
        category: String, query: String, year: Int?, page: Int
    ): Page = coroutineScope {
        val anime = category == "anime"
        val m = if (category in listOf("all", "movie", "anime"))
            async { runCatching { requestTmdb("movie", page, query, year, anime) } } else null
        val t = if (category in listOf("all", "tv", "anime"))
            async { runCatching { requestTmdb("tv", page, query, year, anime) } } else null
        val a = if (anime) async {
            runCatching { anilistClient.titles("anime", query = query, page = page, year = year) }
        } else null
        val movies = m?.await()?.getOrNull()
        val tv = t?.await()?.getOrNull()
        val animeResults = a?.await()?.getOrNull()
        if (movies == null && tv == null && animeResults == null)
            throw IllegalStateException("As fontes de catálogo não responderam.")

        fun List<AnimeDto>.clean(type: String) = asSequence()
            .filter { it.posterPath != null && it.adult != true }
            .filter { !anime || (it.originalLanguage == "ja" && it.genreIds?.contains(16) == true) }
            .filter { year == null || query.isBlank() || it.date.startsWith(year.toString()) }
            .map { it.asItem(type) }.toList()

        val rows = (
            movies?.results.orEmpty().clean("movie") +
            tv?.results.orEmpty().clean("tv") +
            animeResults.orEmpty().filter { !it.poster.isNullOrBlank() }.map { it.asItem() }
        ).distinctBy { it.key }

        Page(
            items = rows,
            hasMore = (movies != null && movies.results.isNotEmpty() && page < movies.totalPages) ||
                (tv != null && tv.results.isNotEmpty() && page < tv.totalPages) ||
                (animeResults != null && animeResults.size >= 24),
            moviesTotal = movies?.totalResults ?: 0,
            seriesTotal = tv?.totalResults ?: 0,
            animeTmdbTotal = if (anime) (movies?.totalResults ?: 0) +
                (tv?.totalResults ?: 0) else 0,
            hasAniList = animeResults != null
        )
    }
}
