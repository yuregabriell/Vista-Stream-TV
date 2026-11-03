package com.ivor.openstream.data.cache

import android.content.Context
import com.ivor.openstream.presentation.tv.TvDetect
import com.ivor.openstream.data.streaming.providers.VistaNativeP2pBridge
import com.ivor.openstream.data.streaming.providers.purgeNativeP2pTemporaryData
import coil3.SingletonImageLoader
import com.ivor.openstream.domain.repository.AnimeRepository
import com.ivor.openstream.domain.repository.StreamingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import com.ivor.openstream.data.streaming.IdMappingService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every cache the app keeps for speed, as one thing Settings can size and clear: artwork (Coil),
 * TMDB answers (OkHttp), Home's built lists, recently found streams and remembered id mappings.
 * None of it is user data; downloads, progress and saved subtitles live elsewhere and are never
 * touched here.
 */
@Singleton
class AppCaches @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataCache: Cache,
    private val animeRepository: AnimeRepository,
    private val streamingRepository: StreamingRepository,
    private val idMappingService: IdMappingService,
    private val nativeP2pBridge: VistaNativeP2pBridge
) {
    /** Bytes on disk across artwork and title info, or null when it can't be read. */
    suspend fun sizeBytes(): Long? = withContext(Dispatchers.IO) {
        // On TV include all of cacheDir, especially the native torrent data that
        // Coil + OkHttp size alone previously omitted from Settings' displayed total.
        if (TvDetect.isTv(context)) return@withContext CacheBudget.usedBytes(context.cacheDir)
        val images = runCatching { SingletonImageLoader.get(context).diskCache?.size }.getOrNull()
        val metadata = runCatching { metadataCache.size() }.getOrNull()
        if (images == null && metadata == null) null else (images ?: 0L) + (metadata ?: 0L)
    }

    /** Clears everything; each part on its own, so one failing doesn't keep the rest. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        runCatching {
            val loader = SingletonImageLoader.get(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        runCatching { metadataCache.evictAll() }
        animeRepository.clearCachedCatalogs()
        streamingRepository.clearResolvedServers()
        runCatching { idMappingService.clear() }
        if (TvDetect.isTv(context)) {
            // The TV does not keep a background mini-player, unlike mobile.
            // When the user requests cache clearing from TV Settings, stop any
            // orphaned engine and delete only its disposable fragment directories.
            runCatching {
                nativeP2pBridge.releaseUnusedExcept(null)
                purgeNativeP2pTemporaryData(context.cacheDir)
            }
        }
    }
}
