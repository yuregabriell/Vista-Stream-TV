package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.livetv.Channel
import com.ivor.openstream.presentation.livetv.LiveTvGuide
import com.ivor.openstream.presentation.livetv.TvProgramme
import com.ivor.openstream.presentation.livetv.clock

private val foreground = Color(0xFFF6F3FB)
private val soft = Color(0xFFC5C0D3)
private val accent = Color(0xFFE0AAFF)

/** Translucent native-TV channel list and guide over video, inspired by linear TV interfaces. */
@Composable
internal fun TvLivePlayerOverlay(
    mode: String,
    channels: List<Channel>,
    activeChannel: Channel,
    guide: LiveTvGuide,
    onPlay: (Channel) -> Unit,
    onDismiss: () -> Unit,
    onModeChange: (String) -> Unit = {}
) {
    val focus = remember(mode) { FocusRequester() }
    LaunchedEffect(mode) {
        kotlinx.coroutines.delay(70)
        runCatching { focus.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        when (mode) {
            "info" -> Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xEA171421), Color(0xFD080A14))),
                        RoundedCornerShape(topStart = 25.dp, topEnd = 25.dp)
                    )
                    .padding(horizontal = 48.dp, vertical = 25.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvOverlayLogo(activeChannel)
                    Spacer(Modifier.width(22.dp))
                    Text(activeChannel.name, color = foreground, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    TvPill("Lista", onClick = { onModeChange("channels") })
                    Spacer(Modifier.width(9.dp))
                    TvPill("Guia", onClick = { onModeChange("guide") })
                    Spacer(Modifier.width(9.dp))
                    TvPill("Fechar", modifier = Modifier.focusRequester(focus), onClick = onDismiss)
                }
                Spacer(Modifier.height(18.dp))
                TvOverlayProgramme(guide.now(activeChannel), guide.next(activeChannel))
            }
            "guide" -> Column(
                Modifier.fillMaxSize().padding(start = 31.dp, end = 34.dp, top = 34.dp, bottom = 28.dp)
                    .background(Color(0xF20D111F), RoundedCornerShape(23.dp)).padding(25.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GUIA TV", color = foreground,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TvPill("Canais", onClick = { onModeChange("channels") })
                    Spacer(Modifier.width(12.dp))
                    TvPill("Fechar", modifier = Modifier.focusRequester(focus), onClick = onDismiss)
                }
                Spacer(Modifier.height(8.dp))
                Text("Programação real · Seleciona uma faixa para assistir ao canal",
                    color = soft, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(channels, key = { it.id }) { channel ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { onPlay(channel) },
                                modifier = Modifier.width(230.dp).height(76.dp),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (channel.id == activeChannel.id)
                                        Color(0xFF514069) else Color(0xFF232637),
                                    focusedContainerColor = Color(0xFF815CA1)
                                )
                            ) {
                                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    TvOverlayLogo(channel, 45)
                                    Spacer(Modifier.width(9.dp))
                                    Text(channel.name, color = foreground, maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            val now = System.currentTimeMillis()
                            val items = guide.schedule(channel).filter {
                                it.endMs > now && it.startMs < now + 4 * 3600_000L
                            }.take(7)
                            if (items.isEmpty()) {
                                Text("Sem programação", color = soft, modifier = Modifier.padding(15.dp))
                            } else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(items, key = { it.channelId + "-" + it.startMs }) { slot ->
                                    Surface(
                                        onClick = { onPlay(channel) },
                                        modifier = Modifier.width(210.dp).height(76.dp),
                                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = Color(0xFF262637),
                                            focusedContainerColor = Color(0xFF815CA1)
                                        )
                                    ) {
                                        Column(Modifier.padding(11.dp)) {
                                            Text(clock(slot.startMs) + " – " + clock(slot.endMs),
                                                color = accent, style = MaterialTheme.typography.labelSmall)
                                            Text(slot.title, color = foreground, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier.width(420.dp).fillMaxHeight()
                        .background(Color(0xEF0C1020))
                        .padding(start = 25.dp, end = 15.dp, top = 26.dp, bottom = 23.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TODOS OS CANAIS", color = foreground,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TvPill("Fechar", modifier = Modifier.focusRequester(focus), onClick = onDismiss)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Usa o comando para selecionar o canal",
                        color = soft, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(19.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(channels, key = { it.id }) { channel ->
                            Surface(
                                onClick = { onPlay(channel) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(15.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (channel.id == activeChannel.id)
                                        Color(0xFF554071) else Color(0xFF252536),
                                    focusedContainerColor = Color(0xFF825CB0)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    TvOverlayLogo(channel, 54)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(channel.name, color = foreground,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(guide.now(channel)?.title ?: "Sem programação",
                                            color = soft, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (channel.id == activeChannel.id) Text("●", color = accent)
                                }
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.align(Alignment.BottomStart).fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xF6090A14))
                                )
                            ).padding(horizontal = 42.dp, vertical = 32.dp)
                    ) {
                        Text("●  AGORA NA TV", color = accent, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(7.dp))
                        Text(activeChannel.name, color = foreground,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        TvOverlayProgramme(guide.now(activeChannel), guide.next(activeChannel))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvOverlayLogo(channel: Channel, size: Int = 60) {
    if (channel.logo.isNotBlank()) AsyncImage(
        model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = size.dp, height = 44.dp)
    ) else Text("◉", color = accent)
}

@Composable
private fun TvOverlayProgramme(programme: TvProgramme?, next: TvProgramme?) {
    if (programme == null) {
        Text("Sem programação disponível para este canal.", color = soft,
            style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(programme.title, color = foreground,
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(clock(programme.startMs) + " — " + clock(programme.endMs),
            color = soft, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(7.dp))
        LinearProgressIndicator(
            progress = { programme.progress() },
            modifier = Modifier.fillMaxWidth(),
            color = accent, trackColor = Color(0xFF494458)
        )
        if (programme.description.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(programme.description, color = soft,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    if (next != null) {
        Spacer(Modifier.height(13.dp))
        Text("A seguir  ·  " + clock(next.startMs) + "  " + next.title,
            color = accent, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
