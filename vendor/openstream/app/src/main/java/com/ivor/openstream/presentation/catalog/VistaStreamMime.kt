package com.ivor.openstream.presentation.catalog

import androidx.media3.common.MimeTypes
import com.ivor.openstream.data.playback.VistaPlaybackEngine
import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class VistaMediaProbe(
    val mime: String? = null,
    val unsupportedReason: String? = null,
    val diagnostic: String? = null
)

/**
 * Small-range HTTP preflight. Never downloads a complete film; no cookies,
 * signed URLs or headers are persisted in diagnostic messages.
 * Network timeout is inconclusive, so Media3 gets a chance to try the source.
 */
internal object VistaStreamMime {
    private val probe = OkHttpClient.Builder()
        .connectTimeout(4L, TimeUnit.SECONDS)
        .readTimeout(5L, TimeUnit.SECONDS)
        .callTimeout(7L, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val segmentProbe = probe.newBuilder()
        .callTimeout(3L, TimeUnit.SECONDS)
        .readTimeout(3L, TimeUnit.SECONDS)
        .build()

    suspend fun inspect(server: VideoServer): VistaMediaProbe = withContext(Dispatchers.IO) {
        if (!server.url.startsWith("https://", true) &&
            !server.url.startsWith("http://", true)
        ) return@withContext VistaMediaProbe()
        runCatching {
            val request = Request.Builder().url(server.url)
                .get()
                .header("Range", "bytes=0-4095")
                .header("User-Agent", BROWSER_USER_AGENT)
                .apply {
                    server.headers.forEach { (name, value) ->
                        if (name.isNotBlank() &&
                            !name.equals("Range", true) &&
                            !name.equals("Host", true)
                        ) header(name, value)
                    }
                }.build()
            probe.newCall(request).execute().use { response ->
                val sample = if (response.isSuccessful) response.peekBody(4096L).bytes()
                    else ByteArray(0)
                val report = VistaPlaybackEngine.diagnose(
                    server.url, server.mimeType, response.code,
                    response.header("Content-Type"), sample
                )
                val segmentFailure = if (report.playableCandidate &&
                    report.format == VistaPlaybackEngine.Format.HLS) {
                    checkFirstHlsSegment(response.request.url,
                        sample.toString(Charsets.UTF_8), server.headers)
                } else null
                val detectedMime = when (report.format) {
                    VistaPlaybackEngine.Format.HLS -> MimeTypes.APPLICATION_M3U8
                    VistaPlaybackEngine.Format.DASH -> MimeTypes.APPLICATION_MPD
                    VistaPlaybackEngine.Format.MP4 -> MimeTypes.VIDEO_MP4
                    VistaPlaybackEngine.Format.MATROSKA -> MimeTypes.VIDEO_MATROSKA
                    VistaPlaybackEngine.Format.WEBM -> MimeTypes.VIDEO_WEBM
                    else -> null
                }
                VistaMediaProbe(
                    mime = detectedMime,
                    unsupportedReason = if (!report.playableCandidate) report.explanation else segmentFailure,
                    diagnostic = segmentFailure ?: report.explanation
                )
            }
        }.getOrElse {
            VistaMediaProbe(diagnostic = "Não foi possível verificar a origem; o player tentará reproduzir.")
        }
    }

    /**
     * A playlist can answer 200 while its segments have expired. Check just one
     * media segment when it stays on the playlist's host, without downloading video.
     * Cross-host checks are skipped rather than leaking Authorization/Cookie tokens.
     * Timeouts remain inconclusive and Media3 will attempt playback normally.
     */
    private fun checkFirstHlsSegment(
        base: okhttp3.HttpUrl, playlist: String, headers: Map<String, String>
    ): String? {
        val entry = VistaPlaybackEngine.firstHlsSegment(playlist) ?: return null
        val resolved = base.resolve(entry) ?: return null
        if (resolved.host != base.host || resolved.scheme != base.scheme) return null
        return runCatching {
            val request = Request.Builder().url(resolved)
                .get()
                .header("Range", "bytes=0-1")
                .header("User-Agent", BROWSER_USER_AGENT)
                .apply {
                    headers.forEach { (name, value) ->
                        if (name.isNotBlank() && !name.equals("Range", true) &&
                            !name.equals("Host", true)) header(name, value)
                    }
                }.build()
            segmentProbe.newCall(request).execute().use { response ->
                if (response.code in setOf(401, 403, 404, 410))
                    "O primeiro segmento HLS está indisponível (HTTP ${response.code})."
                else null
            }
        }.getOrNull()
    }
}
