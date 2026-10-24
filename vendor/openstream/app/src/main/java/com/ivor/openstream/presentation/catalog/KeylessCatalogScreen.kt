package com.ivor.openstream.presentation.catalog

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.domain.model.VideoServer

/**
 * Independent, keyless title discovery. Metadata does NOT imply a stream exists:
 * only enabled compatible providers can supply playable HTTP media.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeylessCatalogScreen(
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit = {},
    searchFirst: Boolean = false,
    viewModel: KeylessCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var category by remember { mutableStateOf(if (searchFirst) "all" else "all") }
    var query by remember { mutableStateOf("") }
    val chosen = state.selected
    val player = state.playing

    if (player != null) {
        // Full-window dialog lives above the parent navigation scaffold. The Home
        // bottom bar and its tabs can no longer cover the seek bar or player buttons.
        Dialog(
            onDismissRequest = viewModel::closePlayer,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            KeylessOnlinePlayer(
                server = player,
                title = state.selected?.title ?: "",
                episodeLabel = if (state.selected?.type == "movie") "" else "T${state.season} · E${state.episode}",
                resumePositionMs = state.resumePositionMs,
                onProgress = viewModel::savePlayback,
                onTryAlternative = { viewModel.nextSource(player) },
                availableServers = state.servers,
                onSelectSource = viewModel::play,
                hasAlternative = state.servers.any { it.id != player.id && it.url != player.url },
                onClose = viewModel::closePlayer
            )
        }
        return
    }
    if (chosen != null) {
        KeylessTitleDetails(
            title = chosen,
            episodes = state.episodes,
            season = state.season,
            episode = state.episode,
            servers = state.servers,
            resolving = state.resolving,
            resolved = state.resolveFinished,
            providersChecked = state.resolvedProviders,
            providersTotal = state.totalProviders,
            failedProviders = state.failedProviders,
            onBack = viewModel::closeDetails,
            onEpisode = viewModel::chooseEpisode,
            resumePositionMs = state.savedProgressMs,
            onWatch = viewModel::playFirst,
            onPlay = viewModel::play
        )
        return
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Vista Stream", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::refresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar catálogo")
            }
            IconButton(onClick = onOpenSources) {
                Icon(Icons.Default.Storefront, contentDescription = "Instalar fontes Stremio")
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Filmes, séries e animes") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(onClick = { viewModel.search(query) }, enabled = query.isNotBlank()) {
                    Text("Buscar")
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((id,label) in listOf("all" to "Tudo", "movie" to "Filmes", "series" to "Séries", "anime" to "Animes")) {
                item {
                    FilterChip(selected = category == id, onClick = { category = id }, label = { Text(label) })
                }
            }
        }
        Text(
            "Catálogo sem chave API · reprodução online quando houver fonte compatível",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        if (state.loading || state.searching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.error != null) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = viewModel::refresh) { Text("Tentar novamente") }
        }
        val sections = if (query.isNotBlank() && state.query.isNotBlank()) {
            mapOf("Resultados" to state.searchResults.filter { category == "all" || it.type == category })
        } else {
            when (category) {
                "all" -> linkedMapOf(
                    "Filmes populares" to state.sections["movie"].orEmpty(),
                    "Séries populares" to state.sections["series"].orEmpty(),
                    "Animes em destaque" to state.sections["anime"].orEmpty()
                )
                else -> mapOf((if (category == "movie") "Filmes" else if (category == "series") "Séries" else "Animes") to state.sections[category].orEmpty())
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 132.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            sections.forEach { (heading, titles) ->
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                }
                items(titles, key = { it.sourceId }) { title ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.select(title) },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AsyncImage(
                            model = title.poster,
                            contentDescription = title.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                        )
                        Text(title.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                        Text(title.year?.toString().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun KeylessTitleDetails(
    title: KeylessTitle,
    episodes: List<com.ivor.openstream.data.catalog.KeylessEpisode>,
    season: Int,
    episode: Int,
    servers: List<VideoServer>,
    resolving: Boolean,
    resolved: Boolean,
    providersChecked: Int,
    providersTotal: Int,
    failedProviders: List<String>,
    resumePositionMs: Long,
    onBack: () -> Unit,
    onEpisode: (Int, Int) -> Unit,
    onWatch: () -> Unit,
    onPlay: (VideoServer) -> Unit
) {
    BackHandler(onBack = onBack)
    var sourcesExpanded by remember(title.sourceId) { mutableStateOf(false) }
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val currentSeasonEpisodes = remember(episodes, season) { episodes.filter { it.season == season } }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(208.dp)) {
            AsyncImage(
                model = title.background ?: title.poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.96f)))
                )
            )
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                Text(" Biblioteca", color = Color.White)
            }
            Text(
                title.title,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                listOfNotNull(
                    title.year?.toString(),
                    when (title.type) { "movie" -> "Filme"; "anime" -> "Anime"; else -> "Série" },
                    if (title.type != "movie") "Temporada $season · Episódio $episode" else null
                ).joinToString("  •  "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                title.description.ifBlank { "Sinopse não disponível." },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 7,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = onWatch,
                enabled = servers.isNotEmpty() && !resolving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(9.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(when {
                    resolving -> "A procurar reprodução..."
                    servers.isEmpty() -> "Sem fonte disponível"
                    resumePositionMs > 15_000L -> "Continuar"
                    else -> "Assistir"
                }, fontWeight = FontWeight.Bold)
            }
            if (resolving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                if (resolving || resolved)
                    "Fontes verificadas: $providersChecked/$providersTotal · Streams: ${servers.size}"
                else "A preparar fontes online...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (resolved && servers.isEmpty()) {
                Text(
                    "Nenhum vídeo compatível encontrado. O catálogo informa quais títulos existem, " +
                        "mas não garante disponibilidade em cada extensão.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (servers.isNotEmpty()) {
                OutlinedButton(
                    onClick = { sourcesExpanded = !sourcesExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fontes disponíveis (${servers.size})  " + if (sourcesExpanded) "⌃" else "⌄")
                }
                if (sourcesExpanded) {
                    servers.forEach { server ->
                        OutlinedButton(onClick = { onPlay(server) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "${server.providerName} · ${server.name} · ${server.streamType}",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            if (title.type != "movie") {
                HorizontalDivider()
                Text("Episódios", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (seasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons) { n ->
                            FilterChip(
                                selected = season == n,
                                onClick = {
                                    episodes.firstOrNull { it.season == n }?.let { onEpisode(n, it.number) }
                                },
                                label = { Text("Temporada $n") }
                            )
                        }
                    }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(currentSeasonEpisodes.ifEmpty {
                        listOf(com.ivor.openstream.data.catalog.KeylessEpisode(season, episode, "Episódio $episode"))
                    }, key = { "${it.season}:${it.number}" }) { ep ->
                        val selected = ep.season == season && ep.number == episode
                        Column(
                            modifier = Modifier.width(190.dp)
                                .clickable { onEpisode(ep.season, ep.number) }
                                .background(
                                    if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(12.dp)
                                ).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().height(106.dp)) {
                                AsyncImage(
                                    model = title.background ?: title.poster,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Episódio selecionado",
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.Center)
                                            .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(50))
                                            .padding(6.dp)
                                    )
                                }
                            }
                            Text(
                                "${ep.number}. ${ep.title}",
                                maxLines = 2,
                                style = MaterialTheme.typography.labelMedium,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeylessOnlinePlayer(
    server: VideoServer,
    title: String,
    episodeLabel: String,
    resumePositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    availableServers: List<VideoServer>,
    onSelectSource: (VideoServer) -> Unit,
    hasAlternative: Boolean,
    onTryAlternative: () -> Unit,
    onClose: () -> Unit
) {
    VistaFullscreenPlayer(
        server = server,
        title = title,
        episodeLabel = episodeLabel,
        resumePositionMs = resumePositionMs,
        onProgress = onProgress,
        availableServers = availableServers,
        onSelectSource = onSelectSource,
        hasAlternative = hasAlternative,
        onTryAlternative = onTryAlternative,
        onClose = onClose
    )
}
