package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.domain.model.MediaIdentity
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** BitTorrent v1 info hashes can be written as 40 hex or 32 RFC4648 Base32 characters. */
internal fun stremioBtih(raw: String?): String? {
    val value = raw?.trim() ?: return null
    if (value.matches(Regex("[0-9a-fA-F]{40}"))) return value.lowercase(Locale.ROOT)
    if (!value.matches(Regex("[A-Za-z2-7]{32}"))) return null
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val bytes = ByteArray(20)
    var buffer = 0
    var bits = 0
    var offset = 0
    for (symbol in value.uppercase(Locale.ROOT)) {
        val digit = alphabet.indexOf(symbol)
        if (digit < 0) return null
        buffer = (buffer shl 5) or digit
        bits += 5
        if (bits >= 8) {
            bits -= 8
            bytes[offset++] = ((buffer ushr bits) and 0xff).toByte()
            buffer = buffer and ((1 shl bits) - 1)
        }
    }
    if (offset != 20 || bits != 0) return null
    val digits = "0123456789abcdef"
    return buildString(40) {
        for (byte in bytes) {
            val number = byte.toInt() and 0xff
            append(digits[number ushr 4])
            append(digits[number and 15])
        }
    }
}

/** Parse only the xt parameter; never guess the info hash from a display name. */
internal fun stremioBtihFromMagnet(raw: String?): String? {
    if (raw?.startsWith("magnet:?", ignoreCase = true) != true) return null
    for (part in raw.substringAfter('?').split('&')) {
        val equalsAt = part.indexOf('=')
        if (equalsAt < 1 || !part.substring(0, equalsAt).equals("xt", true)) continue
        val value = runCatching { URLDecoder.decode(part.substring(equalsAt + 1), "UTF-8") }.getOrNull()
            ?: continue
        if (!value.startsWith("urn:btih:", true)) continue
        stremioBtih(value.substring(9))?.let { return it }
    }
    return null
}

/** Ignore invalid/mis-encoded tracker data instead of sending it to a P2P engine. */
internal fun stremioTrackerUrls(sources: List<String>, magnet: String? = null): List<String> {
    val proposed = mutableListOf<String>()
    sources.forEach { source ->
        if (source.startsWith("tracker:", ignoreCase = true)) proposed += source.substring(8)
    }
    if (magnet?.startsWith("magnet:?", ignoreCase = true) == true) {
        for (part in magnet.substringAfter('?').split('&')) {
            val equalsAt = part.indexOf('=')
            if (equalsAt < 1 || !part.substring(0, equalsAt).equals("tr", true)) continue
            runCatching { URLDecoder.decode(part.substring(equalsAt + 1), "UTF-8") }
                .getOrNull()?.let(proposed::add)
        }
    }
    return proposed.mapNotNull { item ->
        val candidate = item.trim()
        if (candidate.length !in 8..2048) return@mapNotNull null
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return@mapNotNull null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("udp", "http", "https") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawFragment != null ||
            uri.port > 65535 || (scheme == "udp" && uri.port !in 1..65535)) return@mapNotNull null
        candidate
    }.distinct().take(30)
}

internal fun stremioMagnet(hashRaw: String?, trackers: List<String>): String? {
    val hash = stremioBtih(hashRaw) ?: return null
    return buildString {
        append("magnet:?xt=urn:btih:").append(hash)
        for (tracker in stremioTrackerUrls(trackers.map { "tracker:" + it })) {
            append("&tr=").append(URLEncoder.encode(tracker, "UTF-8"))
        }
    }
}

/** Exact existing catalog identities; no title-only fallback that can misroute episodes. */
internal fun stremioCandidateIds(identity: MediaIdentity, idPrefixes: List<String>?): List<String> {
    val isMovie = identity.tmdbType == "movie"
    if (!isMovie && (identity.season < 0 || identity.episode <= 0)) return emptyList()
    val suffix = if (isMovie) "" else ":" + identity.season + ":" + identity.episode
    val imdb = identity.imdbId?.takeIf { it.matches(Regex("tt[0-9]{5,}")) }
    val supportsImdb = idPrefixes == null || idPrefixes.any { it.startsWith("tt") || it == "imdb" }
    val supportsTmdb = idPrefixes?.any { it.startsWith("tmdb") } == true
    return buildList {
        if (imdb != null && supportsImdb) add(imdb + suffix)
        if (supportsTmdb && identity.tmdbId > 0) add("tmdb:" + identity.tmdbId + suffix)
    }
}
