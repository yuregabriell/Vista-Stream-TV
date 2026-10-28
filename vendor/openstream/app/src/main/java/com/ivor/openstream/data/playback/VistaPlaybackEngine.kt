package com.ivor.openstream.data.playback

/**
 * Bounded, side-effect-free source diagnostics for the phone player and Cast preflight.
 * An HTTP response isn't proof of playable video: it can be a login page, JSON error
 * or an expired signed link. Never retain or log the URL, cookies or request headers.
 */
internal object VistaPlaybackEngine {
    enum class Format { HLS, DASH, MP4, MATROSKA, WEBM, MPEG_TS, UNKNOWN }
    enum class Status { READY, UNVERIFIED, UNAVAILABLE, INVALID_RESPONSE }

    data class Report(
        val format: Format,
        val status: Status,
        val httpCode: Int?,
        val explanation: String
    ) {
        val playableCandidate: Boolean get() =
            status == Status.READY || status == Status.UNVERIFIED
    }

    fun diagnose(
        url: String,
        mimeHint: String?,
        httpCode: Int?,
        contentType: String?,
        sample: ByteArray
    ): Report {
        val text = sample.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        val type = contentType.orEmpty().substringBefore(';').trim().lowercase()
        val hint = mimeHint.orEmpty().substringBefore(';').trim().lowercase()
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val sniffed = when {
            text.startsWith("#EXTM3U") -> Format.HLS
            text.startsWith("<MPD", true) || text.contains("<MPD ", true) -> Format.DASH
            sample.size >= 8 && String(sample, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> Format.MP4
            sample.size >= 4 && sample[0] == 0x1A.toByte() &&
                sample[1] == 0x45.toByte() && sample[2] == 0xDF.toByte() &&
                sample[3] == 0xA3.toByte() -> Format.MATROSKA
            sample.size >= 1 && sample[0] == 0x47.toByte() -> Format.MPEG_TS
            else -> Format.UNKNOWN
        }
        val format = when {
            sniffed != Format.UNKNOWN -> sniffed
            type in HLS_TYPES || hint in HLS_TYPES || path.endsWith(".m3u8") -> Format.HLS
            type == "application/dash+xml" || hint == "application/dash+xml" || path.endsWith(".mpd") -> Format.DASH
            type == "video/mp4" || path.endsWith(".mp4") -> Format.MP4
            type == "video/x-matroska" || path.endsWith(".mkv") -> Format.MATROSKA
            type == "video/webm" || path.endsWith(".webm") -> Format.WEBM
            else -> Format.UNKNOWN
        }
        if (httpCode != null && httpCode !in 200..299) {
            val explanation = when (httpCode) {
                401, 403 -> "Fonte recusou o acesso (HTTP $httpCode); ligação ou autorização pode ter expirado."
                404, 410 -> "Vídeo não encontrado na origem (HTTP $httpCode)."
                429 -> "Origem está a limitar pedidos (HTTP 429)."
                in 500..599 -> "Servidor da fonte indisponível (HTTP $httpCode)."
                else -> "Origem respondeu HTTP $httpCode."
            }
            return Report(format, Status.UNAVAILABLE, httpCode, explanation)
        }
        if (type.contains("text/html") || text.startsWith("<!doctype html", true) ||
            text.startsWith("<html", true) || type.contains("application/json") ||
            (text.startsWith("{") && format == Format.UNKNOWN)
        ) return Report(format, Status.INVALID_RESPONSE, httpCode,
            "A fonte devolveu uma página ou resposta JSON, não um vídeo direto.")
        if (sniffed == Format.HLS && !text.contains("#EXT-X-", true) &&
            !text.contains("#EXTINF:", true)
        ) return Report(Format.HLS, Status.UNVERIFIED, httpCode,
            "Playlist HLS identificada; conteúdo ainda não validado.")
        if (sample.isEmpty()) return Report(format, Status.UNVERIFIED, httpCode,
            "A fonte ainda não forneceu bytes para verificar.")
        return Report(format, if (format == Format.UNKNOWN) Status.UNVERIFIED else Status.READY,
            httpCode, if (format == Format.UNKNOWN) "Formato não identificado; o player fará a deteção."
            else "Formato ${format.name} identificado.")
    }

    /** First media segment in a media playlist; master playlists are intentionally not guessed. */
    fun firstHlsSegment(playlist: String): String? {
        if (!playlist.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U")) return null
        val lines = playlist.lineSequence().map { it.trim() }.toList()
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF", true) }) return null
        val index = lines.indexOfFirst { it.startsWith("#EXTINF:", true) }
        if (index < 0) return null
        return lines.drop(index + 1).firstOrNull {
            it.isNotBlank() && !it.startsWith("#")
        }
    }

    private val HLS_TYPES = setOf(
        "application/x-mpegurl", "application/vnd.apple.mpegurl",
        "application/mpegurl", "audio/mpegurl", "audio/x-mpegurl"
    )
}
