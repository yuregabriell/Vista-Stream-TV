package com.ivor.openstream.data.streaming.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ivor.openstream.data.cache.CacheBudget
import com.ivor.openstream.presentation.tv.TvDetect
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.io.IOException
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
/**
 * The Nuvio Engine stores pieces at <cacheDirectory>/payload/<torrentHash>.
 * Keep deletion constrained to a direct, non-symlinked hashed child.
 */
internal fun isolatedTorrentPayloadDirectory(cacheDirectory: File, torrentId: String): File? {
    val hash = torrentId.lowercase(Locale.ROOT)
    if (!hash.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}"))) return null
    return runCatching {
        val parent = File(cacheDirectory, "payload")
        val target = File(parent, hash)
        if (Files.isSymbolicLink(parent.toPath()) || Files.isSymbolicLink(target.toPath())) {
            return@runCatching null
        }
        val canonical = target.canonicalFile
        if (canonical.parentFile != parent.canonicalFile) return@runCatching null
        canonical
    }.getOrNull()
}

/**
 * Remove only the native torrent engine's disposable on-disk directories.
 * Called on fresh process startup (after a forced close/crash) and when the final
 * native torrent is released. Never traverse symlinks or touch Media3's shared
 * cache, artwork, offline downloads or watch history.
 */
internal fun purgeNativeP2pTemporaryData(appCacheDir: File): Boolean {
    val root = runCatching { appCacheDir.canonicalFile }.getOrNull() ?: return false
    if (!root.isDirectory) return false
    var success = true
    for (name in listOf("vista-p2p-pieces", "vista-p2p-engine")) {
        val directory = File(root, name)
        val path = directory.toPath()
        if (!Files.exists(path) && !Files.isSymbolicLink(path)) continue
        if (Files.isSymbolicLink(path) || directory.canonicalFile.parentFile != root) {
            success = false
            continue
        }
        val removed = runCatching {
            // walkFileTree does not follow symbolic links by default.
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            })
            true
        }.getOrDefault(false)
        if (!removed) success = false
    }
    return success
}

/** A scoped delete with no effect on any other torrent, poster, or app cache. */
internal fun purgeIsolatedTorrentPayload(cacheDirectory: File, torrentId: String): Boolean {
    val target = isolatedTorrentPayloadDirectory(cacheDirectory, torrentId) ?: return false
    return !target.exists() || target.deleteRecursively()
}

