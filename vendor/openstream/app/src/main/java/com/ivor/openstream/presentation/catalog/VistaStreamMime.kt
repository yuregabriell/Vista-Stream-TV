package com.ivor.openstream.presentation.catalog

import androidx.media3.common.MimeTypes
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class VistaMediaProbe(
    val mime: String? = null,
    val unsupportedReason: String? = null
)

/**
 * Probes only the first 2048 bytes of an HTTP source. Media3 already supports
 * progressive MP4/MKV/WebM/TS/FLV and adaptive HLS/DASH, but a misleading
 * URL suffix or Content-Type may select the wrong media-source extractor.
 *
 * The probe is limited, closed immediately and never downloads the whole file.
 * It does not decrypt, bypass DRM or resolve HTML embeds.
 */
internal object VistaStreamMime {
    private val probe = OkHttpClient.Builder()
        .connectTimeout(3L, TimeUnit.SECONDS)
        .readTimeout(4L, TimeUnit.SECONDS)
        .callTimeout(5L, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun inspect(server: VideoServer): VistaMediaProbe = withContext(Dispatchers.IO) {
        if (!server.url.startsWith("https://", true) &&
            !server.url.startsWith("http://", true)
        ) return@withContext VistaMediaProbe()
        runCatching {
            val request = Request.Builder().url(server.url)
                .get().header("Range", "bytes=0-2047")
                .apply {
                    server.headers.forEach { (name, value) ->
                        if (name.isNotBlank() &&
                            !name.equals("Range", true) &&
                            !name.equals("Host", true)
                        ) header(name, value)
                    }
                }.build()
            probe.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use VistaMediaProbe()
                val bytes = response.peekBody(2048L).bytes()
                val sample = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF',' ', '\n', '\r','\t')
                val contentType = response.header("Content-Type")
                    .orEmpty().substringBefore(';').trim().lowercase()
                val isHtml = contentType.contains("text/html") ||
                    sample.startsWith("<!doctype html", true) ||
                    sample.startsWith("<html", true)
                if (isHtml) {
                    return@use VistaMediaProbe(
                        unsupportedReason = "Esta fonte devolveu uma página HTML, não um vídeo direto. Escolhe outro servidor."
                    )
                }
                val text = sample.take(1600)
                val mime = when {
                    text.startsWith("#EXTM3U") -> MimeTypes.APPLICATION_M3U8
                    text.contains("<MPD", true) || text.contains("<mpd:", true) ->
                        MimeTypes.APPLICATION_MPD
                    text.contains("<SmoothStreamingMedia", true) ->
                        MimeTypes.APPLICATION_SS
                    bytes.size >= 8 && String(bytes, 4, 4, Charsets.ISO_8859_1) == "ftyp" ->
                        MimeTypes.VIDEO_MP4
                    bytes.size >= 4 && bytes[0] == 0x1A.toByte() &&
                        bytes[1] == 0x45.toByte() && bytes[2] == 0xDF.toByte() &&
                        bytes[3] == 0xA3.toByte() -> MimeTypes.VIDEO_MATROSKA
                    bytes.size >= 3 && bytes[0] == 'F'.code.toByte() &&
                        bytes[1] == 'L'.code.toByte() && bytes[2] == 'V'.code.toByte() ->
                        MimeTypes.VIDEO_FLV
                    contentType in listOf("application/vnd.apple.mpegurl",
                        "application/x-mpegurl", "application/mpegurl", "audio/mpegurl") ->
                        MimeTypes.APPLICATION_M3U8
                    contentType == "application/dash+xml" -> MimeTypes.APPLICATION_MPD
                    contentType in listOf("application/vnd.ms-sstr+xml",
                        "application/vnd.ms-sstr+xml; charset=utf-8") -> MimeTypes.APPLICATION_SS
                    contentType == "video/webm" -> MimeTypes.VIDEO_WEBM
                    contentType == "video/x-matroska" -> MimeTypes.VIDEO_MATROSKA
                    contentType == "video/mp4" || contentType == "application/mp4" ->
                        MimeTypes.VIDEO_MP4
                    else -> null // Leave unknown progressive streams to Media3 sniffing.
                }
                VistaMediaProbe(mime = mime)
            }
        }.getOrDefault(VistaMediaProbe())
    }
}
