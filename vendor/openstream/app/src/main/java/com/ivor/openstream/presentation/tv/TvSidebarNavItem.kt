package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage

/**
 * Network artwork always uses one fixed PNG from Icons8, not platform emoji fonts.
 * Fallback Material icon works if the network is unavailable, maintaining D-pad access.
 *
 * Attribution: Icons8.com / https://icons8.com/license
 * Character artwork may have separate trademark conditions; keep as opt-in asset
 * pending a trademark review before any public marketplace distribution.
 */
internal object TvIcons8 {
    // Uniform iOS7 monochrome artwork. No emoji glyphs or device-dependent fonts.
    private const val BASE = "https://img.icons8.com/FFFFFF/96/ios7/"
    fun url(destination: TvDestination): String = BASE + when (destination) {
        TvDestination.HOME -> "home.png"
        TvDestination.ANIME -> "kuromi.png"
        TvDestination.SEARCH -> "search.png"
        TvDestination.SAVED -> "bookmark.png"
        TvDestination.DOWNLOADS -> "download.png"
        TvDestination.EXTENSIONS -> "puzzle.png"
        TvDestination.SETTINGS -> "settings.png"
    }
}

internal val TvSidebarWidth: Dp = 62.dp

/** Focus enlarges only the active icon, never the other rail items. */
@Composable
internal fun TvSidebarNavItem(
    destination: TvDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var networkIconFailed by remember(destination) { mutableStateOf(false) }
    var networkIconReady by remember(destination) { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.28f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 500f),
        label = "tvSideIconOnly"
    )

    Surface(
        modifier = modifier.size(48.dp).onFocusChanged { focused = it.isFocused },
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.29f)
                else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
            focusedContentColor = Color.White
        ),
        // The Surface itself must stay the same size; only its icon grows.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!networkIconReady || networkIconFailed) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = if (networkIconFailed) stringResource(destination.labelRes) else null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).scale(zoom)
                )
            }
            if (!networkIconFailed) {
                AsyncImage(
                    model = TvIcons8.url(destination),
                    contentDescription = stringResource(destination.labelRes),
                    contentScale = ContentScale.Fit,
                    onSuccess = { networkIconReady = true },
                    onError = { networkIconFailed = true },
                    modifier = Modifier.size(25.dp).scale(zoom)
                )
            }
        }
    }
}
