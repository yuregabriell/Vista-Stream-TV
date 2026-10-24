package com.ivor.openstream.harvester

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.catalog.VistaFullscreenPlayer
import com.ivor.openstream.ui.theme.OpenStreamTheme

/**
 * Harvester's official external Android player.
 * This Activity stays separate from the Vista home scaffold and bottom toolbar.
 * It opens as a result of explicit user intent and never scrapes web pages.
 */
class HarvesterPlaybackActivity : ComponentActivity() {
    private var current by mutableStateOf<HarvesterMedia?>(null)
    private lateinit var library: HarvesterLibraryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        library = HarvesterLibraryStore(this)
        current = HarvesterContract.parse(intent)
        if (current == null) {
            Toast.makeText(this, "Envia um link direto de vídeo válido pelo Harvester.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        current?.let(library::save)
        enableEdgeToEdge()
        setContent {
            val item = current
            OpenStreamTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (item != null) {
                        val savedPosition = library.all().firstOrNull { it.url == item.url }
                            ?.takeIf { it.resumable }?.positionMs ?: 0L
                        val server = VideoServer(
                            id = "harvester:" + item.url.hashCode().toString(),
                            providerId = "harvester-browser",
                            providerName = "Harvester Browser",
                            name = item.title,
                            url = item.url,
                            quality = StreamQuality.UNKNOWN,
                            mimeType = item.mimeType,
                            headers = buildMap {
                                item.referer?.let { put("Referer", it) }
                                item.userAgent?.let { put("User-Agent", it) }
                            },
                            subtitles = listOfNotNull(item.subtitleUrl?.let {
                                StreamSubtitle(url = it, label = "Legendas", language = item.subtitleLanguage)
                            })
                        )
                        VistaFullscreenPlayer(
                            server = server,
                            title = item.title,
                            episodeLabel = "Harvester Browser",
                            resumePositionMs = savedPosition,
                            onProgress = { position, duration -> library.progress(item.url, position, duration) },
                            availableServers = listOf(server),
                            onSelectSource = {},
                            hasAlternative = false,
                            onTryAlternative = {},
                            onClose = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val video = HarvesterContract.parse(intent)
        if (video == null) {
            Toast.makeText(this, "Link de vídeo inválido.", Toast.LENGTH_SHORT).show()
        } else {
            library.save(video)
            current = video
        }
    }
}
