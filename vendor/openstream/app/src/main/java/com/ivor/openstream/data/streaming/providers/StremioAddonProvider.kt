package com.ivor.openstream.data.streaming.providers

import android.util.Log
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.data.subtitles.SavedSubtitleRepository
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * External links in Stremio are generally websites. Only promote explicit, direct
 * media paths into the VistaStream player. Query strings (signed video URLs) stay
 * intact; arbitrary HTML pages never masquerade as video.
 */
internal fun directMediaExternalUrl(raw: String?): String? {
    val url = raw?.trim()?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?.toHttpUrlOrNull() ?: return null
    val path = url.encodedPath.lowercase(java.util.Locale.ROOT)
    val supported = listOf(".mp4", ".m4v", ".mkv", ".webm", ".m3u8", ".mpd", ".mov", ".ts")
    return url.toString().takeIf { supported.any { extension -> path.endsWith(extension) } }
}

/** Stremio fileIdx is zero-based; TorrServer file IDs are one-based. */
internal fun stremioToTorrServerIndex(index: Int?): Int? =
    index?.takeIf { it in 0..9_999 }?.plus(1)

/**
 * A Stremio add-on, spoken over its public HTTP protocol
 * (https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md):
 * `{base}/manifest.json` says which content types and id schemes it serves, and
 * `{base}/stream/{movie|series}/{id}.json` lists streams, where `id` is an IMDb id
 * (`tt0133093`, or `tt0903747:1:1` for an episode) or, when the add-on accepts them, `tmdb:` ids.
 *
 * Direct URLs play with Media3; torrent hashes work only when an optional local/LAN
 * TorrServer or the opt-in native engine is configured; fileIdx can be supplied
 * by the addon or selected from local metadata. The TorrServer bridge may select the largest
 * video file from server metadata. The Stremio
 * externalUrl field is a webpage link; only URLs clearly naming playable video
 * files are accepted, never arbitrary websites or protected players.
 * Configured add-ons keep their settings in the base URL.
 */
