package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.TvStreamingProvidersAddon
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.TmdbResponse
import com.ivor.openstream.data.remote.model.WatchProviderDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.Year

data class TvCatalogGenre(val key: String, val label: String, val movieId: Int?, val tvId: Int?)

val TV_CATALOG_GENRES = listOf(
    TvCatalogGenre("all", "Todos os géneros", null, null),
    TvCatalogGenre("action", "Ação", 28, 10759),
    TvCatalogGenre("adventure", "Aventura", 12, 10759),
    TvCatalogGenre("animation", "Animação", 16, 16),
    TvCatalogGenre("comedy", "Comédia", 35, 35),
    TvCatalogGenre("crime", "Crime", 80, 80),
    TvCatalogGenre("documentary", "Documentário", 99, 99),
    TvCatalogGenre("drama", "Drama", 18, 18),
    TvCatalogGenre("family", "Família", 10751, 10751),
    TvCatalogGenre("fantasy", "Fantasia", 14, 10765),
    TvCatalogGenre("mystery", "Mistério", 9648, 9648),
    TvCatalogGenre("romance", "Romance", 10749, null),
    TvCatalogGenre("scifi", "Ficção científica", 878, 10765),
    TvCatalogGenre("thriller", "Suspense", 53, null),
    TvCatalogGenre("horror", "Terror", 27, null)
)

data class TvCatalogState(
    val items: List<AnimeDto> = emptyList(),
    val mediaType: String = "all",
    val genre: String = "all",
    val year: Int? = null,
    val providerId: Int? = null,
    val providersEnabled: Boolean = false,
    val providers: List<WatchProviderDto> = emptyList(),
    val loading: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null
)

/**
 * Full TV browse backed by TMDB discover with real server-side paging.
 * Provider, genre, year and content filters are combined in the API query,
 * not applied to just the 20 titles currently in the Home carousel.
 */
@HiltViewModel
class TvCatalogViewModel @Inject constructor(
    private val api: TmdbApi,
    private val addon: TvStreamingProvidersAddon
) : ViewModel() {
    private val _state = MutableStateFlow(TvCatalogState(providersEnabled = addon.enabled.value))
    val state: StateFlow<TvCatalogState> = _state
    private var request: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            addon.enabled.collect { enabled ->
                val was = _state.value.providersEnabled
                _state.value = _state.value.copy(
                    providersEnabled = enabled,
                    providerId = if (enabled) _state.value.providerId else null,
                    providers = if (enabled) _state.value.providers else emptyList()
                )
                if (was != enabled) refresh()
                if (enabled) loadProviders()
            }
        }
    }

    fun selectType(type: String) {
        if (type !in listOf("all", "movie", "tv") || _state.value.mediaType == type) return
        _state.value = _state.value.copy(mediaType = type)
        refresh()
    }
    fun selectGenre(key: String) {
        if (_state.value.genre == key || TV_CATALOG_GENRES.none { it.key == key }) return
        _state.value = _state.value.copy(genre = key)
        refresh()
    }
    fun selectYear(year: Int?) {
        if (_state.value.year == year) return
        _state.value = _state.value.copy(year = year)
        refresh()
    }
    fun selectProvider(id: Int?) {
        if (!addon.enabled.value && id != null || _state.value.providerId == id) return
        _state.value = _state.value.copy(providerId = id)
        refresh()
    }

    fun refresh() {
        request?.cancel()
        _state.value = _state.value.copy(
            items = emptyList(), page = 0, hasMore = true, loading = false, error = null
        )
        fetchNext()
    }

    fun loadMore() {
        if (_state.value.loading || !_state.value.hasMore) return
        fetchNext()
    }

    private fun fetchNext() {
        val snapshot = _state.value
        if (snapshot.loading || !snapshot.hasMore) return
        val page = snapshot.page + 1
        _state.value = snapshot.copy(loading = true, error = null)
        request = viewModelScope.launch {
            try {
                val genre = TV_CATALOG_GENRES.firstOrNull { it.key == snapshot.genre }
                val kinds = if (snapshot.mediaType == "all") listOf("movie", "tv") else listOf(snapshot.mediaType)
                val responses: List<Pair<String, TmdbResponse<AnimeDto>>> = coroutineScope {
                    kinds.map { type ->
                        async {
                            val filters = mutableMapOf(
                                "page" to page.toString(),
                                "sort_by" to "popularity.desc",
                                "include_adult" to "false",
                                "language" to "pt-PT"
                            )
                            val genreId = if (type == "movie") genre?.movieId else genre?.tvId
                            if (snapshot.genre != "all" && genreId != null) filters["with_genres"] = genreId.toString()
                            // A genre with no equivalent for the selected medium must not return everything.
                            if (snapshot.genre != "all" && genreId == null) filters["with_genres"] = "-1"
                            snapshot.year?.let { year ->
                                if (type == "movie") filters["primary_release_year"] = year.toString()
                                else filters["first_air_date_year"] = year.toString()
                            }
                            snapshot.providerId?.takeIf { snapshot.providersEnabled }?.let { id ->
                                filters["watch_region"] = "PT"
                                filters["with_watch_providers"] = id.toString()
                                filters["with_watch_monetization_types"] = "flatrate|free|ads"
                            }
                            type to if (type == "movie") api.discoverMovieWith(filters) else api.discoverTvWith(filters)
                        }
                    }.map { it.await() }
                }
                val new = responses.flatMap { (type, results) ->
                    results.results.map { it.copy(mediaType = type) }
                }.filter { it.posterPath != null }
                    .distinctBy { (it.mediaType ?: "tv") to it.id }
                    .sortedByDescending { it.popularity ?: 0.0 }
                val previous = if (page == 1) emptyList() else snapshot.items
                _state.value = _state.value.copy(
                    items = (previous + new).distinctBy { (it.mediaType ?: "tv") to it.id },
                    page = page,
                    hasMore = responses.any { it.second.totalPages > page },
                    loading = false,
                    error = null
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Não foi possível carregar o catálogo. Verifica a ligação e a chave do TMDB."
                )
            }
        }
    }

    private fun loadProviders() {
        viewModelScope.launch {
            val list = runCatching { addon.directory() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(providers = list)
        }
    }
}
