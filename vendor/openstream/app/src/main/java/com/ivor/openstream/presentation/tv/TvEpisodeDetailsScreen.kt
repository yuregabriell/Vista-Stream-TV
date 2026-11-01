package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.presentation.details.DetailsViewModel
import com.ivor.openstream.presentation.player.PlayerViewModel
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.components.SUBTITLES_OFF
import com.ivor.openstream.presentation.player.sourceSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** TV episode detail: metadata and pre-resolved sources without starting an audio/video session. */
@Composable
fun TvEpisodeDetailsScreen(
    mediaType: String,
    animeId: Int,
    season: Int,
    episode: Int,
    onPlay: () -> Unit,
    onChooseEpisode: (Int, Int) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    detailsViewModel: DetailsViewModel = hiltViewModel()
) {
    val details by playerViewModel.mediaDetails.collectAsState()
    val currentEpisode by playerViewModel.currentEpisode.collectAsState()
    val episodes by playerViewModel.seasonEpisodes.collectAsState()
    val serversState by playerViewModel.serversState.collectAsState()
    val selectedServer by playerViewModel.activeServer.collectAsState()
    val subtitles by playerViewModel.remoteSubtitles.collectAsState()
    val preferredSubtitle by playerViewModel.preferredSubtitleLanguage.collectAsState()
    val isSaved by detailsViewModel.isWatchLater.collectAsState()
    val rating by detailsViewModel.rating.collectAsState()

    LaunchedEffect(mediaType, animeId, season, episode) {
        playerViewModel.loadSeasonDetails(mediaType, animeId, season, episode, resolveStreams = true)
    }
    val servers = when (val s = serversState) {
        is ServersState.Ready -> s.servers
        is ServersState.Resolving -> s.servers
        else -> emptyList()
    }
    val languages = remember(subtitles, servers) {
        (subtitles.mapNotNull { it.language } +
            servers.flatMap { it.subtitles.mapNotNull { s -> s.language } })
            .filter { it.isNotBlank() }.distinct().sorted()
    }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val title = details
    if (title == null) {
        TvLoading()
        return
    }
    val otherSeasons = title.seasons.orEmpty().filter { it.seasonNumber > 0 }
    LaunchedEffect(title.id, season, episode) {
        delay(90)
        runCatching { focus.requestFocus() }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        TvBackdrop((currentEpisode?.stillPath ?: title.backdropPath ?: title.posterPath)
            ?.let { "$TV_BACKDROP_BASE$it" })
        TvBackdropScrims()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "hero") {
                Box(
                    Modifier.fillMaxWidth().height(maxHeight - 28.dp)
                        .padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(Modifier.width(590.dp)) {
                        Text("VISTASTREAM  ·  " + if (mediaType == "movie") "FILME" else "EPISÓDIO",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(title.name, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (mediaType == "movie") title.name else currentEpisode?.name
                                ?.takeIf { it.isNotBlank() } ?: "Temporada $season · Episódio $episode",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(
                                if (mediaType == "movie") "Filme" else "T$season · E$episode",
                                currentEpisode?.airDate?.takeIf { it.isNotBlank() }
                                    ?: title.date.takeIf { it.isNotBlank() },
                                currentEpisode?.runtime?.let { "$it min" },
                                currentEpisode?.voteAverage?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }
                            ).joinToString("  ·  "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val overview = currentEpisode?.overview?.takeIf { it.isNotBlank() } ?: title.overview
                        if (overview.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(overview, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onPlay, modifier = Modifier.focusRequester(focus),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                androidx.tv.material3.Text("Reproduzir")
                            }
                            OutlinedButton(onClick = detailsViewModel::toggleWatchLater) {
                                Icon(if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                androidx.tv.material3.Text(if (isSaved) "Guardado" else "Guardar")
                            }
                            OutlinedButton(onClick = { detailsViewModel.rate(10) }) {
                                Icon(Icons.Default.ThumbUp, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                androidx.tv.material3.Text(if (rating == 10) "Gostei ✓" else "Gostei")
                            }
                            OutlinedButton(onClick = { detailsViewModel.rate(1) }) {
                                Icon(Icons.Default.ThumbDown, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                androidx.tv.material3.Text(if (rating == 1) "Não gostei ✓" else "Não gostei")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { scope.launch { listState.animateScrollToItem(1) } }) {
                                Icon(Icons.Default.Dns, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                androidx.tv.material3.Text("Fontes: " + servers.size +
                                    if (serversState is ServersState.Resolving) " · a procurar" else "")
                            }
                            OutlinedButton(onClick = { scope.launch { listState.animateScrollToItem(3) } }) {
                                Icon(Icons.Default.ClosedCaption, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                androidx.tv.material3.Text("Legendas")
                            }
                            if (mediaType != "movie") {
                                OutlinedButton(onClick = {
                                    scope.launch { listState.animateScrollToItem(if (otherSeasons.size > 1) 7 else 5) }
                                }) {
                                    androidx.tv.material3.Text("Episódios")
                                }
                            }
                        }
                    }
                }
            }
            item(key = "source-heading") {
                Column(Modifier.padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd)) {
                    TvRowHeader("Fontes disponíveis (" + servers.size + ")", start = 0.dp)
                    Text(
                        when {
                            serversState is ServersState.Resolving ->
                                "A procurar mais fontes. As encontradas já podem ser selecionadas."
                            servers.isEmpty() -> "Nenhuma fonte encontrada nesta pesquisa."
                            else -> "Escolhe a fonte preferida antes de iniciar o episódio."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = playerViewModel::retryResolution) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        androidx.tv.material3.Text("Procurar mais")
                    }
                }
            }
            item(key = "sources") {
                LazyRow(
                    contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        val label = server.name.take(30) + " · " + server.sourceSummary()
                        if (server.id == selectedServer?.id) {
                            Button(onClick = { playerViewModel.selectServer(server.id) }) {
                                androidx.tv.material3.Text("✓ $label", maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = { playerViewModel.selectServer(server.id) }) {
                                androidx.tv.material3.Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }
            item(key = "subtitle-heading") {
                Column(Modifier.padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd)) {
                    TvRowHeader("Áudio e legendas", start = 0.dp)
                    Text("Preferência de legenda: " + when (preferredSubtitle) {
                        null -> "Automático"
                        SUBTITLES_OFF -> "Desligadas"
                        else -> preferredSubtitle
                    }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "subtitle-options") {
                LazyRow(
                    contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "none") {
                        OutlinedButton(onClick = { playerViewModel.setPreferredSubtitleLanguage(SUBTITLES_OFF) }) {
                            androidx.tv.material3.Text("Desligadas" + if (preferredSubtitle == SUBTITLES_OFF) " ✓" else "")
                        }
                    }
                    items(languages, key = { it }) { language ->
                        OutlinedButton(onClick = { playerViewModel.setPreferredSubtitleLanguage(language) }) {
                            androidx.tv.material3.Text(language.uppercase() + if (preferredSubtitle == language) " ✓" else "")
                        }
                    }
                    if (languages.isEmpty()) item(key = "no-results") {
                        Text("A aguardar opções de legendas",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (mediaType != "movie" && otherSeasons.size > 1) {
                item(key = "season-heading") {
                    TvRowHeader("Temporadas", start = TvLayout.PageStart)
                }
                item(key = "seasons") {
                    LazyRow(
                        contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(otherSeasons, key = { it.seasonNumber }) { item ->
                            TvPill(
                                label = "Temporada " + item.seasonNumber,
                                selected = item.seasonNumber == season,
                                onClick = { onChooseEpisode(item.seasonNumber, 1) }
                            )
                        }
                    }
                }
            }
            if (mediaType != "movie") {
                item(key = "episode-heading") {
                    TvRowHeader("Episódios · Temporada $season", start = TvLayout.PageStart)
                }
                item(key = "episode-rail") {
                    LazyRow(
                        contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                        horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap)
                    ) {
                        items(episodes, key = { it.episodeNumber }) { item ->
                            TvLandscapeCard(
                                title = "E" + item.episodeNumber + " · " + item.name,
                                subtitle = item.airDate?.takeIf { it.isNotBlank() },
                                imageUrl = item.stillPath?.let { "$TV_STILL_BASE$it" },
                                onClick = { onChooseEpisode(season, item.episodeNumber) }
                            )
                        }
                    }
                }
            }
        }
    }
}
