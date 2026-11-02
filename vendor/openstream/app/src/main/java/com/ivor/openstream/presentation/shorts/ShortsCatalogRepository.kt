package com.ivor.openstream.presentation.shorts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A real playable source, not a list of external sites. Uses Sepia Search for
 * discovery, then the public video API on the video's own PeerTube instance
 * to resolve a playable MP4/HLS stream. No embedded websites, scripts,
 * third-party scraper, or login-token bypass.
 *
 * One standalone film is one COMPLETE work. Do not mistake arbitrary clips
 * for complete multi-episode drama series. Other adapters may be added only
 * after playback and rights are independently validated.
 */
internal object ShortsCatalogRepository {
    private const val INDEX = "https://sepiasearch.org/api/v1/search/videos"
    private const val MAX_PER_PAGE = 20
    private const val MAX_PLAYABLE_PER_PAGE = 16
    private val allowedLicences = setOf(1, 2, 7, 8) // CC BY, BY-SA, CC0, public domain.
    private val limit = Semaphore(5)

    private fun JsonObject.text(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        get(key)?.jsonPrimitive?.intOrNull ?: 0

    private fun JsonObject.obj(key: String): JsonObject? =
        get(key) as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? =
        get(key) as? JsonArray

    private fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

    private fun safeHttps(url: String): Boolean = try {
        val parsed = URL(url)
        parsed.protocol == "https" && parsed.host.isNotBlank() &&
            parsed.userInfo == null && parsed.port in setOf(-1, 443)
    } catch (_: Exception) { false }

    private fun jsonFrom(url: String): JsonObject {
        require(safeHttps(url)) { "HTTPS is required" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 6500
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VistaStream-Shorts/1.0")
        }
        try {
            if (connection.responseCode != 200) error("Source HTTP " + connection.responseCode)
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            require(text.length <= 2_500_000) { "Unexpected response" }
            return Json.parseToJsonElement(text).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun firstPlayableUrl(details: JsonObject): String? {
        val playlists = details.arr("streamingPlaylists").orEmpty()
        val hls = playlists.mapNotNull { it.objectOrNull()?.text("playlistUrl") }
            .firstOrNull { safeHttps(it) && it.contains(".m3u8", ignoreCase = true) }
        if (hls != null) return hls
        val files = details.arr("files").orEmpty().mapNotNull { it.objectOrNull() }
        return files.map { it.text("fileUrl") }.firstOrNull(::safeHttps)
    }

    private fun poster(host: String, path: String): String {
        if (safeHttps(path)) return path
        return if (path.startsWith("/")) "https://" + host + path else ""
    }

    private fun resolved(item: JsonObject): ShortDrama? {
        val licence = item.obj("licence")?.int("id") ?: 9
        if (licence !in allowedLicences || item.text("name").isBlank()) return null
        val duration = item.int("duration")
        if (duration !in 50..3600) return null
        if (item.get("isLive")?.jsonPrimitive?.booleanOrNull == true) return null
        val originalUrl = item.text("url")
        if (!safeHttps(originalUrl)) return null
        val host = URL(originalUrl).host
        val uuid = item.text("uuid")
        if (!Regex("[a-fA-F0-9-]{36}").matches(uuid)) return null
        val detailUrl = "https://" + host + "/api/v1/videos/" + uuid
        if (!safeHttps(detailUrl)) return null
        val details = jsonFrom(detailUrl)
        // Recheck licence on origin: the global index can be stale.
        val originLicence = details.obj("licence")?.int("id") ?: 9
        if (originLicence !in allowedLicences || details.int("duration") !in 50..3600)
            return null
        if (details.get("isLive")?.jsonPrimitive?.booleanOrNull == true) return null
        val videoUrl = firstPlayableUrl(details) ?: return null
        val title = details.text("name").ifBlank { item.text("name") }
        val description = details.text("description")
            .ifBlank { details.text("truncatedDescription") }
            .ifBlank { item.text("truncatedDescription") }.take(700)
        val thumbnail = poster(host, details.text("thumbnailPath")
            .ifBlank { item.text("thumbnailPath") })
        val preview = poster(host, details.text("previewPath")
            .ifBlank { item.text("previewPath") })
        val licenceName = details.obj("licence")?.text("label")
            ?.ifBlank { "Creative Commons / domínio público" }
            ?: "Creative Commons / domínio público"
        return ShortDrama(
            id = "pt-" + uuid,
            title = title,
            summary = description,
            genre = "Curta independente",
            poster = thumbnail.ifBlank { preview },
            totalEpisodes = 1,
            licence = licenceName,
            permissionProofUrl = originalUrl,
            verifiedFreeAdFreeDistribution = true,
            episodes = listOf(ShortEpisode(1, "Obra completa", videoUrl)),
            backdrop = preview.ifBlank { thumbnail },
            sourceName = "PeerTube",
            kind = "curta"
        )
    }

    /**
     * The API only supplies *candidates*. We preflight individual origin
     * endpoints before returning entries to the UI. Failures aren't presented
     * as playable titles and never trigger a browser fallback.
     */
    suspend fun page(query: String, offset: Int): List<ShortDrama> =
        withContext(Dispatchers.IO) {
            val search = query.ifBlank { "short drama" }.take(100)
            val url = INDEX + "?search=" + URLEncoder.encode(search, "UTF-8") +
                "&start=" + offset.coerceAtLeast(0) + "&count=" + MAX_PER_PAGE +
                "&nsfw=false"
            val records = jsonFrom(url).arr("data").orEmpty()
                .mapNotNull { it.objectOrNull() }
                .filter { it.obj("licence")?.int("id") in allowedLicences }
            coroutineScope {
                records.take(MAX_PLAYABLE_PER_PAGE).map { item ->
                    async {
                        limit.withPermit {
                            runCatching { resolved(item) }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull().distinctBy { it.id }
            }
        }

    const val PAGE_SIZE = MAX_PER_PAGE
}
