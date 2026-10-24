package com.ivor.openstream.harvester

import android.content.Intent
import android.net.Uri
import kotlinx.serialization.Serializable

/**
 * Public contract for Harvester Browser and Android's video Share/Open flows.
 * The app receives URLs; it does not scrape web pages or bypass DRM.
 */
object HarvesterContract {
    const val ACTION_PLAY = "com.vistastream.tv.action.PLAY"
    const val EXTRA_URL = "com.vistastream.tv.extra.URL"
    const val EXTRA_TITLE = "com.vistastream.tv.extra.TITLE"
    const val EXTRA_REFERER = "com.vistastream.tv.extra.REFERER"
    const val EXTRA_USER_AGENT = "com.vistastream.tv.extra.USER_AGENT"
    const val EXTRA_MIME_TYPE = "com.vistastream.tv.extra.MIME_TYPE"
    const val EXTRA_SUBTITLE_URL = "com.vistastream.tv.extra.SUBTITLE_URL"
    const val EXTRA_SUBTITLE_LANGUAGE = "com.vistastream.tv.extra.SUBTITLE_LANGUAGE"
    const val EXTRA_PAGE_URL = "com.vistastream.tv.extra.PAGE_URL"
    private val videoExtensions = Regex("""(?i)\.(mp4|m4v|mkv|webm|m3u8|mpd|mov|ts|m2ts|flv|avi|3gp)(?:[?#].*)?$""")

    @Suppress("DEPRECATION")
    fun parse(intent: Intent?): HarvesterMedia? {
        intent ?: return null
        val action = intent.action ?: return null
        if (action !in setOf(ACTION_PLAY, Intent.ACTION_VIEW, Intent.ACTION_SEND)) return null
        val raw = when (action) {
            ACTION_PLAY -> intent.getStringExtra(EXTRA_URL) ?: intent.dataString
            Intent.ACTION_VIEW -> intent.dataString
            else -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        }?.trim()?.takeIf { it.length in 7..8192 } ?: return null
        // A text share containing an arbitrary web page must not be sent straight
        // to the decoder: browser detection must first extract a real stream.
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in listOf("http", "https", "content")) return null
        if (scheme != "content" && uri.host.isNullOrBlank()) return null
        val incomingMime = (intent.getStringExtra(EXTRA_MIME_TYPE) ?: intent.type)
            ?.take(120)?.takeIf { it.matches(Regex("(?i)[a-z0-9.+-]+/[a-z0-9.+-]+")) }
        val definiteVideo = incomingMime?.startsWith("video/", true) == true ||
            incomingMime in setOf("application/vnd.apple.mpegurl", "application/dash+xml",
                "application/x-mpegurl", "application/x-mpegURL")
        if (action != ACTION_PLAY && !definiteVideo && !videoExtensions.containsMatchIn(raw)) {
            return null
        }
        fun httpExtra(key: String): String? = intent.getStringExtra(key)?.take(2048)
            ?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
        val title = (intent.getStringExtra(EXTRA_TITLE) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT))
            ?.trim()?.take(180)?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringBefore('?')?.take(80)?.takeIf { it.isNotBlank() }
            ?: "Vídeo do navegador"
        return HarvesterMedia(
            url = raw, title = title, mimeType = incomingMime,
            referer = httpExtra(EXTRA_REFERER),
            userAgent = intent.getStringExtra(EXTRA_USER_AGENT)?.take(350),
            pageUrl = httpExtra(EXTRA_PAGE_URL),
            subtitleUrl = httpExtra(EXTRA_SUBTITLE_URL),
            subtitleLanguage = intent.getStringExtra(EXTRA_SUBTITLE_LANGUAGE)
                ?.take(12)?.takeIf { it.matches(Regex("[a-zA-Z-]{2,12}")) } ?: "pt-BR"
        )
    }

    fun directPlay(media: HarvesterMedia, targetPackage: String): Intent =
        Intent(ACTION_PLAY).setClassName(
            targetPackage, "com.ivor.openstream.harvester.HarvesterPlaybackActivity"
        ).putExtra(EXTRA_URL, media.url)
            .putExtra(EXTRA_TITLE, media.title)
            .putExtra(EXTRA_MIME_TYPE, media.mimeType)
            .putExtra(EXTRA_REFERER, media.referer)
            .putExtra(EXTRA_USER_AGENT, media.userAgent)
            .putExtra(EXTRA_PAGE_URL, media.pageUrl)
            .putExtra(EXTRA_SUBTITLE_URL, media.subtitleUrl)
            .putExtra(EXTRA_SUBTITLE_LANGUAGE, media.subtitleLanguage)
}

@Serializable
data class HarvesterMedia(
    val url: String,
    val title: String,
    val mimeType: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val pageUrl: String? = null,
    val subtitleUrl: String? = null,
    val subtitleLanguage: String = "pt-BR",
    val savedAt: Long = System.currentTimeMillis(),
    val positionMs: Long = 0,
    val durationMs: Long = 0
) {
    val resumable: Boolean get() =
        positionMs > 15_000 && (durationMs <= 0 || positionMs < durationMs - 45_000)
}
