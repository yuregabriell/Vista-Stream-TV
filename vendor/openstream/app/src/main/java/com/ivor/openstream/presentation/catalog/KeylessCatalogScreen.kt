package com.ivor.openstream.presentation.catalog

import android.net.Uri
import com.ivor.openstream.harvester.HarvesterShelf
import androidx.activity.compose.BackHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Pause
import com.ivor.openstream.presentation.player.components.CastDeviceSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
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


// Chromecast remote controls shared with the existing Media3 CastPlayer.
@Composable
private fun KeylessTvRemote(
    castViewModel: KeylessCastViewModel,
    title: String,
    subtitleOptions: List<com.ivor.openstream.presentation.player.session.CastSubtitleOption>,
    activeSubtitle: String?,
    sources: List<VideoServer>,
    onSelectSource: (VideoServer) -> Unit,
    onDismiss: () -> Unit
) {
    var showSources by remember { mutableStateOf(false) }
    var showSubtitles by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text("Assistindo na TV", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = {
                        val p = castViewModel.castPlayer
                        if (p != null) p.seekTo((p.currentPosition - 10000).coerceAtLeast(0))
                    }) { Text("−10s") }
                    Button(onClick = castViewModel::pauseOrResume) {
                        Text("Pausar / Reproduzir")
                    }
                    OutlinedButton(onClick = {
                        val p = castViewModel.castPlayer
                        if (p != null) p.seekTo(p.currentPosition + 10000)
                    }) { Text("+10s") }
                }
                if (sources.isNotEmpty()) {
                    TextButton(onClick = { showSources = true }) { Text("Alterar fonte") }
                }
                TextButton(onClick = { showSubtitles = true }) { Text("Legendas na TV") }
                TextButton(onClick = onDismiss) { Text("Voltar ao catálogo") }
            }
        }
    }
    if (showSources) AlertDialog(
        onDismissRequest = { showSources = false },
        title = { Text("Fonte de reprodução") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sources.forEach { server ->
                    TextButton(onClick = {
                        showSources = false
                        onSelectSource(server)
                    }) { Text(server.providerName + " · " + server.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSources = false }) { Text("Fechar") } }
    )
    if (showSubtitles) AlertDialog(
        onDismissRequest = { showSubtitles = false },
        title = { Text("Legendas na TV") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = {
                    castViewModel.selectSubtitle(null)
                    showSubtitles = false
                }) { Text("Desativadas") }
                subtitleOptions.forEach { option ->
                    TextButton(onClick = {
                        castViewModel.selectSubtitle(option.id)
                        showSubtitles = false
                    }) {
                        Text((if (activeSubtitle == option.id) "✓ " else "") + option.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showSubtitles = false }) { Text("Fechar") } }
    )
}

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
    viewModel: KeylessCatalogViewModel = hiltViewModel(),
    castViewModel: KeylessCastViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val castStatus by castViewModel.status.collectAsState()
    val tvNowPlaying by castViewModel.nowPlaying.collectAsState()
    val tvSubtitles by castViewModel.subtitles.collectAsState()
    var showCastPicker by remember { mutableStateOf(false) }
    var showTvControls by remember { mutableStateOf(false) }
    if (showCastPicker) {
        CastDeviceSheet(
            selector = castViewModel.routeSelector,
            status = castStatus,
            onStopCasting = castViewModel::stopCasting,
            onDismiss = { showCastPicker = false }
        )
    }
    if (showTvControls && castStatus.isCasting && tvNowPlaying != null) {
        KeylessTvRemote(
            castViewModel = castViewModel,
            title = tvNowPlaying!!.title,
            subtitleOptions = tvSubtitles.options,
            activeSubtitle = tvSubtitles.activeId,
            sources = state.servers,
            onSelectSource = viewModel::play,
            onDismiss = { showTvControls = false }
        )
    }
    var category by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var discover by rememberSaveable { mutableStateOf(false) }
    var genre by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFilterPanel by remember { mutableStateOf(false) }
    var showSortPanel by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var listOrigin by remember { mutableStateOf(Offset.Zero) }
    var searchBounds by remember { mutableStateOf(Rect.Zero) }
    var currentUtcDay by remember { mutableLongStateOf(System.currentTimeMillis() / 86_400_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60L * 60L * 1000L)
            currentUtcDay = System.currentTimeMillis() / 86_400_000L
        }
    }
    val chosen = state.selected
    val player = state.playing

    LaunchedEffect(player?.url, castStatus.isCasting) {
        val movie = state.selected
        val selectedVideo = player
        if (castStatus.isCasting && movie != null && selectedVideo != null) {
            castViewModel.cast(movie, selectedVideo, state.season, state.episode, state.resumePositionMs)
        }
    }
    if (player != null && castStatus.isCasting) {
        KeylessTvRemote(
            castViewModel = castViewModel,
            title = state.selected?.title ?: "Vídeo na TV",
            subtitleOptions = tvSubtitles.options,
            activeSubtitle = tvSubtitles.activeId,
            sources = state.servers,
            onSelectSource = viewModel::play,
            onDismiss = viewModel::closePlayer
        )
        return
    }

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
                onClose = viewModel::closePlayer,
                onCastClick = { showCastPicker = true }
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

    // Main home is editorial rails; a selected category/search opens a full
    // paginated catalogue with filters. An open title/player keeps its own state.
    val recentFirst = remember(state.sections) {
        state.sections.values.flatten().distinctBy { it.sourceId }
            .filter { it.year != null }
            .sortedWith(compareByDescending<KeylessTitle> { it.year ?: 0 }.thenBy { it.title })
    }
    var sort by rememberSaveable { mutableStateOf("popular") }
    val searching = query.isNotBlank() && state.query.isNotBlank() && query == state.query
    val browsing = discover || category != "all" || searching || searchFirst
    val browseType = if (category == "all") "all" else category
    LaunchedEffect(browsing, browseType, genre, year, searching, state.query) {
        if (browsing) viewModel.browse(
            browseType, if (searching) state.query else "", genre, year
        )
    }
    val categoryItems = state.sections.values.flatten().distinctBy { it.sourceId }
    val sections = if (!browsing) listOf(
        "Filmes populares" to state.sections["movie"].orEmpty(),
        "Séries para descobrir" to state.sections["series"].orEmpty(),
        "Animes em alta" to state.sections["anime"].orEmpty(),
        "Adicionados recentemente" to recentFirst.take(24)
    ) else emptyList()
    val continueWatching = if (!browsing) viewModel.continueWatching(categoryItems) else emptyList()
    val wideLayout = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val posterWidth = if (wideLayout) 178.dp else 138.dp
    val browseItems = remember(state.browseItems, sort) {
        when (sort) {
            "recent" -> state.browseItems.sortedWith(
                compareByDescending<KeylessTitle> { it.year ?: 0 }.thenBy { it.title }
            )
            "alphabetical" -> state.browseItems.sortedBy { it.title.lowercase() }
            else -> state.browseItems
        }
    }
    val gridColumns = if (wideLayout) 4 else 2
    val browseRows = remember(browseItems, gridColumns) { browseItems.chunked(gridColumns) }
    val catalogListState = rememberLazyListState()
    LaunchedEffect(browsing, browseType, genre, year, searching) {
        catalogListState.scrollToItem(0)
    }
    if (browsing) {
        BackHandler(enabled = !searchFirst) {
            category = "all"
            discover = false
            query = ""
            viewModel.search("")
            genre = ""
            year = null
            sort = "popular"
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    if (showFilterPanel && browsing) {
        VistaBrowseFiltersDialog(
            type = browseType, genre = genre, year = year,
            onDismiss = { showFilterPanel = false },
            onApply = { nextType, nextGenre, nextYear ->
                category = nextType
                discover = true
                genre = nextGenre
                year = nextYear
                showFilterPanel = false
            }
        )
    }
    if (showSortPanel && browsing) {
        VistaBrowseSortDialog(
            selected = sort,
            onDismiss = { showSortPanel = false },
            onApply = { next -> sort = next; showSortPanel = false }
        )
    }
    // Request the next page only near the end of the actual scroll viewport.
    // This avoids fetching the entire remote catalog immediately on first render.
    LaunchedEffect(browsing, state.browsePage, state.browseLoading, state.browseHasMore,
        state.browseError, state.browseType, state.browseGenre, state.browseYear,
        state.browseQuery) {
        if (browsing && !state.browseLoading && state.browseHasMore &&
            state.browseError == null && state.browseType.isNotBlank()) {
            snapshotFlow {
                val layout = catalogListState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                last to layout.totalItemsCount
            }.first { (last, total) -> total > 0 && last >= total - 4 }
            viewModel.loadMore()
        }
    }

    LazyColumn(
        state = catalogListState,
        modifier = modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .onGloballyPositioned { listOrigin = it.positionInWindow() }
            .pointerInput(searchBounds, listOrigin) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false, pass = PointerEventPass.Initial
                    )
                    if (!searchBounds.contains(down.position + listOrigin)) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    }
                }
            },
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
                IconButton(
                    onClick = { if (castStatus.isCasting) showTvControls = true else showCastPicker = true }
                ) {
                    Icon(
                        if (castStatus.isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = if (castStatus.isCasting) "Chromecast conectado" else "Conectar Chromecast",
                        tint = if (castStatus.isCasting) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        if (castStatus.isCasting && tvNowPlaying != null) {
            item(key = "cast-now-playing") {
                ElevatedCard(
                    onClick = { showTvControls = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CastConnected, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Assistindo agora na TV", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(tvNowPlaying!!.title, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            Text(castStatus.deviceName.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Abrir controles da TV")
                    }
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .onGloballyPositioned { searchBounds = it.boundsInWindow() },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.search(query); focusManager.clearFocus(); keyboard?.hide(); discover = false }
                ),
                trailingIcon = {
                    TextButton(onClick = { viewModel.search(query); focusManager.clearFocus(); keyboard?.hide(); discover = false }, enabled = query.isNotBlank()) {
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
                        onClick = {
                            category = id
                            discover = false
                            genre = ""
                            year = null
                            sort = "popular"
                            focusManager.clearFocus()
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
        if (browsing) {
            item(key = "catalog-navigation") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            category = "all"; discover = false
                            query = ""; viewModel.search("")
                            genre = ""; year = null; sort = "popular"
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Text("Início")
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.browseItems.size} títulos carregados",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        when {
                            searching -> "Resultados para \"$query\""
                            category == "movie" -> "Todos os filmes"
                            category == "series" -> "Todas as séries"
                            category == "anime" -> "Explorar animes"
                            else -> "Descobrir títulos"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showFilterPanel = true; focusManager.clearFocus(); keyboard?.hide() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("Filtros" + if (genre.isNotBlank() || year != null) " •" else "")
                        }
                        OutlinedButton(
                            onClick = { showSortPanel = true; focusManager.clearFocus(); keyboard?.hide() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Text("Ordenar")
                        }
                    }
                    if (genre.isNotBlank() || year != null) {
                        Text(
                            listOfNotNull(genre.takeIf { it.isNotBlank() }, year?.toString())
                                .joinToString("  •  "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
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
        if (!browsing && !state.loading) {
            val candidates = state.sections.values.flatten().distinctBy { it.sourceId }
                .filter { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
            val featured = candidates.takeIf { it.isNotEmpty() }?.let {
                it[Math.floorMod(currentUtcDay.toInt(), it.size)]
            }
            if (featured != null) {
                item(key = "featured") {
                    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Em destaque", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                discover = true
                                genre = ""
                                year = null
                                sort = "popular"
                                focusManager.clearFocus()
                            }) {
                                Text("Descubra  ›")
                            }
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
        if (!browsing) {
            item(key = "harvester-browser-library") {
                HarvesterShelf()
            }
        }
        if (!browsing && continueWatching.isEmpty()) {
            item(key = "continue-watching-empty") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Continuar a assistir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(
                        "Os vídeos com progresso guardado nesta instalação aparecem aqui.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching") {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Continuar a assistir",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 22.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.sourceId }) { title ->
                            Column(
                                Modifier.width(posterWidth).clickable { viewModel.select(title) },
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                AsyncImage(
                                    model = title.poster ?: title.background,
                                    contentDescription = title.title,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.69f),
                                    contentScale = ContentScale.Crop
                                )
                                Text(title.title, style = MaterialTheme.typography.labelLarge,
                                    maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "Retomar episódio ou filme",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
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
                            if (!browsing && index < 3) {
                                val destination = listOf("movie", "series", "anime")[index]
                                TextButton(onClick = { category = destination; genre = ""; year = null; sort = "popular"; focusManager.clearFocus() }) { Text("Ver mais  ›") }
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
        if (browsing) {
            itemsIndexed(browseRows, key = { index, _ -> "grid-row-$index" }) { _, titles ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    titles.forEach { title ->
                        Column(
                            modifier = Modifier.weight(1f).clickable {
                                focusManager.clearFocus()
                                viewModel.select(title)
                            },
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            AsyncImage(
                                model = title.poster ?: title.background,
                                contentDescription = title.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.69f)
                                    .background(MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(12.dp))
                            )
                            Text(title.title, maxLines = 2, minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge)
                            Text(
                                listOfNotNull(title.year?.toString(), when (title.type) {
                                    "movie" -> "Filme"
                                    "anime" -> "Anime"
                                    else -> "Série"
                                }).joinToString(" · "),
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    repeat(gridColumns - titles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.browseHasMore || state.browseLoading) {
                item(key = "browse-more") {
                    Column(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.browseLoading) CircularProgressIndicator()
                        else OutlinedButton(onClick = viewModel::loadMore) {
                            Text("Carregar mais títulos")
                        }
                    }
                }
            } else if (browseItems.isEmpty() && !state.loading) {
                item(key = "browse-empty") {
                    Text("Nenhum título encontrado para estes filtros.",
                        modifier = Modifier.padding(24.dp))
                }
            }
            if (state.browseError != null) {
                item(key = "browse-error") {
                    Column(Modifier.padding(20.dp)) {
                        Text(state.browseError.orEmpty(),
                            color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = viewModel::loadMore) { Text("Tentar novamente") }
                    }
                }
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
    onClose: () -> Unit,
    onCastClick: () -> Unit
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
        onClose = onClose,
        onCastClick = onCastClick
    )
}
