package com.ivor.openstream.data.streaming.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Vista-owned optional playback adapter around the GPL-3.0 Nuvio Engine v0.1.6.
 * It produces a loopback HTTP Range URL for our existing Media3 player.
 *
 * Experimental and opt-in: no remote services, no user credentials, and no
 * torrents are opened before the switch is deliberately enabled.
 * Loopback URLs work on-device only, not directly on an external Chromecast.
 */
@Singleton
class VistaNativeP2pBridge @Inject constructor(
    @ApplicationContext context: Context
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("vs_native_p2p_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var engine: NuvioEngine? = null
    private val active = LinkedHashMap<String, String>()

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    suspend fun prepare(
        hashRaw: String,
        stremioFileIndex: Int?,
        filename: String?
    ): String? = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null
        val hash = hashRaw.trim().lowercase(Locale.ROOT)
        if (!hash.matches(Regex("[a-f0-9]{40}"))) return@withContext null
        if (stremioFileIndex != null && stremioFileIndex !in 0..9_999) return@withContext null
        val preferredName = filename?.trim()?.takeIf { it.isNotEmpty() && it.length <= 255 }
        val key = "$hash/${stremioFileIndex ?: -1}/${preferredName.orEmpty()}"
        try {
            withTimeout(16_000L) {
                mutex.withLock {
                    active[key]?.let { return@withLock it }
                    val runtime = engine ?: NuvioEngine.create(
                        NuvioEngineConfig(
                            // Keep ALL P2P data in the app's disposable cache area,
                            // never in persistent app files (watch history stays separate).
                            dataDirectory = File(app.cacheDir, "vista-p2p-engine").apply { mkdirs() },
                            cacheDirectory = File(app.cacheDir, "vista-p2p-pieces").apply { mkdirs() },
                            memoryCacheCapacityBytes = 32L * 1024 * 1024,
                            diskCacheCapacityBytes = 128L * 1024 * 1024,
                            torrentProfile = NuvioTorrentProfile.Balanced,
                            uploadMode = NuvioUploadMode.Disabled,
                            streamInactivityTimeoutMilliseconds = 0
                        )
                    ).also { engine = it }
                    val id = runtime.addMagnet("magnet:?xt=urn:btih:$hash")
                    val prepared = runtime.prepareStream(
                        torrentId = id,
                        fileIndex = if (preferredName == null) stremioFileIndex else null,
                        filenameHint = preferredName
                    )
                    val uri = Uri.parse(prepared.url)
                    if (uri.scheme != "http" || uri.host != "127.0.0.1" ||
                        uri.port !in 1..65_535 || uri.path?.startsWith("/stream/") != true
                    ) return@withLock null
                    active[key] = prepared.url
                    prepared.url
                }
            }
        } catch (_: TimeoutCancellationException) {
            // A tracker without peers must not cancel the other addon searches.
            null
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.w("VistaNativeP2p", "Native P2P source unavailable: ${error.javaClass.simpleName}")
            null
        }
    }
}
