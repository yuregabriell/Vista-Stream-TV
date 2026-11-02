package com.ivor.openstream.data.remote

import android.content.Context
import com.ivor.openstream.data.remote.model.WatchProviderDto
import com.ivor.openstream.data.remote.model.WatchProviderRegionDto
import com.ivor.openstream.data.remote.model.WatchProviderDirectoryDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Built-in optional metadata addon. Uses TMDB's JustWatch-powered provider directory;
 * no separate scraper, user credentials, API key or APK per streaming brand.
 *
 * This does not provide playback or claim a subscription. It only describes which
 * licensed platforms TMDB lists for the selected PT region.
 */
@Singleton
class TvStreamingProvidersAddon @Inject constructor(
    @ApplicationContext context: Context,
    private val api: TmdbApi
) {
    private val prefs = context.getSharedPreferences("vista_tv_watch_providers", Context.MODE_PRIVATE)
    // Native, read-only provider badges are enabled by default on a fresh install.
    // Preserve an explicit user choice to disable the feature.
    private val _enabled = MutableStateFlow(prefs.getBoolean("enabled", true))
    val enabled: StateFlow<Boolean> = _enabled
    private val region = "PT"
    private val directoryLock = Mutex()
    private var directory: List<WatchProviderDto>? = null

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("enabled", value).apply()
        _enabled.value = value
    }

    suspend fun availability(mediaType: String, id: Int): WatchProviderRegionDto? {
        if (!_enabled.value) return null
        val response = if (mediaType == "movie") api.movieWatchProviders(id) else api.tvWatchProviders(id)
        return response.results[region]
    }

    suspend fun directory(): List<WatchProviderDto> = directoryLock.withLock {
        if (!_enabled.value) return@withLock emptyList()
        directory?.let { return@withLock it }
        val movies = runCatching { api.movieWatchProviderDirectory(region).results }.getOrDefault(emptyList())
        val series = runCatching { api.tvWatchProviderDirectory(region).results }.getOrDefault(emptyList())
        val both = (movies + series)
            .distinctBy { it.providerId }
            .sortedWith(compareBy<WatchProviderDto> { it.displayPriority }.thenBy { it.providerName })
        directory = both
        both
    }
}
