package com.ivor.openstream.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.catalog.KeylessEpisode
import com.ivor.openstream.data.catalog.VistaWatchHistory
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.harvester.NativeHarvesterStore
import com.ivor.openstream.harvester.NativeHarvesterProvider
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeylessCatalogState(
    val sections: Map<String, List<KeylessTitle>> = emptyMap(),
    val searchResults: List<KeylessTitle> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val searching: Boolean = false,
    val error: String? = null,
    val selected: KeylessTitle? = null,
    val episodes: List<KeylessEpisode> = emptyList(),
    val season: Int = 1,
    val episode: Int = 1,
    val resolving: Boolean = false,
    val servers: List<VideoServer> = emptyList(),
    val playing: VideoServer? = null,
    val resumePositionMs: Long = 0L,
    val savedProgressMs: Long = 0L,
    val resolveFinished: Boolean = false,
    val resolvedProviders: Int = 0,
    val totalProviders: Int = 0,
    val failedProviders: List<String> = emptyList(),
    val browseItems: List<KeylessTitle> = emptyList(),
    val browseType: String = "",
    val browseQuery: String = "",
    val browseGenre: String = "",
    val browseYear: Int? = null,
    val browsePage: Int = 0,
    val browseLoading: Boolean = false,
    val browseHasMore: Boolean = true,
    val browseError: String? = null
)

