package com.ivor.openstream.presentation.catalog

import androidx.media3.common.MimeTypes
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * A stream may look like an MP4 from its URL while actually serving HLS/DASH.
 * Check only standard HTTP Content-Type before Media3 chooses an extractor.
 * HEAD failure is not an error: many legitimate media hosts support GET only.
 */
internal object VistaStreamMime {
    private val probe = OkHttpClient.Builder()
        .callTimeout(4L, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fromHeaders(server: VideoServer): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(server.url)
                .head()
                .apply {
                    server.headers.forEach { (name, value) ->
                        if (name.isNotBlank()) header(name, value)
                    }
                }.build()
            probe.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val type = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
                when (type) {
                    "application/vnd.apple.mpegurl", "application/x-mpegurl",
                    "application/mpegurl", "audio/mpegurl" -> MimeTypes.APPLICATION_M3U8
                    "application/dash+xml" -> MimeTypes.APPLICATION_MPD
                    "video/mp4", "application/mp4" -> MimeTypes.VIDEO_MP4
                    "video/webm" -> MimeTypes.VIDEO_WEBM
                    else -> null
                }
            }
        }.getOrNull()
    }
}
