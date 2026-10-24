package com.ivor.openstream.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.catalog.KeylessEpisode
import com.ivor.openstream.data.catalog.VistaWatchHistory
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.VideoServer
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
    val failedProviders: List<String> = emptyList()
)

@HiltViewModel
class KeylessCatalogViewModel @Inject constructor(
    private val client: KeylessCatalogClient,
    private val streams: StreamingRepository,
    private val history: VistaWatchHistory
) : ViewModel() {
    val hasTmdbKey: Boolean get() = client.tmdbConfigured
    private val _state = MutableStateFlow(KeylessCatalogState())
    val state: StateFlow<KeylessCatalogState> = _state.asStateFlow()
    private var job: Job? = null
    private var episodeJob: Job? = null
    private var resolutionJob: Job? = null

    init { loadCatalog(force = false) }

    fun refresh() = loadCatalog(force = true)

    private fun loadCatalog(force: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val sections = client.refresh(force = force)
            _state.value = _state.value.copy(
                sections = sections, loading = false,
                error = if (sections.values.all { it.isEmpty() }) "Não foi possível consultar Cinemeta e AniList. Verifica a Internet." else null
            )
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
        _state.value = _state.value.copy(
            selected = item, episodes = emptyList(), season = 1, episode = 1,
            resolving = true, servers = emptyList(), playing = null, resolveFinished = false,
            savedProgressMs = history.read(item.sourceId, 1, 1).takeIf { it.canResume }?.positionMs ?: 0L,
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
                // On explicit "Encontrar fontes online", search *all* enabled compatible
                // providers instead of stopping early when the first link appears.
                streams.resolveServers(identity, includeFallbacks = true).collect { resolution ->
                    if (_state.value.selected?.sourceId == item.sourceId) {
                        _state.value = _state.value.copy(
                            servers = resolution.servers.filter {
                                it.url.startsWith("https://") || it.url.startsWith("http://")
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
