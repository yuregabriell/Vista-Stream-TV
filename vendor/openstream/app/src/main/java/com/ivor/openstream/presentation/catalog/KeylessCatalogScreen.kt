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
                resumePositionMs = state.resumePositionMs,
                onProgress = viewModel::savePlayback,
                onTryAlternative = { viewModel.nextSource(player) },
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
private fun KeylessOnlinePlayer(server: VideoServer, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var status by remember(server.id) { mutableIntStateOf(Player.STATE_IDLE) }
    var errorMessage by remember(server.id) { mutableStateOf<String?>(null) }
    var attempted by remember(server.id) { mutableIntStateOf(0) }
    val player = remember(server.id, server.url) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
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
                val media = MediaItem.Builder()
                    .setUri(server.url)
                    .setSubtitleConfigurations(subtitles)
                    .apply { if (mime != null) setMimeType(mime) }
                    .build()
                setMediaItem(media)
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguages("pt-BR", "pt-PT", "pt")
                    .setPreferredTextLanguages("pt-BR", "pt-PT", "pt")
                    .build()
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(next: Int) {
                status = next
                if (next == Player.STATE_READY) errorMessage = null
            }
            override fun onPlayerError(error: PlaybackException) {
                errorMessage = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "A fonte rejeitou o pedido HTTP. O link pode ter expirado."
                    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
                        "A fonte devolveu uma página web, não um vídeo reproduzível."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "Não foi possível receber o vídeo da fonte."
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                        "O formato devolvido pela fonte está inválido."
                    else -> "Erro no vídeo: " + error.errorCodeName
                }
            }
        }
        player.addListener(listener)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, status, attempted) {
        if (status == Player.STATE_BUFFERING || status == Player.STATE_IDLE) {
            delay(20_000)
            if (status != Player.STATE_READY && errorMessage == null) {
                errorMessage = "Esta fonte não começou a reproduzir. Experimenta outra fonte."
            }
        }
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3500
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> if (view.player !== player) view.player = player }
        )
        if (status == Player.STATE_BUFFERING && errorMessage == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("A carregar vídeo...", color = androidx.compose.ui.graphics.Color.White)
            }
        }
        errorMessage?.let { problem ->
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .padding(24.dp)
                    .background(
                        androidx.compose.ui.graphics.Color(0xEF10121A),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
                    )
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Não foi possível reproduzir", color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleLarge)
                Text(problem, color = androidx.compose.ui.graphics.Color.White)
                Button(onClick = {
                    errorMessage = null
                    status = Player.STATE_BUFFERING
                    attempted++
                    player.prepare()
                    player.playWhenReady = true
                }) { Text("Tentar novamente") }
                OutlinedButton(onClick = onClose) { Text("Escolher outra fonte") }
            }
        }
        FilledTonalIconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(24.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Fechar vídeo")
        }
    }
}
