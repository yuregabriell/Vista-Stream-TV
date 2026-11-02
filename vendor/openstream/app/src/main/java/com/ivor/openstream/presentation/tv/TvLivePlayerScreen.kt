package com.ivor.openstream.presentation.tv

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.livetv.Channel
import com.ivor.openstream.presentation.livetv.Country
import com.ivor.openstream.presentation.livetv.LiveTvAddons
import com.ivor.openstream.presentation.livetv.LiveTvGuide
import com.ivor.openstream.presentation.livetv.LiveProgramme
import com.ivor.openstream.presentation.player.components.ExoPlayerView
import com.ivor.openstream.presentation.player.session.MiniPlayerViewModel
import kotlinx.coroutines.delay

/** Snapshot of the visible channel order so CH+/- uses the active country and filter. */
internal data class TvLiveSelection(
    val country: Country,
    val channels: List<Channel>,
    val selectedId: String
)

/**
 * Android delivers CH+/CH- to Activity.dispatchKeyEvent, including when a native
 * Media3 PlayerView owns focus. Consume only real channel/page rocker keys while
 * the live player is on screen; leave VOLUME_UP/DOWN to Android (and Cast).
 */
internal enum class TvLiveCommand { NEXT, PREVIOUS, CHANNELS, GUIDE, INFO }

internal object TvLiveRemoteKeys {
    private var listener: ((TvLiveCommand) -> Unit)? = null
    private var panelOpen: (() -> Boolean)? = null

    fun setListener(onCommand: ((TvLiveCommand) -> Unit)?, isPanelOpen: (() -> Boolean)? = null) {
        listener = onCommand
        panelOpen = isPanelOpen
    }

    fun dispatch(event: KeyEvent): Boolean {
        val handler = listener ?: return false
        val code = event.keyCode
        val channelKey = code in listOf(
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN
        )
        if (!channelKey && panelOpen?.invoke() == true) return false
        val command = when (code) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> TvLiveCommand.NEXT
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> TvLiveCommand.PREVIOUS
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT -> TvLiveCommand.CHANNELS
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU -> TvLiveCommand.GUIDE
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> TvLiveCommand.INFO
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler(command)
        return true
    }
}
/**
 * Same Media3 engine, playback controls, track selector and subtitle UI used by
 * VistaStream's movie/series player — presented as a genuine fullscreen TV route.
 */
