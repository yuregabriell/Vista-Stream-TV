package com.ivor.openstream.harvester

import android.content.Context
import android.net.Uri
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** Explicit user approved video sources; never store authentication cookies. */
@Serializable
data class NativeSavedStream(
    val url: String, val title: String, val pageUrl: String = "",
    val mime: String = "", val kind: String = "movie",
    val season: Int = 1, val episode: Int = 1,
    val year: Int = 0, val audioMode: String = "", val language: String = "",
    val quality: String = "", val savedAt: Long = System.currentTimeMillis()
)

class NativeHarvesterStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vs_native_harvester_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(NativeSavedStream.serializer())

    @Synchronized fun all(): List<NativeSavedStream> = runCatching {
        json.decodeFromString(serializer, prefs.getString("items", "[]") ?: "[]")
    }.getOrDefault(emptyList())

    @Synchronized fun find(identity: MediaIdentity): List<NativeSavedStream> {
        val titles = listOfNotNull(identity.title, identity.originalTitle).map(::fold).filter { it.length >= 3 }
        return all().filter { item ->
            val isMovie = identity.tmdbType == "movie"
            (if (isMovie) item.kind == "movie" else item.kind != "movie") &&
                titles.any { it == fold(item.title) } &&
                (item.year == 0 || identity.year == null || item.year == identity.year) &&
                (isMovie || (item.season == identity.season && item.episode == identity.episode)) &&
                safeDirect(item.url)
        }
    }

    @Synchronized fun save(item: NativeSavedStream): Boolean {
        if (!safeDirect(item.url) || item.title.isBlank() ||
            item.season !in 1..999 || item.episode !in 1..9999) return false
        val old = all()
        val merged = listOf(item) + old.filterNot {
            it.url == item.url && it.season == item.season && it.episode == item.episode
        }
        prefs.edit().putString("items", json.encodeToString(serializer, merged.take(250))).apply()
        return true
    }

    @Synchronized fun remove(url: String) {
        prefs.edit().putString("items", json.encodeToString(serializer, all().filterNot { it.url == url })).apply()
    }

    companion object {
        fun fold(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

        fun safeDirect(raw: String): Boolean {
            val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return false
            if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return false
            val query = uri.query.orEmpty().lowercase()
            if (Regex("(^|&)(token|sig|signature|expires|expiry|ip_bind|auth|key|policy|session)=").containsMatchIn(query)) return false
            if (raw.length > 4096 || raw.contains("@")) return false
            return Regex("(?i)\\.(mp4|m4v|webm|m3u8|mpd|mkv|mov|ts)$")
                .containsMatchIn(uri.path.orEmpty())
        }
    }
}

class NativeHarvesterProvider(private val store: NativeHarvesterStore) : StreamProvider {
    override val id = "native-harvester"
    override val displayName = "Harvester Browser"
    override val priority = 30
    override val isEnabled = true
    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = runCatching {
        store.find(identity).mapIndexed { i, item ->
            VideoServer(
                id = "native-harvester-" + item.url.hashCode() + "-" + i,
                providerId = id, providerName = displayName,
                name = "Harvester · " + item.quality.ifBlank { "Auto" } +
                    (if (item.language.isNotBlank()) " · " + item.language else ""),
                url = item.url, quality = StreamQuality.parse(item.quality),
                audio = when (item.audioMode.lowercase()) {
                    "dub" -> StreamAudio.DUB
                    "multi" -> StreamAudio.MULTI
                    "sub" -> StreamAudio.SUB
                    else -> StreamAudio.UNKNOWN
                },
                audioLanguage = item.language.takeIf { it.isNotBlank() },
                mimeType = item.mime.takeIf { it.isNotBlank() },
                headers = item.pageUrl.takeIf { it.startsWith("https://") }
                    ?.let { mapOf("Referer" to it) } ?: emptyMap()
            )
        }
    }
}
