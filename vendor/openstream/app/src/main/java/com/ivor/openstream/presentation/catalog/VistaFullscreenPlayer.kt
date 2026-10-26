package com.ivor.openstream.presentation.catalog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.delay
import java.util.Locale

private fun Context.findPlayerActivity(): Activity? {
    var value: Context? = this
    while (value != null) {
        if (value is Activity) return value
        value = (value as? ContextWrapper)?.baseContext
    }
    return null
}

/** Direct HTTP streaming, playback progress and track selection; no full-video download. */
@Composable
fun VistaFullscreenPlayer(
    server: VideoServer,
    title: String,
    episodeLabel: String,
    resumePositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    availableServers: List<VideoServer>,
    onSelectSource: (VideoServer) -> Unit,
    hasAlternative: Boolean,
    onTryAlternative: () -> Unit,
    onClose: () -> Unit,
    onCastClick: () -> Unit = {},
    externalCasting: Boolean = false
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val activity = remember(context) { context.findPlayerActivity() }
    val composeView = LocalView.current
    // A Compose Dialog has its own Android Window. Hide system bars there as
    // well as on the host Activity, otherwise status/navigation bars remain visible.
    val dialogWindow = remember(composeView) {
        (composeView.parent as? DialogWindowProvider)?.window
    }

    // SENSOR_LANDSCAPE permits either landscape rotation without prompting the user.
    // The activity manifest already handles orientation/screenSize config changes.
    DisposableEffect(activity, dialogWindow) {
        val original = activity?.requestedOrientation
        val windows = listOfNotNull(activity?.window, dialogWindow).distinct()
        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        windows.forEach { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = original ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            windows.forEach { window ->
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    var state by remember(server.url) { mutableIntStateOf(Player.STATE_IDLE) }
    var playing by remember(server.url) { mutableStateOf(false) }
    var position by remember(server.url) { mutableLongStateOf(0L) }
    var duration by remember(server.url) { mutableLongStateOf(0L) }
    var visible by remember(server.url) { mutableStateOf(true) }
    var err by remember(server.url) { mutableStateOf<String?>(null) }
    var tracks by remember(server.url) { mutableStateOf(Tracks.EMPTY) }
    var trackSheet by remember(server.url) { mutableStateOf(false) }
    var sourceSheet by remember(server.url) { mutableStateOf(false) }
    var retry by remember(server.url) { mutableIntStateOf(0) }

    val player = remember(server.id, server.url) {
        val transport = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(server.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, transport))
            ).build().apply {
                val inferred = server.mimeType ?: when {
                    server.isHls -> MimeTypes.APPLICATION_M3U8
                    server.url.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
                    server.url.contains(".ism/Manifest", ignoreCase = true) -> MimeTypes.APPLICATION_SS
                    server.url.substringBefore('?').endsWith(".mkv", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
                    server.url.substringBefore('?').endsWith(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
                    else -> null
                }
                val subtitles = server.subtitles.mapNotNull { entry ->
                    runCatching {
                        val type = when (entry.url.substringBefore('?').substringAfterLast('.').lowercase()) {
                            "srt" -> MimeTypes.APPLICATION_SUBRIP
                            "ass", "ssa" -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.TEXT_VTT
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(entry.url))
                            .setMimeType(type)
                            .setLanguage(entry.language ?: "pt")
                            .setLabel(entry.label)
                            .build()
                    }.getOrNull()
                }
                val media = MediaItem.Builder()
                    .setUri(server.url)
                    .setSubtitleConfigurations(subtitles)
                    .apply { inferred?.let(::setMimeType) }
                    .build()
                setMediaItem(media)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("pt-BR", "pt-PT", "pt")
                    .setPreferredTextLanguages("pt-BR", "pt-PT", "pt")
                    .build()
                if (resumePositionMs > 15_000L) seekTo(resumePositionMs)
            }
    }
    LaunchedEffect(externalCasting, player) {
        if (externalCasting) player.pause()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state = playbackState
                if (playbackState == Player.STATE_READY) err = null
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(currentTracks: Tracks) { tracks = currentTracks }
            override fun onPlayerError(error: PlaybackException) {
                visible = true
                err = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                        "Esta fonte devolveu um contentor de vídeo incompatível. Tenta outra fonte ou formato."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                        "O ficheiro de vídeo desta fonte está danificado ou mal formado."
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "A fonte recusou a ligação ou o endereço do vídeo expirou."
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                        "A fonte devolveu uma página ou resposta que não é um vídeo."
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Este vídeo usa uma codificação que o dispositivo não consegue reproduzir."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Falha de ligação ao servidor da fonte."
                    else -> "Falha na reprodução (${error.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            val length = player.duration
            if (length > 0) onProgress(player.currentPosition, length)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        // Probe direct HTTP sources once before playback. The actual content
        // can be HLS, DASH, SmoothStreaming or a progressive container.
        val detected = VistaStreamMime.inspect(server)
        if (detected.unsupportedReason != null) {
            err = detected.unsupportedReason
            return@LaunchedEffect
        }
        val media = player.currentMediaItem
        val hint = detected.mime
        if (hint != null && media != null &&
            media.localConfiguration?.mimeType != hint
        ) {
            player.setMediaItem(media.buildUpon().setMimeType(hint).build())
        }
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(player) {
        var lastSavedBucket = -1L
        while (true) {
            delay(1200)
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            if (player.isPlaying && duration > 0 && position / 5_000L != lastSavedBucket) {
                onProgress(position, duration)
                lastSavedBucket = position / 5_000L
            }
        }
    }
    LaunchedEffect(visible, playing, trackSheet, err) {
        if (visible && playing && !trackSheet && err == null) {
            delay(4_500)
            visible = false
        }
    }
    LaunchedEffect(player, state, retry) {
        if (state == Player.STATE_BUFFERING || state == Player.STATE_IDLE) {
            delay(22_000)
            if (state != Player.STATE_READY && err == null) {
                err = "O vídeo não começou a carregar. Podes escolher outra fonte."
                visible = true
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black)
            .clickable { visible = !visible }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { this.player = player; useController = false } },
            update = { if (it.player !== player) it.player = player }
        )

        if (state == Player.STATE_BUFFERING && err == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if (visible) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.72f),
                0.22f to Color.Transparent,
                0.62f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.92f)
            )))
            Row(
                modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (episodeLabel.isNotBlank()) {
                        Text(episodeLabel, color = Color.White.copy(alpha = .75f),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = onCastClick) {
                    Icon(Icons.Default.Cast, contentDescription = "Conectar Chromecast", tint = Color.White)
                }
            }
            if (err == null) {
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0)) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Voltar 10 segundos", tint = Color.White)
                    }
                    FilledTonalIconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play(); visible = true },
                        modifier = Modifier.size(66.dp)
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pausar" else "Reproduzir",
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    IconButton(onClick = { player.seekTo(player.currentPosition + 10_000L) }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Avançar 10 segundos", tint = Color.White)
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (duration > 0) {
                    Slider(
                        value = (position.toFloat() / duration).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            player.seekTo((fraction * duration).toLong())
                            position = player.currentPosition
                            visible = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${clock(position)} / ${clock(duration)}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    if (availableServers.size > 1) {
                        TextButton(onClick = { sourceSheet = true }) {
                            Text("Fontes", color = Color.White)
                        }
                    }
                    TextButton(onClick = { trackSheet = true }) {
                        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Áudio e legendas", color = Color.White)
                    }
                }
            }
        }

        if (err != null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .fillMaxWidth(.82f)
                    .background(Color(0xF0141620), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Não foi possível reproduzir", color = Color.White, fontWeight = FontWeight.Bold)
                Text(err.orEmpty(), color = Color.White, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = {
                        err = null
                        state = Player.STATE_BUFFERING
                        retry++
                        player.prepare()
                        player.playWhenReady = true
                    }) { Text("Tentar novamente") }
                    OutlinedButton(onClick = if (hasAlternative) onTryAlternative else onClose) {
                        Text(if (hasAlternative) "Outra fonte" else "Voltar")
                    }
                }
            }
        }
    }
    if (sourceSheet) {
        AlertDialog(
            onDismissRequest = { sourceSheet = false },
            title = { Text("Escolher fonte de reprodução") },
            text = {
                Column(
                    Modifier.widthIn(max = 600.dp).heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableServers.forEach { option ->
                        TextButton(
                            onClick = {
                                sourceSheet = false
                                if (option.id != server.id) onSelectSource(option)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${if (option.id == server.id) "✓  " else ""}${option.providerName} · ${option.name}" +
                                    (option.audioLanguage?.let { " · $it" } ?: ""),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sourceSheet = false }) { Text("Fechar") } }
        )
    }
    if (trackSheet) {
        VistaTrackPicker(
            tracks = tracks,
            player = player,
            onDismiss = { trackSheet = false }
        )
    }
}

private fun clock(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}

private data class VistaTrack(val group: Tracks.Group, val index: Int) {
    val format get() = group.getTrackFormat(index)
    val selected get() = group.isTrackSelected(index)
}

@Composable
private fun VistaTrackPicker(tracks: Tracks, player: ExoPlayer, onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf("Áudio") }
    val options = remember(tracks, tab) {
        val type = when (tab) {
            "Áudio" -> C.TRACK_TYPE_AUDIO
            "Legendas" -> C.TRACK_TYPE_TEXT
            else -> C.TRACK_TYPE_VIDEO
        }
        tracks.groups.filter { it.type == type }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) }.map {
                VistaTrack(group, it)
            }
        }.distinctBy { "${it.group.mediaTrackGroup.id}:${it.index}" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Áudio e legendas") },
        text = {
            Column(Modifier.widthIn(min = 260.dp, max = 620.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Áudio", "Legendas", "Qualidade").forEach { label ->
                        FilterChip(
                            selected = tab == label,
                            onClick = { tab = label },
                            label = { Text(label) }
                        )
                    }
                }
                Column(Modifier.heightIn(max = 248.dp).verticalScroll(rememberScrollState())) {
                    if (tab == "Legendas") {
                        TextButton(onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            onDismiss()
                        }) { Text("Desativadas") }
                    }
                    if (tab == "Qualidade") {
                        TextButton(onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                            onDismiss()
                        }) { Text("Automática") }
                    }
                    if (options.isEmpty()) {
                        Text(
                            "A fonte não fornece faixas selecionáveis deste tipo.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    options.forEach { choice ->
                        val f = choice.format
                        val lang = f.language?.let { tag ->
                            Locale.forLanguageTag(tag).getDisplayName(Locale("pt", "BR"))
                        }?.takeIf { it.isNotBlank() && it != "und" }
                        val name = if (tab == "Qualidade") {
                            f.height.takeIf { it > 0 }?.let { "${it}p" } ?: f.label ?: "Faixa ${choice.index + 1}"
                        } else listOfNotNull(lang, f.label).distinct().joinToString(" · ")
                            .ifBlank { "Faixa ${choice.index + 1}" }
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val t = choice.group.type
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(t, false)
                                    .setOverrideForType(
                                        TrackSelectionOverride(choice.group.mediaTrackGroup, choice.index)
                                    ).build()
                                onDismiss()
                            }
                        ) {
                            Text(
                                "${if (choice.selected) "✓  " else ""}$name",
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
