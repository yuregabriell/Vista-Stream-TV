package com.ivor.openstream.data.cast

/**
 * HLS hints used before the Cast receiver loads media. Many providers expose
 * signed/extensionless URLs and may reject a second probe request, so URL suffix
 * alone is not a reliable way to select the receiver's media pipeline.
 */
internal object CastHlsSupport {
    const val MIME_TYPE = "application/x-mpegURL"
    const val DASH_MIME_TYPE = "application/dash+xml"
    const val SEGMENT_FMP4 = "fmp4"
    const val SEGMENT_MPEG_TS = "mpeg2_ts"

    fun isHlsMime(value: String?): Boolean {
        val mime = value?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return mime in setOf(
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/mpegurl",
            "audio/mpegurl",
            "audio/x-mpegurl"
        )
    }

    fun isHlsUrl(url: String): Boolean =
        url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)

    fun hasHlsHint(url: String, mimeHint: String?): Boolean =
        isHlsMime(mimeHint) || isHlsUrl(url)

    fun masterVariant(playlist: String): String? {
        val lines = playlist.lineSequence().map { it.trim() }.toList()
        val index = lines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        if (index == -1) return null
        return lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
    }

    /**
     * Only supply Cast's segment format when it is identifiable. Do not guess
     * MPEG-TS for an unavailable fMP4 variant, which breaks receiver startup.
     */
    fun segmentFormat(playlist: String?): String? {
        if (playlist.isNullOrBlank()) return null
        if (playlist.contains("#EXT-X-MAP", ignoreCase = true)) return SEGMENT_FMP4
        val segments = playlist.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('?').substringBefore('#').lowercase() }
            .toList()
        if (segments.any { it.endsWith(".m4s") || it.endsWith(".mp4") || it.endsWith(".cmfv") }) {
            return SEGMENT_FMP4
        }
        if (segments.any { it.endsWith(".ts") || it.endsWith(".mpegts") }) {
            return SEGMENT_MPEG_TS
        }
        return null
    }
}
