package com.ivor.openstream.presentation.catalog

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
        KeylessOnlinePlayer(player, onClose = viewModel::closePlayer)
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
            onBack = viewModel::closeDetails,
            onEpisode = viewModel::chooseEpisode,
            onResolve = viewModel::findStreams,
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
    onBack: () -> Unit,
    onEpisode: (Int, Int) -> Unit,
    onResolve: () -> Unit,
    onPlay: (VideoServer) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
            Text(" Voltar à biblioteca")
        }
        Text(title.title, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = title.poster,
                contentDescription = null,
                modifier = Modifier.width(120.dp).height(180.dp),
                contentScale = ContentScale.Crop
            )
            Column {
                Text(listOfNotNull(title.year?.toString(), if (title.type == "anime") "Anime" else title.type).joinToString(" · "))
                Spacer(Modifier.height(8.dp))
                Text(title.description.ifBlank { "Sem sinopse disponível." }, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
        if (title.type != "movie") {
            Text("Episódios", style = MaterialTheme.typography.titleMedium)
            if (episodes.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(episodes.take(250)) { ep ->
                        FilterChip(
                            selected = season == ep.season && episode == ep.number,
                            onClick = { onEpisode(ep.season, ep.number) },
                            label = { Text("T${ep.season} E${ep.number}") }
                        )
                    }
                }
            } else {
                Text("Temporada $season · Episódio $episode — seleção inicial; catálogo sem lista detalhada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onResolve, enabled = !resolving) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text(if (resolving) " A procurar streams online..." else " Encontrar fontes online")
        }
        if (resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (resolved && servers.isEmpty()) {
            Text(
                "Nenhuma extensão instalada ofereceu um vídeo HTTP compatível para este título. " +
                "Adiciona uma fonte Stremio com streams diretos, ou escolhe outro título.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        servers.forEach { server ->
            OutlinedButton(onClick = { onPlay(server) }, modifier = Modifier.fillMaxWidth()) {
                Text("${server.providerName} · ${server.name} · ${server.streamType}", maxLines = 2)
            }
        }
    }
}

@Composable
private fun KeylessOnlinePlayer(server: VideoServer, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val player = remember(server.id, server.url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(server.headers)
        val dataSource = DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .build().apply {
                val mime = server.mimeType ?: when {
                    server.isHls -> MimeTypes.APPLICATION_M3U8
                    server.url.substringBefore('?').endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                    else -> null
                }
                val subtitles = server.subtitles.mapNotNull { item ->
                    runCatching {
                        val type = when {
                            item.url.substringBefore('?').endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
                            item.url.substringBefore('?').endsWith(".ass", true) -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.TEXT_VTT
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(item.url))
                            .setMimeType(type)
                            .setLanguage(item.language ?: "pt")
                            .build()
                    }.getOrNull()
                }
                val media = MediaItem.Builder().setUri(server.url)
                    .setSubtitleConfigurations(subtitles)
                    .apply { if (mime != null) setMimeType(mime) }
                    .build()
                setMediaItem(media)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("pt-BR", "pt-PT", "pt")
                    .setPreferredTextLanguages("pt-BR", "pt-PT", "pt")
                    .build()
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxSize()
        )
        FilledTonalIconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Fechar vídeo")
        }
    }
}
