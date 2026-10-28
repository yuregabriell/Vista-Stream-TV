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
                    unsupportedReason = if (!report.playableCandidate) report.explanation else null,
                    diagnostic = report.explanation
                )
            }
        }.getOrElse {
            VistaMediaProbe(diagnostic = "Não foi possível verificar a origem; o player tentará reproduzir.")
        }
    }
}
