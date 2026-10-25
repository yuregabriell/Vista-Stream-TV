package com.ivor.openstream.data.catalog

import android.content.Context
import android.net.Uri
import com.ivor.openstream.data.settings.TmdbKeyStore
import com.ivor.openstream.data.settings.MetadataGatewayStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
data class KeylessTitle(
    val sourceId: String,
    val title: String,
    val type: String,
    val year: Int? = null,
    val poster: String? = null,
    val background: String? = null,
    val description: String = "",
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val anilistId: Int? = null,
    val episodes: Int? = null,
    val alternateTitle: String? = null,
    val genres: List<String> = emptyList(),
    val format: String? = null
)

data class KeylessEpisode(val season: Int, val number: Int, val title: String)

@Singleton
class KeylessCatalogClient @Inject constructor(
    @Named("StreamingClient") private val http: OkHttpClient,
    private val json: Json,
    @ApplicationContext context: Context,
    private val keyStore: TmdbKeyStore,
    private val gatewayStore: MetadataGatewayStore
) {
    private val prefs = context.getSharedPreferences("vista_keyless_catalog_v1", Context.MODE_PRIVATE)
    val tmdbConfigured: Boolean get() = true // Public, throttled server is bundled by URL only; no TMDB secret

    /** Cinemeta is a Stremio metadata/catalog service; it does NOT host the movies. */
    suspend fun titles(type: String, query: String = "", page: Int = 1, force: Boolean = false, genre: String = "", year: Int? = null): List<KeylessTitle> = withContext(Dispatchers.IO) {
        val cacheKey = "${type}_${query.trim().lowercase()}_${genre}_${year ?: 0}_$page"
        if (!force && query.isBlank() && genre.isBlank() && year == null && page == 1 &&
            System.currentTimeMillis() - prefs.getLong("${cacheKey}_ts", 0L) < 6 * 60 * 60 * 1000L) {
            prefs.getString(cacheKey, null)?.let { raw ->
                runCatching { json.decodeFromString<List<KeylessTitle>>(raw) }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }
        }
        val results = runCatching {
            when (type) {
                "anime" -> anime(query, page, genre, year)
                "movie", "series" -> cinemeta(type, query, page, genre, year)
                else -> emptyList()
            }
        }.getOrElse { problem ->
            // Keep the last-known-good catalog even if it is older than the refresh TTL.
            if (query.isBlank() && genre.isBlank() && year == null && page == 1) {
                prefs.getString(cacheKey, null)?.let { raw ->
                    runCatching { json.decodeFromString<List<KeylessTitle>>(raw) }.getOrNull()
                } ?: throw problem
            } else throw problem
        }
        if (query.isBlank() && genre.isBlank() && year == null && results.isNotEmpty() && page == 1) {
            prefs.edit().putString(cacheKey, json.encodeToString(results))
                .putLong("${cacheKey}_ts", System.currentTimeMillis()).apply()
        }
        results
    }

    /**
     * Home initially showed one tiny public feed per type. Fetch multiple pages
     * and keep the larger aggregated index cached to reduce calls and startup work.
     * Metadata does not imply playable streams.
     */
    suspend fun refresh(force: Boolean = false): Map<String, List<KeylessTitle>> = supervisorScope {
        val cacheName = "home_public_catalog_v2"
        val saved = prefs.getString(cacheName, null)?.let { raw ->
            runCatching { json.decodeFromString<Map<String, List<KeylessTitle>>>(raw) }.getOrNull()
        }
        val fresh = System.currentTimeMillis() - prefs.getLong("${cacheName}_ts", 0L) <
            3L * 60 * 60 * 1_000
        if (!force && fresh && saved?.values?.any { it.isNotEmpty() } == true)
            return@supervisorScope saved

        val catalog = listOf("movie", "series", "anime").map { type ->
            async {
                val pages = (1..4).map { page ->
                    runCatching { titles(type, page = page, force = force) }.getOrDefault(emptyList())
                }
                type to pages.flatten().distinctBy { it.sourceId }.take(360)
            }
        }.map { it.await() }.toMap()
        val merged = catalog.mapValues { (type, values) ->
            values.ifEmpty { saved?.get(type).orEmpty() }
        }
        if (merged.values.any { it.isNotEmpty() }) {
            prefs.edit().putString(cacheName, json.encodeToString(merged))
                .putLong("${cacheName}_ts", System.currentTimeMillis()).apply()
        }
        merged
    }

    /**
     * Cinemeta may provide moviedb_id directly in its metadata. This lets the
     * existing TMDB-id-based resolvers run WITHOUT an API key or a TMDB request.
     */
    suspend fun enrich(item: KeylessTitle): KeylessTitle = withContext(Dispatchers.IO) {
        if (item.imdbId == null) return@withContext item
        val type = if (item.type == "movie") "movie" else "series"
        val raw = request("https://v3-cinemeta.strem.io/meta/$type/${item.imdbId}.json")
        val meta = (json.parseToJsonElement(raw) as? JsonObject)?.get("meta") as? JsonObject
            ?: return@withContext item
        item.copy(
            tmdbId = meta.str("moviedb_id")?.toIntOrNull()
                ?: meta.str("tmdb_id")?.toIntOrNull()
                ?: item.tmdbId,
            description = item.description.ifBlank { meta.str("description").orEmpty() },
            background = item.background ?: meta.str("background"),
            poster = item.poster ?: meta.str("poster")
        )
    }

    suspend fun episodes(item: KeylessTitle): List<KeylessEpisode> = withContext(Dispatchers.IO) {
        if (item.imdbId.isNullOrBlank() || item.type == "movie") return@withContext emptyList()
        val data = request("https://v3-cinemeta.strem.io/meta/series/${item.imdbId}.json")
        val videos = (json.parseToJsonElement(data) as? JsonObject)?.get("meta")
            ?.jsonObject?.get("videos") as? JsonArray ?: return@withContext emptyList()
        videos.mapNotNull { v ->
            val o = v as? JsonObject ?: return@mapNotNull null
            val season = o.int("season") ?: return@mapNotNull null
            val episode = o.int("episode") ?: return@mapNotNull null
            if (season < 1 || episode < 1) return@mapNotNull null
            KeylessEpisode(season, episode, o.str("title") ?: "Episódio $episode")
        }.distinctBy { it.season to it.number }.sortedWith(compareBy({ it.season }, { it.number }))
    }

    suspend fun findAnimeImdb(item: KeylessTitle): String? = withContext(Dispatchers.IO) {
        if (item.imdbId != null) return@withContext item.imdbId
        val candidates = listOfNotNull(item.alternateTitle, item.title).distinct()
        for (name in candidates) {
            val matches = runCatching { cinemeta("series", name, 1) }.getOrDefault(emptyList())
            val normalized = name.filter(Char::isLetterOrDigit).lowercase()
            val found = matches.firstOrNull {
                it.title.filter(Char::isLetterOrDigit).lowercase() == normalized ||
                    it.alternateTitle?.filter(Char::isLetterOrDigit)?.lowercase() == normalized
            }?.imdbId
            if (found != null) return@withContext found
        }
        null
    }


    private fun cinemeta(type: String, query: String, page: Int, genre: String = "", year: Int? = null): List<KeylessTitle> {
        // Cinemeta year catalog and standard genre/search/skip extras.
        val catalog = if (year != null && query.isBlank()) "year" else "top"
        val extras = buildList {
            if (catalog == "year") add("genre=${year}")
            else if (genre.isNotBlank()) add("genre=${Uri.encode(genre)}")
            if (query.isNotBlank()) add("search=${Uri.encode(query)}")
            if (page > 1) add("skip=${(page - 1) * 100}")
        }
        val path = "https://v3-cinemeta.strem.io/catalog/$type/$catalog" +
            if (extras.isNotEmpty()) "/${extras.joinToString("&")}.json" else ".json"
        val response = json.parseToJsonElement(request(path)) as? JsonObject ?: return emptyList()
        val metas = response["metas"] as? JsonArray ?: return emptyList()
        return metas.mapNotNull { it as? JsonObject }.mapNotNull { meta ->
            val id = meta.str("id") ?: return@mapNotNull null
            if (!id.startsWith("tt")) return@mapNotNull null
            val title = meta.str("name") ?: return@mapNotNull null
            if (meta.str("adult") == "true") return@mapNotNull null
            val genres = (meta["genres"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty()
            KeylessTitle(
                sourceId = "cinemeta:$id", title = title, type = type,
                year = meta.str("releaseInfo")?.take(4)?.toIntOrNull()
                    ?: meta.str("year")?.take(4)?.toIntOrNull(),
                poster = meta.str("poster"),
                background = meta.str("background") ?: meta.str("banner"),
                description = meta.str("description").orEmpty(),
                imdbId = id,
                tmdbId = meta.str("moviedb_id")?.toIntOrNull(),
                genres = genres
            )
        }.distinctBy(KeylessTitle::sourceId)
            .filter { item ->
                (year == null || item.year == null || item.year == year) &&
                    (genre.isBlank() || catalog != "year" ||
                        item.genres.any { it.equals(genre, ignoreCase = true) })
            }
    }

    private fun anime(query: String, page: Int, genre: String = "", year: Int? = null): List<KeylessTitle> {
        // Build only the requested GraphQL arguments. The no-filter request
        // deliberately matches the previously working AniList request.
        // JsonPrimitive serialises search/genre strings with proper GraphQL quotes.
        val arguments = buildList {
            add("type: ANIME")
            add("isAdult: false")
            add("sort: [TRENDING_DESC]")
            if (query.isNotBlank()) add("search: ${JsonPrimitive(query.trim())}")
            if (genre.isNotBlank()) add("genre: ${JsonPrimitive(genre)}")
            if (year != null) {
                add("startDate_greater: ${year * 10_000}")
                add("startDate_lesser: ${(year + 1) * 10_000}")
            }
        }.joinToString(", ")
        val graphql = """
          query {
            Page(page: $page, perPage: 24) {
              media($arguments) {
                id title { english romaji native } coverImage { large } bannerImage
                description(asHtml: false) startDate { year } episodes format genres
              }
            }
          }
        """.trimIndent()
        val body = buildJsonObject { put("query", graphql) }.toString()
        val request = Request.Builder().url("https://graphql.anilist.co")
            .header("Accept", "application/json")
            .header("User-Agent", "VistaStream/1.0 (Android; public anime catalog)")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val raw = http.newCall(request).execute().use { response ->
            val content = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val explanation = runCatching {
                    ((json.parseToJsonElement(content) as JsonObject)["errors"] as? JsonArray)
                        ?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw IllegalStateException(
                    "AniList HTTP ${response.code}" +
                        (explanation?.take(180)?.let { ": $it" } ?: "")
                )
            }
            content
        }
        val response = json.parseToJsonElement(raw) as? JsonObject ?: return emptyList()
        val error = (response["errors"] as? JsonArray)?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        if (error != null) throw IllegalStateException("AniList: ${error.take(180)}")
        val list = response["data"]?.jsonObject?.get("Page")?.jsonObject
            ?.get("media") as? JsonArray ?: return emptyList()
        return list.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val id = obj.int("id") ?: return@mapNotNull null
            val title = obj["title"] as? JsonObject ?: return@mapNotNull null
            val name = title.str("english") ?: title.str("romaji") ?: return@mapNotNull null
            KeylessTitle(
                sourceId = "anilist:$id", title = name, alternateTitle = title.str("romaji"),
                type = "anime",
                year = (obj["startDate"] as? JsonObject)?.int("year"),
                poster = (obj["coverImage"] as? JsonObject)?.str("large"),
                background = obj.str("bannerImage"),
                description = obj.str("description").orEmpty().replace(Regex("<[^>]+>"), ""),
                anilistId = id, episodes = obj.int("episodes"),
                format = obj.str("format"),
                genres = (obj["genres"] as? JsonArray)?.mapNotNull {
                    (it as? JsonPrimitive)?.contentOrNull
                }.orEmpty()
            )
        }.distinctBy(KeylessTitle::sourceId)
    }

    private fun request(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Catálogo HTTP ${response.code}")
            response.body?.string()?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Resposta vazia do catálogo")
        }
    }

    private fun JsonObject.str(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()
}