class StremioAddonProvider(
    override val id: String,
    override val displayName: String,
    override val priority: Int,
    override val isFallback: Boolean,
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json,
    private val torrentBridge: TorrServerBridge? = null,
    private val nativeBridge: VistaNativeP2pBridge? = null
) : StreamProvider {
    override val isEnabled: Boolean = true

    /** Read once per app run; add-ons rarely change what they serve. */
    @Volatile
    private var addonInfo: AddonInfo? = null

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = withContext(Dispatchers.IO) {
        try {
            val info = addonInfo ?: loadAddonInfo().also { addonInfo = it }
            val type = if (identity.tmdbType == "movie") "movie" else "series"
            if (info.types.isNotEmpty() && type !in info.types) return@withContext Result.success(emptyList())

            // The add-on's preferred id scheme first; the next only when the first finds nothing.
            for (videoId in candidateIds(identity, info)) {
                val servers = fetchStreams(type, videoId, identity.title, identity.season, identity.episode)
                if (servers.isNotEmpty()) return@withContext Result.success(servers)
            }
            Result.success(emptyList())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "$displayName: ${error.message}")
            Result.failure(error)
        }
    }

    private fun candidateIds(identity: MediaIdentity, info: AddonInfo): List<String> =
        stremioCandidateIds(identity, info.idPrefixes)

    private fun torrentHash(stream: JsonObject): String? =
        stremioBtih(stream.text("infoHash")) ?: stremioBtihFromMagnet(stream.text("url"))

    private fun torrentTrackers(stream: JsonObject): List<String> =
        stremioTrackerUrls(
            (stream["sources"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            stream.text("url")
        )

    private fun nativeKey(stream: JsonObject): String? {
        val hash = torrentHash(stream) ?: return null
        val hints = stream["behaviorHints"] as? JsonObject
        val idx = (stream["fileIdx"] as? JsonPrimitive)?.intOrNull ?: -1
        return hash + "/" + idx + "/" + hints?.text("filename").orEmpty()
    }

    private suspend fun fetchStreams(
        type: String, videoId: String, title: String, season: Int, episode: Int
    ): List<VideoServer> {
        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("stream")
            ?.addPathSegment(type)
            ?.addPathSegment("$videoId.json")
            ?.build()
            ?: throw IOException("Bad add-on address")
        val body = get(url.toString()) ?: return emptyList()
        val streams = (json.parseToJsonElement(body) as? JsonObject)?.get("streams") as? JsonArray ?: return emptyList()

        // Optional: only use TorrServer when the addon returns torrent results.
        // /echo verifies the local/LAN engine without downloading a torrent.
        val hasTorrent = streams.any { (it as? JsonObject)?.let(::torrentHash) != null }
        val torrentBase = if (hasTorrent) torrentBridge?.availableBase() else null
        // Native metadata discovery is slow. If TorrServer is available it handles
        // ALL results; otherwise try at most two distinct native candidates, so one
        // stalled torrent does not starve every other provider in the global search.
        val nativePrepared = mutableMapOf<String, String>()
        if (hasTorrent && torrentBase == null && nativeBridge?.isEnabled() == true) {
            val targets = streams.asSequence().mapNotNull { it as? JsonObject }
                .filter { torrentHash(it) != null }.distinctBy { nativeKey(it) }.take(2)
            for (target in targets) {
                val hash = torrentHash(target) ?: continue
                val hints = target["behaviorHints"] as? JsonObject
                val ready = nativeBridge.prepare(
                    hash,
                    (target["fileIdx"] as? JsonPrimitive)?.intOrNull,
                    hints?.text("filename"),
                    torrentTrackers(target)
                )
                if (ready != null) {
                    nativeKey(target)?.let { nativePrepared[it] = ready }
                }
            }
        }

        // Add-ons often list one release several times, once per file host; keep one entry each.
        val releases = LinkedHashMap<String, MutableList<VideoServer>>()
        var missingFileIdLookups = 0
        streams.forEach { element ->
            val stream = element as? JsonObject ?: return@forEach
            val nativeUrl = nativeKey(stream)?.let(nativePrepared::get)
            val resolver: (String, String?, List<String>) -> Int? = { hash, filename, trackers ->
                // Metadata discovery is network-bound; limit it per addon search.
                if (missingFileIdLookups++ >= 3) null
                else torrentBase?.let {
                    torrentBridge?.discoverFileIndex(
                        it, hash, filename,
                        if (type == "series") season else null,
                        if (type == "series") episode else null,
                        trackers
                    )
                }
            }
            val candidate = toCandidate(stream, title, torrentBase, nativeUrl, resolver, type == "series")
            if (candidate != null) {
                releases.getOrPut(candidate.releaseKey) { mutableListOf() } += candidate.server
            }
            // A configured TorrServer and a native engine can be alternate mirrors.
            // Leave the native source visible for manual fallback on the same device.
            if (torrentBase != null && nativeUrl != null) {
                val native = toCandidate(stream, title, null, nativeUrl, resolver, type == "series")
                if (native != null) {
                    releases.getOrPut(native.releaseKey) { mutableListOf() } +=
                        native.server.copy(id = native.server.id + "-native",
                            name = native.server.name + " · nativo")
                }
            }
        }
        val seenIds = HashSet<String>()
        val labelCounts = HashMap<String, Int>()
        // Preserve every distinct mirror the addon supplies (not just the first host
        // per release). Deduplicate identical URLs, never cap by a fixed source count.
        val allMirrors = releases.values.flatMap { it }.distinctBy { it.url }
        // Do not present a missing optional P2P engine as "no episodes exist".
        if (allMirrors.isEmpty() && hasTorrent) {
            throw IllegalStateException(
                if (torrentBase != null) "P2P_METADATA_UNAVAILABLE"
                else if (nativeBridge?.isEnabled() == true) "P2P_NATIVE_UNAVAILABLE"
                else "P2P_ENGINE_NOT_CONFIGURED"
            )
        }
        return firstPlayableMirrors(allMirrors.map { listOf(it) }).map { server ->
            // Two releases with the same label still need different ids, and names to tell them apart.
            var serverId = server.id
            var suffix = 2
            while (!seenIds.add(serverId)) serverId = "${server.id}-${suffix++}"
            val repeat = labelCounts.merge(server.name, 1) { old, added -> old + added } ?: 1
            server.copy(id = serverId, name = if (repeat > 1) "${server.name} ($repeat)" else server.name)
        }
    }

    /**
     * Opportunistic bounded preflight on each distinct candidate. All candidates
     * remain visible when the check runs out of time: slow hosts should not silently
     * disappear from the list. Actual playbacks still validate streams and fail over.
     */
    private suspend fun firstPlayableMirrors(releases: List<List<VideoServer>>): List<VideoServer> = coroutineScope {
        val playable = AtomicReferenceArray<VideoServer?>(releases.size)
        val gate = Semaphore(VERIFY_PARALLELISM)
        withTimeoutOrNull(VERIFY_BUDGET_MS) {
            releases.mapIndexed { index, mirrors ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        playable.set(index, mirrors.firstOrNull {
                            // Torrents are slow to preflight; start buffering only on play.
                            it.id.contains(":p2p:") || looksPlayable(it)
                        })
                    }
                }
            }.joinAll()
        }
        // The verification budget is for *ranking/fast rejection*, never a quota.
        // Keep the first plausible mirror when verification times out; Media3 checks
        // it on actual playback and the failover system can try the next candidate.
        List(releases.size) { index ->
            playable.get(index) ?: releases[index].firstOrNull()
        }.filterNotNull()
    }

    /** A 16-byte range request: video bytes (or an HLS playlist) rather than an error page. */
    private fun looksPlayable(server: VideoServer): Boolean = runCatching {
        val request = Request.Builder()
            .url(server.url)
            .apply { server.headers.forEach { (name, value) -> header(name, value) } }
            .header("Range", "bytes=0-15")
            .build()
        probeClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val head = ByteArray(16)
            val read = response.body?.byteStream()?.read(head) ?: -1
            if (read <= 0) return@use false
            val start = String(head, 0, read, Charsets.ISO_8859_1).trimStart()
            when {
                server.isHls -> start.startsWith("#EXTM3U")
                // HTML/XML error pages and JSON error bodies.
                start.startsWith("<") || start.startsWith("{") -> false
                else -> true
            }
        }
    }.getOrDefault(false)

    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    /** A stream as a server, plus the key that groups its mirrors on other hosts. */
    private class Candidate(val server: VideoServer, val releaseKey: String)

    private fun toCandidate(
        stream: JsonObject,
        title: String,
        torrentBase: String?,
        nativeUrl: String?,
        missingFileIndex: (String, String?, List<String>) -> Int?,
        isSeries: Boolean
    ): Candidate? {
        // Stremio externalUrl normally means a web page, NOT playable media.
        // A few addons return a genuine direct media URL here instead of under
        // "url". Accept only recognisable video files/playlists; never hand
        // arbitrary pages, login screens or DRM endpoints to Media3.
        val direct = stream.text("url")?.trim()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?: directMediaExternalUrl(stream.text("externalUrl"))
        val hash = torrentHash(stream)
        val trackers = torrentTrackers(stream)
        val hints = stream["behaviorHints"] as? JsonObject
        val filename = hints?.text("filename")
        // Stremio fileIdx uses torrent order, while TorrServer enumerates files
        // sorted by filename. Prefer exact filename from TorrServer metadata.
        val stremioIndex = (stream["fileIdx"] as? JsonPrimitive)?.intOrNull
        val discovered = if (direct == null && hash != null && torrentBase != null)
            missingFileIndex(hash, filename, trackers) else null
        // A naive fileIdx + 1 can select the wrong episode: TorrServer's file
        // ordering need not be the add-on's torrent ordering. For TV require
        // filename/episode verification from the server metadata before playback.
        val torrServerIndex = discovered ?:
            if (!isSeries) stremioToTorrServerIndex(stremioIndex) else null
        val torrServerP2p = direct == null && hash != null &&
            torrServerIndex != null && torrentBase != null
        val p2p = torrServerP2p || (direct == null && nativeUrl != null)
        val url = direct ?: if (torrServerP2p) torrentBridge?.playUrl(
            torrentBase!!, hash!!, torrServerIndex!!, trackers
        ) else nativeUrl
        if (url == null) return null

        val parts = StremioStreamLabels.Parts(
            nameLines = stream.text("name").nonBlankLines(),
            detailLines = (stream.text("title") ?: stream.text("description")).nonBlankLines(),
            filename = filename
        )
        val label = StremioStreamLabels.label(parts, title, fallback = displayName).take(MAX_LABEL_LENGTH)
        val releaseKey = parts.releaseKey.takeIf { it.length > 1 } ?: url
        val requestHeaders = ((hints?.get("proxyHeaders") as? JsonObject)?.get("request") as? JsonObject)
            ?.mapNotNull { (name, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { name to it } }
            ?.toMap()
            .orEmpty()

        val subtitles = (stream["subtitles"] as? JsonArray).orEmpty().mapNotNull { element ->
            val subtitle = element as? JsonObject ?: return@mapNotNull null
            val subtitleUrl = subtitle.text("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val rawLanguage = subtitle.text("lang")
            val language = SavedSubtitleRepository.normalizeLanguage(rawLanguage)
            StreamSubtitle(
                url = subtitleUrl,
                label = language?.let { SavedSubtitleRepository.languageName(it) } ?: rawLanguage ?: "Subtitles",
                language = language
            )
        }

        val searchable = listOfNotNull(stream.text("name"), stream.text("title"), stream.text("description"), filename)
            .joinToString(" ")
        val server = VideoServer(
            id = "$id-" + Integer.toHexString(releaseKey.hashCode()) + if (p2p) ":p2p:" else "",
            providerId = id,
            providerName = displayName,
            name = if (p2p) "$label · P2P" else label,
            url = url,
            quality = qualityOf(searchable),
            headers = mapOf("User-Agent" to BROWSER_USER_AGENT) + requestHeaders,
            subtitles = subtitles,
            isDownloadable = !p2p
        )
        return Candidate(server, releaseKey)
    }

    /**
     * An explicit resolution ("1080p") wins over words like "4K" that add-ons also use in their
     * own names ("4KHDHub 1080p" is a 1080p stream).
     */
    private fun qualityOf(text: String): StreamQuality {
        val height = Regex("(?i)(?:^|\\D)(2160|1440|1080|720|480|360)p").find(text)?.groupValues?.get(1)
        return when {
            height != null -> StreamQuality.parse("${height}p")
            Regex("(?i)\\b(4k|uhd)\\b").containsMatchIn(text) -> StreamQuality.Q2160
            else -> StreamQuality.UNKNOWN
        }
    }

    private fun loadAddonInfo(): AddonInfo {
        val body = runCatching { get("${baseUrl.trimEnd('/')}/manifest.json") }.getOrNull()
            ?: return AddonInfo(types = emptySet(), idPrefixes = null)
        val manifest = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return AddonInfo(types = emptySet(), idPrefixes = null)
        // A "stream" resource given as an object may narrow types and id prefixes for itself.
        val streamResource = (manifest["resources"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.text("name") == "stream" }
        val types = (streamResource?.get("types") ?: manifest["types"]).strings()
        val prefixes = (streamResource?.get("idPrefixes") ?: manifest["idPrefixes"])
            ?.let { it.strings() }
            ?.takeIf { it.isNotEmpty() }
        return AddonInfo(types = types.toSet(), idPrefixes = prefixes)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                // Many add-ons answer 404 for titles they don't have.
                response.code == 404 -> null
                else -> throw IOException("$displayName returned HTTP ${response.code}")
            }
        }
    }

    private data class AddonInfo(
        val types: Set<String>,
        /** Null when the add-on doesn't say: then IMDb ids are assumed, as in Stremio itself. */
        val idPrefixes: List<String>?
    )

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonElement?.strings(): List<String> =
        (this as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }.filter { it.isNotEmpty() }

    private fun String?.nonBlankLines(): List<String> =
        this?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private companion object {
        const val TAG = "StremioAddon"
        const val MAX_LABEL_LENGTH = 110
        const val VERIFY_PARALLELISM = 8
        const val VERIFY_BUDGET_MS = 8_000L
        const val PROBE_TIMEOUT_MS = 4_000L
    }
}
