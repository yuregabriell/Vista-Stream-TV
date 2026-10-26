package com.ivor.openstream.harvester

import com.ivor.openstream.data.catalog.KeylessTitle
import java.net.URI
import java.net.URLDecoder
import java.text.Normalizer
import kotlin.math.abs

/**
 * Local, explainable title matching. The source URL is read as untrusted TEXT:
 * no network request is made to it, no query token is persisted as a search hint.
 */
internal object ImportSmartMatcher {
    data class Hints(
        val title: String,
        val queries: List<String>,
        val year: Int?,
        val fromPage: Boolean
    )

    data class Ranked(val item: KeylessTitle, val score: Int, val reason: String)

    private val yearPattern = Regex("""(?<!\d)(?:19\d{2}|20\d{2})(?!\d)""")
    private val junk = Regex(
        """(?i)\b(?:dublado|legendado|dual[\s._-]*audio|multi[\s._-]*audio|dub|sub|pt[\s._-]*br|pt[\s._-]*pt|1080p?|720p?|2160p?|480p?|4k|full[\s._-]*hd|hdtv|bluray|web[\s._-]*dl|webrip|brrip|hdrip|assistir|online|gratis|completo|filme|movie|serie|series|anime|temporada)\b"""
    )
    private val hashToken = Regex("""(?i)\b(?=[a-f0-9]*[a-f])(?=[a-f0-9]*\d)[a-f0-9]{8,}\b""")
    private val episode = Regex("""(?i)\b(?:s\d{1,2}e\d{1,3}|\d{1,2}x\d{1,3}|ep(?:isodio)?[\s._-]*\d{1,3})\b""")

    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase()
        .replace(Regex("""[^a-z0-9]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

    private fun clean(value: String): String = value
        .replace(Regex("""[._\-+]+"""), " ")
        .let { episode.replace(it, " ") }
        .let { hashToken.replace(it, " ") }
        .let { junk.replace(it, " ") }
        .let { yearPattern.replace(it, " ") }
        .replace(Regex("""(?i)\b(?:x264|x265|h264|hevc|aac|mkv|mp4|m3u8|part\s*\d+)\b"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '_', '.')
        .take(100)

    fun inspect(rawTitle: String, pageUrl: String, yearText: String = ""): Hints {
        val pathname = runCatching {
            val uri = URI(pageUrl.trim().take(2048))
            if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) "" else
                URLDecoder.decode(uri.rawPath.orEmpty().substringAfterLast('/'), "UTF-8")
                    .substringBeforeLast('.', missingDelimiterValue = uri.rawPath.orEmpty().substringAfterLast('/'))
        }.getOrDefault("")
        val page = clean(pathname)
        val raw = clean(rawTitle)
        val validPage = page.length >= 5 && page.split(' ').size >= 2 &&
            fold(page) !in setOf("player", "video", "watch", "embed", "server php", "index php")
        val validRaw = raw.length >= 3 && fold(raw) !in setOf("video encontrado", "video", "player")
        val primary = when {
            validPage && (!validRaw || fold(page).contains(fold(raw)) || fold(raw).contains(fold(page))) -> page
            validRaw -> raw
            validPage -> page
            else -> raw.ifBlank { "Vídeo encontrado" }
        }
        val year = yearText.toIntOrNull()?.takeIf { it in 1900..2100 }
            ?: yearPattern.find(pathname)?.value?.toIntOrNull()
            ?: yearPattern.find(rawTitle)?.value?.toIntOrNull()
        val queries = listOf(primary, raw.takeIf { validRaw }, page.takeIf { validPage })
            .filterNotNull().map(::clean).filter { it.length >= 3 }
            .distinctBy(::fold).take(3)
        return Hints(primary, queries, year, validPage)
    }

    private fun lexicalScore(query: String, candidate: String): Double {
        val q = fold(query).split(' ').filter { it.isNotBlank() }.toSet()
        val c = fold(candidate).split(' ').filter { it.isNotBlank() }.toSet()
        if (q.isEmpty() || c.isEmpty()) return 0.0
        if (q == c) return 1.0
        val shared = q.intersect(c).size.toDouble()
        if (shared == 0.0) return 0.0
        return 0.67 * shared / q.size + 0.33 * shared / c.size
    }

    fun rank(items: List<KeylessTitle>, hints: Hints, wanted: Set<String>): List<Ranked> =
        items.asSequence().filter { it.type in wanted }.map { item ->
            val lexical = hints.queries.maxOfOrNull { query ->
                maxOf(lexicalScore(query, item.title),
                    item.alternateTitle?.let { lexicalScore(query, it) } ?: 0.0)
            } ?: 0.0
            val yearBonus = when {
                hints.year == null || item.year == null -> 0.0
                hints.year == item.year -> 0.15
                abs(hints.year - item.year) == 1 -> -0.12
                else -> -0.45
            }
            val score = ((lexical * 0.83 + yearBonus) * 100).toInt().coerceIn(0, 99)
            val reason = when {
                lexical >= 0.99 && hints.year == item.year -> "Nome e ano correspondem"
                lexical >= 0.99 -> "Nome correspondente"
                hints.year != null && hints.year == item.year -> "Ano correspondente; confirmar nome"
                else -> "Correspondência parcial; confirmar"
            }
            Ranked(item, score, reason)
        }.filter { it.score >= 23 }
            .distinctBy { it.item.tmdbId?.let { id -> "tmdb:$id" }
                ?: it.item.imdbId?.let { id -> "imdb:$id" } ?: it.item.sourceId }
            .sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.item.title })
            .take(16).toList()
}
