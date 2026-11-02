package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ivor.openstream.presentation.livetv.LiveProgramme
import com.ivor.openstream.presentation.livetv.LiveTvGuide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class TvLivePanel { NONE, CHANNELS, GUIDE, INFO }
private val focused = Color(0xFFEDE7F7)
private val accent = Color(0xFFDF9DFA)
private fun scheduleTime(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

@Composable
internal fun TvLiveChannelOverlay(
    channels: List<Channel>,
    current: Channel,
    favorites: Set<String>,
    onTune: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit
) {
    var category by remember { mutableStateOf("Todos") }
    val categories = remember(channels) {
        (listOf("Todos","Favoritos") + channels.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()).distinct()
    }
    val visible = channels.filter {
        category == "Todos" || category == it.group || (category == "Favoritos" && it.id in favorites)
    }
    Row(
        Modifier.width(660.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF90C1020),Color(0xF0191B2B))))
            .padding(start = 24.dp, end = 22.dp, top = 24.dp, bottom = 24.dp)
    ) {
        Column(Modifier.width(155.dp)) {
            Text("CATEGORIAS", color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it }) { group ->
                    Surface(
                        onClick = { category = group },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (group == category) Color(0xFF453057) else Color.Transparent,
                            contentColor = Color.White,
                            focusedContainerColor = focused,
                            focusedContentColor = Color(0xFF161621)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
                    ) {
                        Text(group, Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TvPill("Guia TV", onClick = onGuide)
            TvPill("Fechar", onClick = onClose)
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todos os canais", modifier = Modifier.weight(1f),
                    color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge)
                Text(visible.size.toString(), color = Color(0xFFB6B3C5))
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { onTune(channel) },
                            modifier = Modifier.weight(1f).height(68.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (current.id == channel.id) Color(0xFF433255) else Color(0xFF232737),
                                contentColor = Color.White,
                                focusedContainerColor = focused,
                                focusedContentColor = Color(0xFF161624)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.025f)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text((channels.indexOf(channel)+1).toString().padStart(3,'0'),
                                    modifier = Modifier.width(39.dp),
                                    style = MaterialTheme.typography.labelMedium)
                                if (channel.logo.isNotBlank()) {
                                    AsyncImage(
                                        model = channel.logo, contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(width = 52.dp, height = 44.dp)
                                    )
                                } else {
                                    androidx.tv.material3.Icon(Icons.Default.Public,
                                        contentDescription = null, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(channel.group.ifBlank { channel.sourceLabel },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            onClick = { onFavorite(channel) },
                            modifier = Modifier.size(43.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF28283C),
                                contentColor = Color(0xFFFFA2C0),
                                focusedContainerColor = focused,
                                focusedContentColor = Color(0xFF241C35)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (channel.id in favorites) "♥" else "♡",
                                    style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvLiveEpgOverlay(
    channels: List<Channel>,
    current: Channel,
    programmes: Map<String, List<LiveProgramme>>,
    onTune: (Channel) -> Unit,
    onClose: () -> Unit
) {
    val start = remember { (System.currentTimeMillis() / 1_800_000L) * 1_800_000L }
    val stop = start + 3 * 3_600_000L
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xF0181B2B), Color(0xFC090B17)))
        ).padding(horizontal = 34.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VISTASTREAM  ·  GUIA", color = accent, fontWeight = FontWeight.Bold)
                Text("Programação", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            TvPill("Fechar guia", onClick = onClose)
        }
        Spacer(Modifier.height(15.dp))
        Row {
            Spacer(Modifier.width(240.dp))
            repeat(6) { slot ->
                Text(scheduleTime(start + slot * 1_800_000L), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = Color(0xFFC3BFD0))
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(channels, key = { it.id }) { ch ->
                val programmesForChannel = LiveTvGuide.forChannel(ch, programmes)
                    .filter { it.endMs > start && it.startMs < stop }.sortedBy { it.startMs }
                Row(Modifier.fillMaxWidth().height(66.dp)) {
                    Surface(
                        onClick = { onTune(ch) },
                        modifier = Modifier.width(232.dp).fillMaxHeight(),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (ch.id == current.id) Color(0xFF44335D) else Color(0xFF26293B),
                            contentColor = Color.White,
                            focusedContainerColor = focused,
                            focusedContentColor = Color(0xFF141626)
                        )
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (ch.logo.isNotBlank()) AsyncImage(ch.logo, contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(width = 48.dp, height = 40.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(ch.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val cells = mutableListOf<Pair<String,Float>>()
                    var cursor = start
                    programmesForChannel.forEach { p ->
                        val from = maxOf(p.startMs,start,cursor)
                        val to = minOf(p.endMs,stop)
                        if (from > cursor) cells += "Sem informação" to
                            ((from - cursor).toFloat() / (stop-start))
                        if (to > from) {
                            cells += (p.title + "\n" + scheduleTime(p.startMs) + "–" + scheduleTime(p.endMs)) to
                                ((to - from).toFloat() / (stop-start))
                            cursor = to
                        }
                    }
                    if (cursor < stop) cells += "Sem informação" to
                        ((stop - cursor).toFloat() / (stop-start))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        cells.filter { it.second > .005f }.forEach { (name, width) ->
                            Surface(
                                onClick = { onTune(ch) },
                                modifier = Modifier.weight(width).fillMaxHeight(),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0xFF303448), contentColor = Color.White,
                                    focusedContainerColor = focused,
                                    focusedContentColor = Color(0xFF1C172A)
                                )
                            ) {
                                Text(name, Modifier.padding(8.dp), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvLiveInfoOverlay(
    channel: Channel,
    number: Int,
    programmes: List<LiveProgramme>,
    onGuide: () -> Unit,
    onChannels: () -> Unit,
    onClose: () -> Unit
) {
    val now = System.currentTimeMillis()
    val playing = programmes.firstOrNull { it.startMs <= now && it.endMs > now }
    val next = programmes.firstOrNull { it.startMs > now }
    Column(
        Modifier.fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xF0121523),Color(0xFF101320))),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ).padding(horizontal = 38.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number.toString().padStart(3,'0'), color = accent,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(14.dp))
            if (channel.logo.isNotBlank()) {
                AsyncImage(channel.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 66.dp, height = 43.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("● AO VIVO  ·  " + channel.group, color = Color(0xFFFF9EC7))
            }
            TvPill("Fechar", onClick = onClose)
        }
        Text(playing?.title ?: "Ao vivo · Programação indisponível", color = Color.White,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        if (playing != null) {
            Text(scheduleTime(playing.startMs) + " – " + scheduleTime(playing.endMs),
                color = Color(0xFFC6C2D4))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { ((now - playing.startMs).toFloat() /
                    (playing.endMs - playing.startMs).coerceAtLeast(1L)).coerceIn(0f,1f) },
                color = accent, trackColor = Color(0xFF464357), modifier = Modifier.fillMaxWidth()
            )
            if (playing.description.isNotBlank()) Text(playing.description, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = Color(0xFFD0CBD8))
        }
        if (next != null) Text("A seguir · " + scheduleTime(next.startMs) + "  " + next.title,
            color = Color(0xFFC0BCD0), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvPill("Todos os canais", onClick = onChannels)
            TvPill("Guia TV", onClick = onGuide)
        }
    }
}
