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
internal object TvLiveRemoteKeys {
    private var channelListener: ((Int) -> Unit)? = null

    fun setListener(listener: ((Int) -> Unit)?) {
        channelListener = listener
    }

    fun dispatch(event: KeyEvent): Boolean {
        val listener = channelListener ?: return false
        val change = when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> 1
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> -1
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            listener(change)
        }
        // Consume key-up as well, preventing the channel rocker from scrolling a menu.
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
    var showChannelBanner by remember { mutableStateOf(true) }
    val onChange = remember(selection) {
        { direction: Int ->
            if (selection.channels.isNotEmpty()) {
                index = (index + direction + selection.channels.size) % selection.channels.size
            }
        }
    }
    val latestOnChange by rememberUpdatedState(onChange)

    // Keep key routing independent of focus inside the film-style player chrome.
    DisposableEffect(Unit) {
        TvLiveRemoteKeys.setListener { direction -> latestOnChange(direction) }
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

    BackHandler(onBack = onExit)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (channel != null) {
            // ExoPlayerView prepares the URI using the app's own Media3 session,
            // including HLS/DASH detection and existing quality/audio controls.
            ExoPlayerView(
                videoUrl = channel.url,
                title = channel.name,
                subtitle = selection.country.label + " · Ao vivo",
                requestHeaders = emptyMap(),
                exoPlayer = session.player,
                applyRequestHeaders = session::setRequestHeaders,
                smoothMotion = session.smoothMotion,
                isFullscreen = true,
                onFullscreenToggle = {},
                onBackClick = onExit,
                onMinimize = onExit,
                dpadControls = true,
                canChangeSource = false,
                sourceLabel = "TV ao vivo",
                showSkipButton = false,
                keepPlayingInBackground = false,
                modifier = Modifier.fillMaxSize()
            )

            // NOS-style non-interactive zapping identity: the physical rocker
            // changes streams, then this compact banner fades on its own.
            AnimatedVisibility(
                visible = showChannelBanner,
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
        } else {
            Text("Canal indisponível", Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}
