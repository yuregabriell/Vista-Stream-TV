package com.ivor.openstream

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.ivor.openstream.presentation.tv.TvDetect
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ivor.openstream.data.cache.CacheBudget
import com.ivor.openstream.data.diagnostics.Diagnostics
import com.ivor.openstream.data.downloads.SmartDownloads
import com.ivor.openstream.data.notifications.EpisodeAlarm
import com.ivor.openstream.data.settings.AppDns
import com.ivor.openstream.data.settings.AppLocale
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.data.settings.CacheSize
import com.ivor.openstream.data.streaming.providers.purgeNativeP2pTemporaryData
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class OpenStreamApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var appDns: AppDns

    @Inject
    lateinit var appSettingsStore: AppSettingsStore

    @Inject
    lateinit var smartDownloads: SmartDownloads

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base, AppLocale.savedTag(base)))
    }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.installCrashRecorder(this)
        // A terminated process cannot run cleanup callbacks. Reclaim only our native
        // engine's disposable torrent data before any player can be started again.
        if (!purgeNativeP2pTemporaryData(cacheDir)) {
            Log.w("VistaNativeP2p", "Could not fully remove stale P2P pieces at startup")
        }
        // Daily episode check, only while the toggle is on.
        if (appSettingsStore.current.episodeNotifications &&
            !EpisodeAlarm.isScheduled(this)
        ) {
            EpisodeAlarm.schedule(this)
        }
        smartDownloads.start()
    }

    /**
     * Artwork goes through the app's DNS setting too, so posters load where TMDB's DNS is blocked.
     * The disk cache stays in Coil's default folder (so nothing cached earlier is lost) and is
     * sized by the Cache size setting.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Share connections for visible artwork, but avoid flooding the
        // metadata gateway on low-memory TV sticks.
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 4
        }
        val client = OkHttpClient.Builder().dns(appDns).dispatcher(dispatcher).build()
        val directory = File(cacheDir, CacheBudget.IMAGE_DIR)
        // Hilt injects in onCreate; nothing should ask for images before that, but never crash on it.
        val wanted = (if (::appSettingsStore.isInitialized) appSettingsStore.current.cacheSize else CacheSize.STANDARD).imageBytes
        val tvDevice = TvDetect.isTv(this)
        return ImageLoader.Builder(context)
            // A TV can hold several cinematic backdrops plus Media3 decoder buffers.
            // Keep its decoded-bitmaps LRU modest: evicted images still reload from disk.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, if (tvDevice) 0.08 else 0.25)
                    .build()
            }
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .diskCache {
                DiskCache.Builder()
                    .directory(directory.toOkioPath())
                    // Bound TV artwork to 48 MB regardless of the phone preset;
                    // Android can evict it safely when storage is under pressure.
                    .maxSizeBytes(CacheBudget.bytesFor(directory,
                        if (tvDevice) wanted.coerceAtMost(48L * 1024L * 1024L) else wanted))
                    .build()
            }
            .build()
    }
}
