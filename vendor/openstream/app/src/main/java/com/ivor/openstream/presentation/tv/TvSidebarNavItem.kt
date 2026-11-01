package com.ivor.openstream.presentation.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface

internal val TvSidebarWidth: Dp = 64.dp

/**
 * The rail must have pixel-identical icon alignment on every Android TV.
 * Bundled Material Rounded vectors avoid the transparent gutters and network
 * failures of previous Icons8 PNGs. Only the *glyph* animates on D-pad focus.
 */
private fun TvDestination.roundedGlyph(): ImageVector = when (this) {
    TvDestination.HOME -> Icons.Rounded.Home
    TvDestination.ANIME -> Icons.Rounded.AutoAwesome
    TvDestination.SEARCH -> Icons.Rounded.Search
    TvDestination.SAVED -> Icons.Rounded.Bookmark
    TvDestination.DOWNLOADS -> Icons.Rounded.Download
    TvDestination.EXTENSIONS -> Icons.Rounded.Extension
    TvDestination.SETTINGS -> Icons.Rounded.Settings
}

@Composable
internal fun TvSidebarNavItem(
    destination: TvDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(
        targetValue = if (focused) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 560f),
        label = "tvMenuGlyphZoom"
    )
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.size(52.dp).onFocusChanged { focused = it.isFocused },
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            // The active route is bright even *without* remote focus.
            containerColor = if (selected) scheme.primary else Color.Transparent,
            contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
            focusedContainerColor = if (selected) scheme.primary else scheme.surfaceContainerHigh,
            focusedContentColor = if (selected) scheme.onPrimary else scheme.onSurface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = destination.roundedGlyph(),
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(29.dp).scale(zoom)
            )
        }
    }
}
