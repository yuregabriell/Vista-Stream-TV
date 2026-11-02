package com.ivor.openstream.presentation.shorts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Only internally curated works that explicitly allow free, ad-free,
 * in-app distribution may be displayed. This JSON is empty until appropriate
 * rights and COMPLETE episode URLs have been verified; it is not ReelShort
 * scraping and it never substitutes a paywalled episode with a trailer.
 */
@Serializable
internal data class ShortEpisode(val number: Int, val title: String, val videoUrl: String)

@Serializable
internal data class ShortDrama(
    val id: String,
    val title: String,
    val summary: String,
    val genre: String,
    val poster: String,
    val totalEpisodes: Int,
    val licence: String,
    val permissionProofUrl: String,
    val verifiedFreeAdFreeDistribution: Boolean,
    val episodes: List<ShortEpisode>
) {
    fun completeAndAllowed(): Boolean =
        id.isNotBlank() && title.isNotBlank() &&
        verifiedFreeAdFreeDistribution &&
        licence.isNotBlank() && permissionProofUrl.startsWith("https://") &&
        totalEpisodes > 0 && episodes.size == totalEpisodes &&
        episodes.sortedBy { it.number }.map { it.number } == (1..totalEpisodes).toList() &&
        episodes.all { it.videoUrl.startsWith("https://") }
}

@Composable
fun ShortsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val series = remember(context) {
        runCatching {
            val manifest = context.assets.open("shorts_catalog.json").bufferedReader()
                .use { it.readText() }
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<ShortDrama>>(manifest)
                .filter(ShortDrama::completeAndAllowed)
                .distinctBy { it.id }
        }.getOrDefault(emptyList())
    }
    var playing by remember { mutableStateOf<ShortDrama?>(null) }

    if (playing != null) {
        ShortsPlayer(
            drama = playing!!,
            onClose = { playing = null }
        )
    }

    var section by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Assistir", "Descobrir", "Fontes").forEachIndexed { index, name ->
                androidx.compose.material3.FilterChip(
                    selected = section == index,
                    onClick = { section = index },
                    label = { Text(name, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        when (section) {
            0 -> ShortsVerifiedLibrary(series, onPlay = { playing = it },
                modifier = Modifier.weight(1f))
            1 -> ShortsDiscoverPanel(modifier = Modifier.weight(1f))
            else -> ShortsSourcesPanel(modifier = Modifier.weight(1f))
        }
    }
}


@Composable
private fun ShortsVerifiedLibrary(
    series: List<ShortDrama>,
    onPlay: (ShortDrama) -> Unit,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().padding(
                start = 20.dp, end = 20.dp, top = 34.dp, bottom = 20.dp
            ).clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(58.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Dramas curtos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Uma nova forma de assistir histórias em episódios verticais, no telemóvel.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "Episódios completos · Grátis · Sem anúncios",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
                Text(
                    "Ainda não há séries com todos os episódios e autorização de reprodução verificados. Não mostramos histórias pela metade nem episódios bloqueados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp, top = 12.dp, bottom = 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("Shorts", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
                Text(
                    "Dramas completos, gratuitos e sem anúncios",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(series, key = { it.id }) { item ->
                Surface(
                    onClick = { onPlay(item) },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.width(108.dp).height(162.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(item.genre, color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge)
                            Text(item.title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, maxLines = 3,
                                overflow = TextOverflow.Ellipsis)
                            Text(item.summary, style = MaterialTheme.typography.bodySmall,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("${item.totalEpisodes} episódios completos",
                                style = MaterialTheme.typography.labelMedium)
                            Icon(Icons.Default.PlayArrow, contentDescription = "Reproduzir")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsPlayer(drama: ShortDrama, onClose: () -> Unit) {
    val context = LocalContext.current
    var current by remember(drama.id) { mutableIntStateOf(0) }
    val player = remember(context, drama.id) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player, drama.id) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    current < drama.episodes.lastIndex) {
                    current++
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(current, drama.id) {
        val episode = drama.episodes.getOrNull(current) ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(episode.videoUrl))
        player.prepare()
        player.playWhenReady = true
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart)
                    .statusBarsPadding().padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(drama.title, style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "Episódio ${current + 1} de ${drama.totalEpisodes}",
                    color = Color.White.copy(alpha = 0.85f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { if (current > 0) current-- },
                        enabled = current > 0) {
                        Icon(Icons.Default.SkipPrevious, "Anterior", tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (current < drama.episodes.lastIndex) current++ else onClose()
                    }) {
                        Icon(Icons.Default.SkipNext, "Próximo", tint = Color.White)
                    }
                }
            }
        }
    }
}