@Composable
internal fun TvLivePlayerScreen(
    selection: TvLiveSelection,
    onExit: () -> Unit,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) {
        var current: Context = context
        while (current is android.content.ContextWrapper && current !is Activity) {
            current = current.baseContext
        }
        current as? Activity
    }
    val session = viewModel.session
    var index by remember(selection) {
        mutableStateOf(selection.channels.indexOfFirst { it.id == selection.selectedId }.coerceAtLeast(0))
    }
    val channel = selection.channels.getOrNull(index)
    var resolvedUrl by remember(channel?.id) { mutableStateOf<String?>(null) }
    var resolving by remember(channel?.id) { mutableStateOf(channel != null) }
    LaunchedEffect(channel?.id) {
        resolving = true
        resolvedUrl = channel?.let { LiveTvAddons.resolve(it) }
        resolving = false
    }
    var showChannelBanner by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(TvLivePanel.NONE) }
    val prefs = remember(context) {
        context.getSharedPreferences("vistastream_live_tv", Context.MODE_PRIVATE)
    }
    var favorites by remember(selection.country.code) {
        mutableStateOf(prefs.getStringSet("favorites_" + selection.country.code, emptySet())?.toSet() ?: emptySet())
    }
    var programmes by remember(selection.country.code) {
        mutableStateOf<Map<String, List<LiveProgramme>>>(emptyMap())
    }
    LaunchedEffect(selection.country.code) {
        programmes = LiveTvGuide.programmes(context, selection.country.code)
    }
    fun tune(ch: Channel) {
        val nextIndex = selection.channels.indexOfFirst { it.id == ch.id }
        if (nextIndex >= 0) index = nextIndex
        panel = TvLivePanel.NONE
    }
    fun toggleFavorite(ch: Channel) {
        favorites = if (ch.id in favorites) favorites - ch.id else favorites + ch.id
        prefs.edit().putStringSet("favorites_" + selection.country.code, favorites).apply()
    }
    val onChange = remember(selection) {
        { direction: Int ->
            if (selection.channels.isNotEmpty()) {
                index = (index + direction + selection.channels.size) % selection.channels.size
            }
        }
    }
    val latestPanel by rememberUpdatedState(panel)
    val latestCommands by rememberUpdatedState<(TvLiveCommand) -> Unit>({ cmd ->
        when (cmd) {
            TvLiveCommand.NEXT -> onChange(1)
            TvLiveCommand.PREVIOUS -> onChange(-1)
            TvLiveCommand.CHANNELS -> panel = TvLivePanel.CHANNELS
            TvLiveCommand.GUIDE -> panel = TvLivePanel.GUIDE
            TvLiveCommand.INFO -> panel = TvLivePanel.INFO
        }
    })
    // Activity receives physical channel buttons even when Media3 owns focus.
    DisposableEffect(Unit) {
        TvLiveRemoteKeys.setListener({ cmd -> latestCommands(cmd) }, { latestPanel != TvLivePanel.NONE })
        onDispose { TvLiveRemoteKeys.setListener(null) }
    }

    // Only one shared Media3 player exists in the VistaStream application.
    DisposableEffect(session) {
        session.stop()
        onDispose { session.stop() }
    }

    // The live-TV route has no sidebar, letterbox frame or dialog margins.
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insets = WindowInsetsControllerCompat(window, window.decorView)
            insets.hide(WindowInsetsCompat.Type.systemBars())
            insets.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (window != null) {
                val insets = WindowInsetsControllerCompat(window, window.decorView)
                insets.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
        }
    }

    LaunchedEffect(channel?.id) {
        showChannelBanner = true
        delay(2400)
        showChannelBanner = false
    }

    BackHandler { if (panel != TvLivePanel.NONE) panel = TvLivePanel.NONE else onExit() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (channel != null) {
            // ExoPlayerView prepares the URI using the app's own Media3 session,
            // including HLS/DASH detection and existing quality/audio controls.
            if (resolvedUrl != null) {
            ExoPlayerView(
                videoUrl = resolvedUrl.orEmpty(),
                title = channel.name,
                subtitle = selection.country.label + " · Ao vivo",
                requestHeaders = emptyMap(),
                exoPlayer = session.player,
                applyRequestHeaders = session::setRequestHeaders,
                smoothMotion = session.smoothMotion,
                isFullscreen = true,
                onFullscreenToggle = {},
                onBackClick = { if (panel != TvLivePanel.NONE) panel = TvLivePanel.NONE else onExit() },
                onMinimize = onExit,
                dpadControls = true,
                liveTvMode = true,
                canChangeSource = false,
                sourceLabel = "TV ao vivo",
                showSkipButton = false,
                keepPlayingInBackground = false,
                modifier = Modifier.fillMaxSize()
            )
            } else {
                Text(
                    if (resolving) "A obter transmissão de " + channel.sourceLabel + "..."
                    else "Nenhuma transmissão direta disponível nesta fonte.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }

            // NOS-style non-interactive zapping identity: the physical rocker
            // changes streams, then this compact banner fades on its own.
            AnimatedVisibility(
                visible = showChannelBanner && panel == TvLivePanel.NONE,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 34.dp, top = 26.dp)
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xF21C182F), Color(0xE8181E30))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(width = 72.dp, height = 50.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (index + 1).toString().padStart(2, '0') + "  ",
                                color = Color(0xFFE2BEFF),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                channel.name,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "● AO VIVO  ·  " + selection.country.label,
                            color = Color(0xFFFFA3C8),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            when (panel) {
                TvLivePanel.NONE -> Unit
                TvLivePanel.CHANNELS -> TvLiveChannelOverlay(
                    channels = selection.channels, current = channel, favorites = favorites,
                    onTune = ::tune, onFavorite = ::toggleFavorite,
                    onGuide = { panel = TvLivePanel.GUIDE },
                    onClose = { panel = TvLivePanel.NONE }
                )
                TvLivePanel.GUIDE -> TvLiveEpgOverlay(
                    channels = selection.channels, current = channel, programmes = programmes,
                    onTune = ::tune, onClose = { panel = TvLivePanel.NONE }
                )
                TvLivePanel.INFO -> Box(Modifier.align(Alignment.BottomCenter)) {
                    TvLiveInfoOverlay(
                        channel = channel, number = index + 1,
                        programmes = LiveTvGuide.forChannel(channel, programmes),
                        onGuide = { panel = TvLivePanel.GUIDE },
                        onChannels = { panel = TvLivePanel.CHANNELS },
                        onClose = { panel = TvLivePanel.NONE }
                    )
                }
            }
        } else {
            Text("Canal indisponível", Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}
