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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.saveable.rememberSaveable
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

    // Home is vertically scrollable as a whole. Its bottom content inset reserves the
    // space occupied by the floating navigation toolbar in AppNavigation.
    // System status bar is accounted for before the first title or filter.
    val recentFirst = remember(state.sections, category) {
        state.sections.values.flatten().distinctBy { it.sourceId }
            .filter { it.year != null }
            .sortedWith(compareByDescending<KeylessTitle> { it.year ?: 0 }
                .thenBy { it.title })
    }
    var sort by rememberSaveable { mutableStateOf("popular") }
    val searching = query.isNotBlank() && state.query.isNotBlank() && query == state.query
    val categoryItems = when (category) {
        "movie" -> state.sections["movie"].orEmpty()
        "series" -> state.sections["series"].orEmpty()
        "anime" -> state.sections["anime"].orEmpty()
        else -> state.sections.values.flatten().distinctBy { it.sourceId }
    }
    fun sorted(items: List<KeylessTitle>): List<KeylessTitle> = when (sort) {
        "recent" -> items.sortedWith(compareByDescending<KeylessTitle> { it.year ?: 0 }.thenBy { it.title })
        "alphabetical" -> items.sortedBy { it.title.lowercase() }
        else -> items
    }
    val sections = if (searching) {
        listOf("Resultados da pesquisa" to sorted(state.searchResults.filter {
            category == "all" || it.type == category
        }))
    } else when (category) {
        "movie" -> listOf(
            "Filmes populares" to sorted(state.sections["movie"].orEmpty()),
            "Filmes recentes" to state.sections["movie"].orEmpty().filter { it.year != null }
                .sortedByDescending { it.year }
        )
        "series" -> listOf(
            "Séries populares" to sorted(state.sections["series"].orEmpty()),
            "Séries recentes" to state.sections["series"].orEmpty().filter { it.year != null }
                .sortedByDescending { it.year }
        )
        "anime" -> listOf(
            "Animes em alta" to sorted(state.sections["anime"].orEmpty()),
            "Animes recentes" to state.sections["anime"].orEmpty().filter { it.year != null }
                .sortedByDescending { it.year }
        )
        else -> listOf(
            "Filmes populares" to sorted(state.sections["movie"].orEmpty()),
            "Séries para descobrir" to sorted(state.sections["series"].orEmpty()),
            "Animes em alta" to sorted(state.sections["anime"].orEmpty()),
            "Adicionados recentemente ao catálogo" to recentFirst.take(24)
        )
    }
    val wideLayout = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val posterWidth = if (wideLayout) 178.dp else 138.dp
    LazyColumn(
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 22.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "home-header") {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 22.dp, end = 16.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Vista Stream",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        "A tua biblioteca online",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar catálogo")
                }
                IconButton(onClick = onOpenSources) {
                    Icon(Icons.Default.Storefront, contentDescription = "Fontes e extensões")
                }
            }
        }
        item(key = "home-search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filmes, séries e animes") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.search(query) }
                ),
                trailingIcon = {
                    TextButton(onClick = { viewModel.search(query) }, enabled = query.isNotBlank()) {
                        Text("Buscar")
                    }
                }
            )
        }
        item(key = "home-categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(listOf(
                    "all" to "Tudo",
                    "movie" to "Filmes",
                    "series" to "Séries",
                    "anime" to "Animes"
                )) { (id, label) ->
                    FilterChip(
                        selected = category == id,
                        onClick = { category = id },
                        label = { Text(label) }
                    )
                }
            }
        }
        item(key = "home-sort") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Ordenar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("popular" to "Populares", "recent" to "Recentes", "alphabetical" to "A–Z")) { (id, label) ->
                        FilterChip(
                            selected = sort == id,
                            onClick = { sort = id },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        if (state.loading || state.searching) {
            item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
        }
        if (state.error != null) {
            item(key = "error") {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::refresh) { Text("Tentar novamente") }
                }
            }
        }
        if (!searching && category == "all" && !state.loading) {
            val featured = state.sections["movie"].orEmpty().firstOrNull {
                !it.background.isNullOrBlank() || !it.poster.isNullOrBlank()
            } ?: categoryItems.firstOrNull()
            if (featured != null) {
                item(key = "featured") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Em destaque", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("DESCUBRA", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .height(if (wideLayout) 320.dp else 235.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                                .clickable { viewModel.select(featured) }
                        ) {
                            AsyncImage(
                                model = featured.background ?: featured.poster,
                                contentDescription = featured.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        listOf(Color.Black.copy(alpha = 0.04f),
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Black.copy(alpha = 0.95f))
                                    )
                                )
                            )
                            Column(
                                Modifier.align(Alignment.BottomStart).padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(featured.title, color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(featured.year?.toString(), "Filme · Ver detalhes").joinToString("  •  "),
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
        sections.forEachIndexed { index, (heading, titles) ->
            if (titles.isNotEmpty()) {
                item(key = "rail-$index") {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 22.dp, end = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                heading, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (category == "all" && !searching && index < 3) {
                                val destination = listOf("movie", "series", "anime")[index]
                                TextButton(onClick = { category = destination }) { Text("Ver mais") }
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(titles.take(45), key = { it.sourceId }) { title ->
                                Column(
                                    modifier = Modifier.width(posterWidth)
                                        .clickable { viewModel.select(title) },
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    AsyncImage(
                                        model = title.poster ?: title.background,
                                        contentDescription = title.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                            .aspectRatio(0.69f)
                                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                    )
                                    Text(title.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelLarge, minLines = 2)
                                    Text(
                                        listOfNotNull(title.year?.toString(),
                                            when (title.type) {
                                                "movie" -> "Filme"
                                                "anime" -> "Anime"
                                                else -> "Série"
                                            }).joinToString(" · "),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (searching && state.searchResults.isEmpty() && !state.searching) {
            item(key = "search-empty") {
                Text(
                    "Nenhum título encontrado. Experimenta outro nome.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "end-spacer") {
            Text(
                "Catálogo atualizado a partir das fontes de metadados · Reprodução sujeita à disponibilidade",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val currentSeasonEpisodes = remember(episodes, season) { episodes.filter { it.season == season } }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
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
                enabled = servers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(9.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(when {
                    servers.isNotEmpty() && resumePositionMs > 15_000L -> "Continuar"
                    servers.isNotEmpty() -> "Assistir"
                    resolving -> "A procurar reprodução..."
                    else -> "Sem fonte disponível"
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
                var menuOpen by remember(title.sourceId, season, episode) { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Fontes disponíveis (${servers.size})")
                        Spacer(Modifier.weight(1f))
                        Text("▾")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        modifier = Modifier
                            .widthIn(min = 270.dp, max = 560.dp)
                            .heightIn(max = 340.dp)
                    ) {
                        servers.forEach { server ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            server.providerName,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "${server.name} · ${server.streamType}" +
                                                (server.audioLanguage?.let { " · $it" } ?: ""),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onPlay(server)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                }
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
            Spacer(Modifier.height(166.dp))
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
