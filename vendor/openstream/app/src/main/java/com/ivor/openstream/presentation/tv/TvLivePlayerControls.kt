package com.ivor.openstream.presentation.tv

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Third VistaStream player chrome: live TV. Unlike Films/Series there is no
 * seekbar, next episode, source picker or skip intro. Shorts keep their own UI.
 * Physical CH+/CH- live in Activity.dispatchKeyEvent, not screen buttons.
 */
@Composable
internal fun TvLivePlayerControls(
    visible: Boolean,
    title: String,
    subtitle: String,
    playing: Boolean,
    onPause: () -> Unit,
    onChannels: () -> Unit,
    onGuide: () -> Unit,
    onInfo: () -> Unit,
    onSettings: () -> Unit,
    focusRequester: FocusRequester,
    onInteraction: () -> Unit
) {
    AnimatedVisibility(
        visible = visible, enter = fadeIn(), exit = fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF3121220), Color(0xFB0A0B15))
                        )
                    )
                    .padding(horizontal = 42.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("●  AO VIVO   ·   VISTASTREAM LIVE",
                    color = Color(0xFFE6A7FF),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                Text(title, color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFFC5C0D0),
                    style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvPill(
                        if (playing) "Pausar" else "Reproduzir",
                        modifier = Modifier.focusRequester(focusRequester),
                        icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        onClick = { onInteraction(); onPause() }
                    )
                    TvPill("Canais", icon = Icons.Default.List,
                        onClick = { onInteraction(); onChannels() })
                    TvPill("Guia TV", icon = Icons.Default.MenuBook,
                        onClick = { onInteraction(); onGuide() })
                    TvPill("Informações", icon = Icons.Default.Info,
                        onClick = { onInteraction(); onInfo() })
                    Spacer(Modifier.weight(1f))
                    TvPill("Qualidade e áudio", icon = Icons.Default.Settings,
                        onClick = { onInteraction(); onSettings() })
                }
                Text(
                    "CH+/CH−  Mudar canal     ·     OK  Reproduzir/Pausar     ·     Voltar  Sair",
                    color = Color(0xFFAFAAC0), style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
