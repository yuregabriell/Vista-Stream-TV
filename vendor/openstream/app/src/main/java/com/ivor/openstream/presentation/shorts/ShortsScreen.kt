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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ShortEpisode(
    val number: Int,
    val title: String,
    val videoUrl: String
)

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
    val episodes: List<ShortEpisode>,
    val backdrop: String? = null,
    val sourceName: String = "Biblioteca",
    val kind: String = "serie"
) {
    fun completeAndAllowed(): Boolean =
        id.isNotBlank() && title.isNotBlank() &&
        verifiedFreeAdFreeDistribution &&
        licence.isNotBlank() && permissionProofUrl.startsWith("https://") &&
        totalEpisodes > 0 && episodes.size == totalEpisodes &&
        episodes.sortedBy { it.number }.map { it.number } == (1..totalEpisodes).toList() &&
        episodes.all { it.videoUrl.startsWith("https://") }
}

/**
 * Single premium mobile screen: catalog -> details -> ExoPlayer. No separate
 * "sources", "discovery" or browser link screens. Only preflighted playable,
 * complete, freely redistributable videos enter the catalog.
 */
@Composable
fun ShortsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val curated = remember(context) {
        runCatching {
            val file = context.assets.open("shorts_catalog.json")
                .bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }.decodeFromString<List<ShortDrama>>(file)
                .filter { it.completeAndAllowed() }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    val categories = listOf(
        "Dramas" to "short drama",
        "Romance" to "romance short film",
        "Fantasia" to "fantasy short film",
        "Ação" to "action short film",
        "Curtas" to "short film"
    )
    var category by rememberSaveable { mutableStateOf("Dramas") }
    var typing by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("short drama") }
    var page by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<ShortDrama>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }
    var moreAvailable by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<ShortDrama?>(null) }
    var playing by remember { mutableStateOf<ShortDrama?>(null) }
    var startingEpisode by remember { mutableIntStateOf(0) }

    fun setSearch(next: String) {
        search = next
        page = 0
        results = emptyList()
        moreAvailable = true
        loadError = false
    }

    LaunchedEffect(search, page) {
        isLoading = true
        loadError = false
        val data = runCatching { ShortsCatalogRepository.page(search, page) }
        if (data.isSuccess) {
            val next = data.getOrDefault(emptyList())
            results = if (page == 0) next else
                (results + next).distinctBy { it.id }
            // Some search pages are filtered by licensing and availability.
            // Offer more pages even when one page yields zero playable works.
            moreAvailable = page < 160
        } else {
            loadError = true
        }
        isLoading = false
    }

    if (playing != null) {
        ShortsPlayer(
            drama = playing!!,
            startIndex = startingEpisode,
            onClose = { playing = null }
        )
    }

    BackHandler(enabled = selected != null && playing == null) {
        selected = null
    }

    val catalog = remember(curated, results, search, category) {
        val seeds = if (search.isBlank() || search == "short drama") curated else emptyList()
        (seeds + results).filter { it.completeAndAllowed() }.distinctBy { it.id }
    }

    if (selected != null) {
        ShortsDetails(
            drama = selected!!,
            modifier = modifier.fillMaxSize(),
            onBack = { selected = null },
            onPlay = { index ->
                startingEpisode = index
                playing = selected
            }
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
            contentPadding = PaddingValues(
                start = 18.dp, end = 18.dp, top = 10.dp, bottom = 115.dp
            )
        ) {
            item {
                Text(
                    "Dramas Curtos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Histórias para assistir no VistaStream",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = typing,
                        onValueChange = { typing = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Pesquisar curtas") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                    FilledIconButton(
                        onClick = { setSearch(typing.trim().ifBlank { "short drama" }) }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Pesquisar")
                    }
                }
            }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories, key = { it.first }) { option ->
                        FilterChip(
                            selected = category == option.first,
                            onClick = {
                                category = option.first
                                typing = ""
                                setSearch(option.second)
                            },
                            label = { Text(option.first) }
                        )
                    }
                }
            }

            if (catalog.isNotEmpty()) {
                item {
                    ShortsFeatured(
                        drama = catalog.first(),
                        onClick = { selected = catalog.first() }
                    )
                }
                item {
                    Text(
                        "Explorar histórias",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(catalog.chunked(2), key = { it.joinToString("-") { item -> item.id } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { entry ->
                            ShortsPoster(
                                drama = entry,
                                onClick = { selected = entry },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            if (isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "A procurar títulos e verificar a reprodução…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else if (loadError) {
                item {
                    Text("Não foi possível consultar o catálogo. Verifica a ligação.",
                        color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        // Trigger a new search even if the page stays the same.
                        page = 0
                        search = search + " "
                    }) { Text("Tentar novamente") }
                }
            } else if (catalog.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Nenhuma obra reproduzível nesta pesquisa",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Só entram títulos com vídeo direto e licença aberta verificados. " +
                                "Não mostramos links externos, trailers ou episódios pagos como completos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (!isLoading && moreAvailable && !loadError) {
                item {
                    OutlinedButton(
                        onClick = { page += ShortsCatalogRepository.PAGE_SIZE },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Carregar mais títulos")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsFeatured(drama: ShortDrama, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(345.dp)
            .clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = drama.backdrop?.takeIf { it.isNotBlank() } ?: drama.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.10f),
                    0.38f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.92f)
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                if (drama.kind == "curta") "CURTA COMPLETO" else
                    drama.totalEpisodes.toString() + " EPISÓDIOS",
                color = Color(0xFFB5C5FF),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                drama.title, color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Text(
                drama.sourceName + " · " + drama.genre,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium
            )
            FilledTonalButton(onClick = onClick) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Ver título")
            }
        }
    }
}

@Composable
private fun ShortsPoster(
    drama: ShortDrama,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.72f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = drama.poster.ifBlank { drama.backdrop },
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(7.dp),
                color = Color.Black.copy(alpha = 0.76f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    if (drama.kind == "curta") "Completo" else
                        drama.totalEpisodes.toString() + " EP",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            drama.title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            drama.sourceName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShortsDetails(
    drama: ShortDrama,
    modifier: Modifier,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(460.dp)) {
                AsyncImage(
                    model = drama.backdrop?.takeIf { it.isNotBlank() } ?: drama.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.40f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background
                    )
                ))
                FilledIconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                }
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (drama.kind == "curta") "CURTA COMPLETO"
                        else drama.totalEpisodes.toString() + " EPISÓDIOS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        drama.title, style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black, maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        drama.genre + " · " + drama.sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = { onPlay(0) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reproduzir", style = MaterialTheme.typography.titleMedium)
                }
                if (drama.summary.isNotBlank()) {
                    Text(
                        drama.summary, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Fonte: " + drama.sourceName + " · " + drama.licence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (drama.kind == "curta") "Vídeo completo" else "Episódios",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        items(drama.episodes.sortedBy { it.number }, key = { it.number }) { episode ->
            Surface(
                onClick = { onPlay((episode.number - 1).coerceAtLeast(0)) },
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 18.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text(
                            episode.title.ifBlank { "Episódio " + episode.number },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Disponível para reprodução",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortsPlayer(
    drama: ShortDrama,
    startIndex: Int,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var current by remember(drama.id, startIndex) { mutableIntStateOf(startIndex) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val player = remember(context, drama.id) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player, drama.id) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "Não foi possível reproduzir este vídeo."
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    current < drama.episodes.lastIndex) current++
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
        playbackError = null
        val builder = MediaItem.Builder().setUri(episode.videoUrl)
        if (episode.videoUrl.contains(".m3u8", ignoreCase = true)) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            FilledIconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopStart)
                    .statusBarsPadding().padding(12.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
            }
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = 18.dp, end = 18.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (playbackError != null) {
                    Text(playbackError!!, color = Color.White)
                    OutlinedButton(onClick = {
                        val uri = drama.episodes[current].videoUrl
                        player.setMediaItem(MediaItem.fromUri(uri))
                        player.prepare()
                        player.playWhenReady = true
                        playbackError = null
                    }) {
                        Text("Tentar novamente")
                    }
                }
                Text(
                    drama.title, style = MaterialTheme.typography.titleMedium,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Parte " + (current + 1) + " de " + drama.totalEpisodes,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { if (current > 0) current-- }, enabled = current > 0) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior",
                            tint = Color.White)
                    }
                    IconButton(onClick = {
                        if (current < drama.episodes.lastIndex) current++ else onClose()
                    }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Próximo",
                            tint = Color.White)
                    }
                }
            }
        }
    }
}
