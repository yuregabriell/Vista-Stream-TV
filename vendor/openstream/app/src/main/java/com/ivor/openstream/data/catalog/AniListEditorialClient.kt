package com.ivor.openstream.data.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Editorial data only. AniList IDs must NEVER be passed to the player or TMDB details. */
data class AnimeEditorialItem(
    val anilistId: Int,
    val names: List<String>,
    val format: String,
    val genres: Set<String>,
    val year: Int?,
    val trendScore: Int,
    val popularity: Int
) {
    val isMovie: Boolean get() = format == "MOVIE"
}

data class AnimeEditorialFeed(
    val trending: List<AnimeEditorialItem>,
    val popular: List<AnimeEditorialItem>,
    val newReleases: List<AnimeEditorialItem>
)

/**
 * One batched GraphQL request (no covers/backdrops) every three hours.
 * Stores just the small JSON payload and can reuse stale data during outages.
 * Does not modify the authoritative TMDB catalog.
 */
@Singleton
class AniListEditorialClient @Inject constructor(
    @Named("StreamingClient") private val http: OkHttpClient,
    private val json: Json,
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("vs_tv_anilist_editorial_v1", Context.MODE_PRIVATE)
    private val cacheKey = "editorial_graphql"
    private val query = """
        query VistaEditorial {
          trending: Page(page: 1, perPage: 50) {
            media(type: ANIME, isAdult: false, sort: [TRENDING_DESC]) {
              id title { english romaji native } synonyms genres format
              startDate { year } trending popularity
            }
          }
          popular: Page(page: 1, perPage: 50) {
            media(type: ANIME, isAdult: false, sort: [POPULARITY_DESC]) {
              id title { english romaji native } synonyms genres format
              startDate { year } trending popularity
            }
          }
          newReleases: Page(page: 1, perPage: 50) {
            media(type: ANIME, isAdult: false, sort: [START_DATE_DESC]) {
              id title { english romaji native } synonyms genres format
              startDate { year } trending popularity
            }
          }
        }
    """.trimIndent()

    suspend fun get(): AnimeEditorialFeed? = withContext(Dispatchers.IO) {
        val old = prefs.getString(cacheKey, null)
        val stamp = prefs.getLong("editorial_ts", 0L)
        if (old != null && System.currentTimeMillis() - stamp < 3L * 60 * 60 * 1000) {
            parse(old)?.let { return@withContext it }
        }
        val body = """{"query":${json.encodeToString(kotlinx.serialization.serializer<String>(), query)}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Accept", "application/json")
            .header("User-Agent", "VistaStream-TV/1.0")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val fresh = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        } catch (_: Exception) { null }
        val parsed = fresh?.let(::parse)
        if (parsed != null && fresh.length <= 250_000) {
            prefs.edit().putString(cacheKey, fresh)
                .putLong("editorial_ts", System.currentTimeMillis()).apply()
        }
        parsed ?: old?.let(::parse)
    }

    private fun parse(raw: String): AnimeEditorialFeed? = runCatching {
        val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonObject
            ?: return@runCatching null
        fun items(section: String): List<AnimeEditorialItem> {
            val list = data[section]?.jsonObject?.get("media") as? JsonArray ?: return emptyList()
            return list.mapNotNull { element ->
                val row = element as? JsonObject ?: return@mapNotNull null
                val id = (row["id"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
                val titles = row["title"] as? JsonObject
                val names = buildList {
                    listOf("english", "romaji", "native").forEach { field ->
                        (titles?.get(field) as? JsonPrimitive)?.contentOrNull
                            ?.takeIf(String::isNotBlank)?.let(::add)
                    }
                    (row["synonyms"] as? JsonArray)?.take(8)?.forEach { name ->
                        (name as? JsonPrimitive)?.contentOrNull
                            ?.takeIf(String::isNotBlank)?.let(::add)
                    }
                }.distinct()
                if (names.isEmpty()) return@mapNotNull null
                AnimeEditorialItem(
                    anilistId = id,
                    names = names,
                    format = (row["format"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    genres = (row["genres"] as? JsonArray)?.mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    }?.toSet().orEmpty(),
                    year = (row["startDate"] as? JsonObject)?.get("year")
                        ?.let { it as? JsonPrimitive }?.intOrNull,
                    trendScore = (row["trending"] as? JsonPrimitive)?.intOrNull ?: 0,
                    popularity = (row["popularity"] as? JsonPrimitive)?.intOrNull ?: 0
                )
            }
        }
        AnimeEditorialFeed(
            trending = items("trending"),
            popular = items("popular"),
            newReleases = items("newReleases")
        )
    }.getOrNull()
}