@Singleton
class VistaNativeP2pBridge @Inject constructor(
    @ApplicationContext context: Context
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("vs_native_p2p_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var engine: NuvioEngine? = null

    private data class PreparedStream(
        val torrentId: String,
        val streamId: String,
        val url: String
    )

    // Maps prepared sources back to their exact torrent. Never delete the app-wide
    // cache, only a completed torrent's own verified payload directory.
    private val active = LinkedHashMap<String, PreparedStream>()
    // Read by the Android task-removal callback without crossing the IO mutex.
    @Volatile private var activeStreamUrls: Set<String> = emptySet()

    fun isActiveNativeStream(url: String?): Boolean =
        url != null && url in activeStreamUrls

    /** Low-resource TV devices should never warm multiple torrents per addon. */
    fun isTelevisionDevice(): Boolean = TvDetect.isTv(app)
    // Includes hashes whose metadata lookup failed before a stream could be
    // prepared, so an abandoned search cannot leave orphan payloads behind.
    private val attemptedHashes = LinkedHashSet<String>()


    /**
     * After Media3 has stopped or switched streams, release only native torrents
     * no longer referenced by the active video. A source retained in the mobile
     * mini player is kept alive. This never touches posters, downloads, library
     * files or shared HTTP/Media3 caches.
     *
     * Nuvio's removeTorrent() stops the native handle but intentionally preserves
     * downloaded payload; its per-torrent directory is therefore removed here
     * ONLY after the native removed event has been received.
     */
    suspend fun releaseUnusedExcept(currentMediaUrl: String?): List<String> =
        withContext(Dispatchers.IO) {
        mutex.withLock {
            val runtime = engine ?: return@withLock emptyList()
            val unused = active.filterValues { it.url != currentMediaUrl }
            if (unused.isEmpty()) {
                if (active.isEmpty() && currentMediaUrl == null) {
                    // Source searches can time out before prepareStream() returns.
                    // Their hashes still belong exclusively to this P2P cache.
                    runtime.close()
                    engine = null
                    attemptedHashes.forEach { id ->
                        purgeIsolatedTorrentPayload(File(app.cacheDir, "vista-p2p-pieces"), id)
                    }
                    attemptedHashes.clear()
                    if (!purgeNativeP2pTemporaryData(app.cacheDir)) {
                        Log.w("VistaNativeP2p", "Native P2P temporary cache cleanup was incomplete")
                    }
                }
                return@withLock emptyList()
            }
            val removedMediaUrls = mutableListOf<String>()
            val stillInUse = active.values.filter { it.url == currentMediaUrl }
                .map { it.torrentId }.toSet()

            for ((torrentId, sessions) in unused.values.groupBy { it.torrentId }) {
                for (stream in sessions.distinctBy { it.streamId }) {
                    // Nuvio can already have stopped an expired HTTP stream.
                    runCatching { runtime.stopStream(stream.streamId) }
                }
                if (torrentId in stillInUse) {
                    removedMediaUrls += sessions.map { it.url }
                    active.entries.removeAll { it.value.torrentId == torrentId &&
                        it.value.url != currentMediaUrl }
                    continue
                }

                val removed = runCatching {
                    runtime.removeTorrent(torrentId)
                    true
                }.getOrElse {
                    Log.w("VistaNativeP2p", "Cannot release torrent: ${it.javaClass.simpleName}")
                    false
                }
                if (!removed) continue

                active.entries.removeAll { it.value.torrentId == torrentId }
                removedMediaUrls += sessions.map { it.url }
                if (!purgeIsolatedTorrentPayload(
                    File(app.cacheDir, "vista-p2p-pieces"), torrentId
                )) {
                    Log.w("VistaNativeP2p", "Could not erase an isolated torrent payload")
                }
            }
            if (active.isEmpty()) {
                // Avoid holding libtorrent, sockets and memory caches after exit.
                runtime.close()
                engine = null
                attemptedHashes.forEach { id ->
                    purgeIsolatedTorrentPayload(File(app.cacheDir, "vista-p2p-pieces"), id)
                }
                attemptedHashes.clear()
                    if (!purgeNativeP2pTemporaryData(app.cacheDir)) {
                        Log.w("VistaNativeP2p", "Native P2P temporary cache cleanup was incomplete")
                    }
            }
            activeStreamUrls = active.values.map { it.url }.toSet()
            // Only these unique loopback URLs are safe to evict from Media3's
            // shared HTTP cache. Never use Cache.evictAll() on exit.
            removedMediaUrls.distinct()
        }
    }

    fun isEnabled(): Boolean = prefs.getBoolean("enabled", true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
    }

    suspend fun prepare(
        hashRaw: String,
        stremioFileIndex: Int?,
        filename: String?,
        trackers: List<String> = emptyList()
    ): String? = withContext(Dispatchers.IO) {
        if (!isEnabled()) return@withContext null
        val hash = stremioBtih(hashRaw) ?: return@withContext null
        val magnet = stremioMagnet(hash, trackers) ?: return@withContext null
        if (stremioFileIndex != null && stremioFileIndex !in 0..9_999) return@withContext null
        val preferredName = filename?.trim()?.takeIf { it.isNotEmpty() && it.length <= 255 }
        val key = "$hash/${stremioFileIndex ?: -1}/${preferredName.orEmpty()}"
        try {
            withTimeout(16_000L) {
                mutex.withLock {
                    active[key]?.let { return@withLock it.url }
                    val runtime = engine ?: NuvioEngine.create(
                        NuvioEngineConfig(
                            // Keep ALL P2P data in the app's disposable cache area,
                            // never in persistent app files (watch history stays separate).
                            dataDirectory = File(app.cacheDir, "vista-p2p-engine").apply { mkdirs() },
                            cacheDirectory = File(app.cacheDir, "vista-p2p-pieces").apply { mkdirs() },
                            memoryCacheCapacityBytes = if (TvDetect.isTv(app))
                                CacheBudget.TV_P2P_MEMORY_MAX_BYTES else 32L * 1024 * 1024,
                            diskCacheCapacityBytes = if (TvDetect.isTv(app))
                                CacheBudget.TV_P2P_DISK_MAX_BYTES else 128L * 1024 * 1024,
                            torrentProfile = NuvioTorrentProfile.Balanced,
                            uploadMode = NuvioUploadMode.Disabled,
                            streamInactivityTimeoutMilliseconds = 0
                        )
                    ).also { engine = it }
                    attemptedHashes += hash
                    val id = runtime.addMagnet(magnet)
                    val prepared = runtime.prepareStream(
                        torrentId = id,
                        fileIndex = if (preferredName == null) stremioFileIndex else null,
                        filenameHint = preferredName
                    )
                    val uri = Uri.parse(prepared.url)
                    if (uri.scheme != "http" || uri.host != "127.0.0.1" ||
                        uri.port !in 1..65_535 || uri.path?.startsWith("/stream/") != true
                    ) return@withLock null
                    active[key] = PreparedStream(
                        torrentId = prepared.torrentId,
                        streamId = prepared.id,
                        url = prepared.url
                    )
                    activeStreamUrls = active.values.map { it.url }.toSet()
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
