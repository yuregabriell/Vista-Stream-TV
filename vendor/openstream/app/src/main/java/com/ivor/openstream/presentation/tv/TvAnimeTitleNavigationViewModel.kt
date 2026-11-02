package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared mapping used by both hero artwork and TV title navigation.
 * AniList catalogue IDs must never be used as TMDB episode/source IDs.
 */
data class TvAnimeCatalogMatch(
    val mediaType: String,
    val tmdbId: Int,
    val backdropPath: String?
)

/** Removes sequel/season suffixes before comparing against TMDB's base TV series. */
internal object TvAnimeNames {
    private val seasonSuffix = Regex(
        """(?i)(?:[\s:–\-]+)(?:season\s*\d+|\d+(?:st|nd|rd|th)\s+(?:season|stage)|part\s*\d+|cour\s*\d+|final\s+season)\s*$"""
    )
    private val subtitleSeparator = Regex("""\s+[-–—]\s+|:\s+""")
    fun base(name: String): String = name.trim().replace(seasonSuffix, "").trim()
    /**
     * AniList often catalogs arcs/seasons individually, where TMDB catalogs the
     * parent show. Try the *longest* title first, then suffix-free variants.
     * No fuzzy candidate is accepted; each must match a whole exact variant.
     */
    fun searchNames(title: String): List<String> {
        val original = title.trim()
        val variants = linkedSetOf(original, base(original))
        for (value in variants.toList()) {
            val separators = subtitleSeparator.findAll(value).toList()
            for (separator in separators.asReversed()) {
                val prefix = value.substring(0, separator.range.first).trim()
                if (prefix.length >= 6) {
                    variants.add(prefix)
                    variants.add(base(prefix))
                }
            }
        }
        return variants.filter { it.isNotBlank() }.distinctBy(::key)
    }
    fun key(name: String): String =
        name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
}

@Singleton
class TvAnimeCatalogMatcher @Inject constructor(
    private val tmdb: TmdbApi
) {
    private val matches = mutableMapOf<String, TvAnimeCatalogMatch>()

    suspend fun resolve(title: KeylessTitle): TvAnimeCatalogMatch? = withContext(Dispatchers.IO) {
        val kind = if (title.format.equals("MOVIE", ignoreCase = true)) "movie" else "tv"
        synchronized(matches) { matches[title.sourceId] }?.let { return@withContext it }
        if (title.tmdbId != null && title.tmdbId > 0) {
            return@withContext TvAnimeCatalogMatch(kind, title.tmdbId, null)
        }

        val aliases = listOfNotNull(title.title, title.alternateTitle)
            .map { it.trim() }.filter { it.isNotBlank() }
        val names = aliases.flatMap(TvAnimeNames::searchNames)
            .filter { it.isNotBlank() }
            .distinctBy(TvAnimeNames::key)
        val expected = names.map(TvAnimeNames::key).toSet()
        // A sequel with a year of 2026 may belong to a TMDB show launched in
        // 2022/2024. Only bypass the year check after a strict base-title match.
        val isSeason = aliases.any { TvAnimeNames.base(it) != it.trim() }
        val originalKeys = aliases.map(TvAnimeNames::key).toSet()
        for (name in names) {
            val results: List<AnimeDto> = try {
                if (kind == "movie") tmdb.searchMovieForArtwork(name).results
                else tmdb.searchTvForArtwork(name).results
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep trying alternate title and unsuffixed original name.
                continue
            }
            val candidate = results.firstOrNull { item ->
                val year = item.date.take(4).toIntOrNull()
                val candidateKey = TvAnimeNames.key(item.name)
                val exactOrSeason = candidateKey in originalKeys || isSeason
                // Never map a distant spin-off to a short franchise prefix
                // solely because it contains the same word (wrong episodes).
                val meaningfulAlias = candidateKey.length >= 6 &&
                    candidateKey in expected &&
                    (candidateKey in originalKeys || candidateKey.length >=
                        (names.firstOrNull()?.let(TvAnimeNames::key)?.length ?: 0) * 0.45)
                item.id > 0 && meaningfulAlias &&
                    (exactOrSeason || title.year == null || year == null ||
                        kotlin.math.abs(year - title.year) <= 2)
            }
            if (candidate != null) {
                val match = TvAnimeCatalogMatch(kind, candidate.id, candidate.backdropPath)
                synchronized(matches) {
                    if (matches.size >= 150) matches.clear()
                    matches[title.sourceId] = match
                }
                return@withContext match
            }
        }
        null // Don't navigate to a different show based only on fuzzy similarity.
    }
}

@HiltViewModel
class TvAnimeTitleNavigationViewModel @Inject constructor(
    private val matcher: TvAnimeCatalogMatcher
) : ViewModel() {
    suspend fun resolve(title: KeylessTitle): Pair<String, Int>? =
        matcher.resolve(title)?.let { it.mediaType to it.tmdbId }
}