@HiltViewModel
class KeylessCatalogViewModel @Inject constructor(
    private val client: KeylessCatalogClient,
    private val streams: StreamingRepository,
    private val history: VistaWatchHistory,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val hasTmdbKey: Boolean get() = client.tmdbConfigured

    fun continueWatching(titles: List<KeylessTitle>): List<KeylessTitle> =
        (history.savedTitles() + titles).distinctBy { it.sourceId }
            .filter { history.isInProgress(it.sourceId) }
            .sortedByDescending { history.lastPlayedAt(it.sourceId) }
            .take(20)
    private val _state = MutableStateFlow(KeylessCatalogState())
    val state: StateFlow<KeylessCatalogState> = _state.asStateFlow()
    private var job: Job? = null
    private var episodeJob: Job? = null
    private var resolutionJob: Job? = null
    private var browseJob: Job? = null
    private var catalogJob: Job? = null
    private var catalogUpdatedAt = 0L

    /** Full-category paging independent of the Home preview rails. */
    fun browse(type: String, query: String = "", genre: String = "", year: Int? = null) {
        val current = _state.value
        if (current.browseType == type && current.browseQuery == query &&
            current.browseGenre == genre && current.browseYear == year) return
        browseJob?.cancel()
        _state.value = current.copy(
            browseType = type, browseQuery = query, browseGenre = genre,
            browseYear = year, browseItems = emptyList(), browsePage = 0,
            browseHasMore = true, browseLoading = false, browseError = null
        )
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.browseType.isBlank() || current.browseLoading || !current.browseHasMore) return
        browseJob?.cancel()
        val type = current.browseType
        val query = current.browseQuery
        val genre = current.browseGenre
        val year = current.browseYear
        val page = current.browsePage + 1
        _state.value = current.copy(browseLoading = true, browseError = null)
        browseJob = viewModelScope.launch {
            try {
                val types = if (type == "all") listOf("movie", "series", "anime") else listOf(type)
                val fetched = types.map { kind ->
                    async {
                        runCatching {
                            client.titles(kind, query = query, page = page, genre = genre, year = year)
                        }
                    }
                }.map { it.await() }
                if (fetched.all { it.isFailure }) {
                    throw fetched.first().exceptionOrNull()
                        ?: IllegalStateException("Falha ao carregar o catálogo")
                }
                val returned = fetched.map { it.getOrDefault(emptyList()) }
                val results = returned.flatten().distinctBy { it.sourceId }
                val sameQuery = _state.value.browseType == type &&
                    _state.value.browseQuery == query &&
                    _state.value.browseGenre == genre && _state.value.browseYear == year
                if (sameQuery) {
                    val more = returned.any { resultsByType ->
                        resultsByType.size >= 24
                    }
                    _state.value = _state.value.copy(
                        browseItems = (_state.value.browseItems + results).distinctBy { it.sourceId },
                        browsePage = page,
                        browseHasMore = more && page < 50,
                        browseLoading = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    browseLoading = false,
                    browseError = error.message ?: "Falha ao carregar mais títulos."
                )
            }
        }
    }


    init { loadCatalog(force = false) }

    fun refresh() = loadCatalog(force = true)

    /** Public catalog caches are refreshed every three hours, not only on app restart. */
    fun refreshIfStale() {
        if (catalogJob?.isActive == true) return
        val age = System.currentTimeMillis() - catalogUpdatedAt
        if (catalogUpdatedAt == 0L || age < 0 || age >= 3L * 60 * 60 * 1000) {
            loadCatalog(force = false)
        }
    }

    private fun loadCatalog(force: Boolean) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val sections = client.refresh(force = force)
                if (sections.values.any { it.isNotEmpty() }) {
                    catalogUpdatedAt = System.currentTimeMillis()
                }
                _state.value = _state.value.copy(
                    sections = sections, loading = false,
                    error = if (sections.values.all { it.isEmpty() })
                        "Não foi possível consultar Cinemeta e AniList. Verifica a Internet."
                        else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Não foi possível atualizar o catálogo."
                )
            }
        }
    }

    fun search(text: String) {
        job?.cancel()
        _state.value = _state.value.copy(query = text)
        if (text.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false)
            return
        }
        job = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true)
            val results = listOf("movie", "series", "anime").map { type ->
                async { runCatching { client.titles(type, text) }.getOrDefault(emptyList()) }
            }.flatMap { it.await() }.distinctBy { it.sourceId }
            _state.value = _state.value.copy(searchResults = results, searching = false)
        }
    }

    fun select(item: KeylessTitle) {
        resolutionJob?.cancel()
        episodeJob?.cancel()
        val last = history.lastEpisode(item.sourceId)
        val startSeason = last?.first ?: 1
        val startEpisode = last?.second ?: 1
        _state.value = _state.value.copy(
            selected = item, episodes = emptyList(), season = startSeason, episode = startEpisode,
            resolving = true, servers = emptyList(), playing = null, resolveFinished = false,
            savedProgressMs = history.read(item.sourceId, startSeason, startEpisode)
                .takeIf { it.canResume }?.positionMs ?: 0L,
            resumePositionMs = 0L,
            resolvedProviders = 0, totalProviders = 0, failedProviders = emptyList()
        )
        episodeJob = viewModelScope.launch {
            val enriched = runCatching { client.enrich(item) }.getOrDefault(item)
            val remoteEpisodes = runCatching { client.episodes(enriched) }.getOrDefault(emptyList())
            // AniList exposes episode counts but often has no per-episode metadata.
            val list = if (remoteEpisodes.isNotEmpty()) remoteEpisodes else {
                (1..(enriched.episodes ?: 1).coerceIn(1, 250)).map {
                    KeylessEpisode(1, it, "Episódio $it")
                }
            }
            if (_state.value.selected?.sourceId == item.sourceId) {
                _state.value = _state.value.copy(selected = enriched, episodes = list)
                // The user should not have to press a second button to search.
                findStreams()
            }
        }
    }

    fun chooseEpisode(season: Int, episode: Int) {
        resolutionJob?.cancel()
        val title = _state.value.selected ?: return
        _state.value = _state.value.copy(
            season = season, episode = episode, servers = emptyList(), resolving = true,
            savedProgressMs = history.read(title.sourceId, season, episode).takeIf { it.canResume }?.positionMs ?: 0L,
            resolveFinished = false, resolvedProviders = 0, totalProviders = 0, failedProviders = emptyList()
        )
        findStreams()
    }

    fun closeDetails() {
        episodeJob?.cancel()
        resolutionJob?.cancel()
        _state.value = _state.value.copy(selected = null, servers = emptyList(), playing = null, resolving = false)
    }

    fun findStreams() {
        val item = _state.value.selected ?: return
        resolutionJob?.cancel()
        _state.value = _state.value.copy(resolving = true, resolveFinished = false, servers = emptyList(),
            resolvedProviders = 0, totalProviders = 0, failedProviders = emptyList())
        resolutionJob = viewModelScope.launch {
            try {
                val enriched = runCatching { client.enrich(item) }.getOrDefault(item)
                val imdb = enriched.imdbId ?: runCatching { client.findAnimeImdb(enriched) }.getOrNull()
                val identity = MediaIdentity(
                    tmdbId = enriched.tmdbId ?: 0,
                    tmdbType = if (item.type == "movie") "movie" else "tv",
                    imdbId = imdb,
                    anilistId = item.anilistId,
                    title = item.title,
                    originalTitle = item.alternateTitle,
                    year = item.year,
                    season = _state.value.season,
                    episode = _state.value.episode
                )
                // Library sources must appear even when their extension is disabled,
                // remote providers time out, or the source cache is stale.
                val localSources = NativeHarvesterProvider(NativeHarvesterStore(context))
                    .resolve(identity).getOrDefault(emptyList())
                if (_state.value.selected?.sourceId == item.sourceId) {
                    _state.value = _state.value.copy(servers = localSources)
                }
                // On explicit "Encontrar fontes online", search *all* enabled compatible
                // providers instead of stopping early when the first link appears.
                streams.resolveServers(identity, includeFallbacks = true).collect { resolution ->
                    if (_state.value.selected?.sourceId == item.sourceId) {
                        _state.value = _state.value.copy(
                            servers = (localSources + resolution.servers).distinctBy { it.url }.filter {
                                it.url.startsWith("https://") || it.url.startsWith("http://") ||
                                    it.url.startsWith("rtsp://")
                            },
                            resolving = !resolution.isComplete, resolveFinished = resolution.isComplete,
                            resolvedProviders = resolution.completedProviders,
                            totalProviders = resolution.totalProviders,
                            failedProviders = resolution.failedProviders
                        )
                    }
                }
                _state.value = _state.value.copy(resolving = false, resolveFinished = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = _state.value.copy(resolving = false, resolveFinished = true)
            }
        }
    }

    fun play(server: VideoServer) {
        val title = _state.value.selected ?: return
        history.rememberTitle(title)
        val saved = history.read(title.sourceId, _state.value.season, _state.value.episode)
        _state.value = _state.value.copy(
            playing = server, resolving = false,
            resumePositionMs = saved.takeIf { it.canResume }?.positionMs ?: 0L
        )
    }

    fun playFirst() {
        _state.value.servers.firstOrNull()?.let(::play)
    }

    fun nextSource(current: VideoServer) {
        val candidates = _state.value.servers
        val next = candidates.firstOrNull { it.id != current.id && it.url != current.url }
        if (next == null) closePlayer() else play(next)
    }

    fun savePlayback(positionMs: Long, durationMs: Long) {
        val title = _state.value.selected ?: return
        history.write(title.sourceId, _state.value.season, _state.value.episode, positionMs, durationMs)
    }

    fun closePlayer() {
        val title = _state.value.selected
        _state.value = _state.value.copy(
            playing = null,
            savedProgressMs = if (title != null) {
                history.read(title.sourceId, _state.value.season, _state.value.episode)
                    .takeIf { it.canResume }?.positionMs ?: 0L
            } else 0L
        )
    }
}
