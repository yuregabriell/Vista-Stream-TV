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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.presentation.details.DetailsViewModel
import com.ivor.openstream.data.local.entity.TitleRatingEntity
import com.ivor.openstream.presentation.player.PlayerViewModel
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.components.SUBTITLES_OFF
import com.ivor.openstream.presentation.player.sourceSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** TV episode detail: metadata and pre-resolved sources without starting an audio/video session. */
@Composable
fun TvEpisodeDetailsScreen(
    mediaType: String,
    animeId: Int,
    season: Int,
    episode: Int,
    onPlay: () -> Unit,
    /** OK on an episode thumbnail should play that episode immediately. */
    onPlayEpisode: (Int, Int) -> Unit,
    /** Selecting a season changes the preview, without starting playback. */
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
    val episodeProgress by detailsViewModel.episodeProgress.collectAsState()
    var activePanel by remember { mutableStateOf<TvEpisodePanel?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

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
    val episodeRailState = rememberLazyListState()
    val seasonRailState = rememberLazyListState()
    LaunchedEffect(episodes, season, episode) {
        val index = episodes.indexOfFirst { it.episodeNumber == episode }
        if (index >= 0) episodeRailState.scrollToItem(index)
    }
    val appContext = LocalContext.current
    var pendingFutureEpisode by remember(season, episode) { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingDate by remember(season, episode) { mutableStateOf<String?>(null) }
    var doNotWarn by remember(mediaType, animeId) {
        mutableStateOf(TvReleaseWarningPreferences.isSuppressed(appContext, mediaType, animeId))
    }
    fun beginPlayback(selectedSeason: Int, selectedEpisode: Int, airDate: String?, play: () -> Unit) {
        if (!isUpcomingEpisode(airDate) || doNotWarn) {
            play()
        } else {
            pendingFutureEpisode = selectedSeason to selectedEpisode
            pendingDate = airDate
        }
    }
    val scope = rememberCoroutineScope()
    val title = details
    if (title == null) {
        TvLoading()
        return
    }
    val otherSeasons = title.seasons.orEmpty().filter { it.seasonNumber > 0 }
    val allSeasonCount = title.numberOfSeasons?.takeIf { it > 0 }
        ?: otherSeasons.size.takeIf { it > 0 } ?: 1
    val allEpisodeCount = title.numberOfEpisodes?.takeIf { it > 0 }
        ?: otherSeasons.sumOf { it.episodeCount }.takeIf { it > 0 }
        ?: episodes.size
    LaunchedEffect(otherSeasons, season) {
        val index = otherSeasons.indexOfFirst { it.seasonNumber == season }
        if (index >= 0) seasonRailState.scrollToItem(index)
    }
    LaunchedEffect(title.id, season, episode) {
        delay(90)
        runCatching { focus.requestFocus() }
    }
    val inContinueWatching = episodeProgress.values.any { !it.completed }
    val subtitleSummary = when (preferredSubtitle) {
        null -> "Automático"
        SUBTITLES_OFF -> "Desligadas"
        else -> preferredSubtitle?.let(::tvEpisodeLanguageName) ?: "Automático"
    }
    Box(Modifier.fillMaxSize()) {
        TvBackdrop((currentEpisode?.stillPath ?: title.backdropPath ?: title.posterPath)
            ?.let { "$TV_BACKDROP_BASE$it" })
        TvBackdropScrims()
        Column(Modifier.fillMaxSize()) {
            // A fixed breadcrumb never scrolls off or gets clipped when focus returns
            // to an earlier row after navigating lower in the page.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.68f))
                    .padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (mediaType == "movie") "Filme" else "T$season · E$episode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 70.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "hero") {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (mediaType == "movie") title.name
                            else currentEpisode?.name?.takeIf { it.isNotBlank() }
                                ?: "Temporada $season · Episódio $episode",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 570.dp)
                        )
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
                            Text(
                                overview,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 600.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                beginPlayback(season, episode, currentEpisode?.airDate, onPlay)
                            },
                            modifier = Modifier.width(294.dp).focusRequester(focus),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            androidx.tv.material3.Text(
                                if (isUpcomingEpisode(currentEpisode?.airDate)) "Verificar reprodução"
                                else "Reproduzir"
                            )
                        }

                        // Netflix-style compact actions; focused TV icons still have a clear
                        // visible ring and accessible descriptions for screen readers.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvEpisodeIconAction(
                                icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label = if (isSaved) "Remover da lista" else "Guardar na lista",
                                active = isSaved,
                                onClick = { detailsViewModel.toggleWatchLaterFor(title) }
                            )
                            TvEpisodeIconAction(
                                icon = Icons.Default.ThumbUp,
                                label = "Gostei",
                                active = rating == TitleRatingEntity.LIKE,
                                onClick = { detailsViewModel.rate(TitleRatingEntity.LIKE) }
                            )
                            TvEpisodeIconAction(
                                icon = Icons.Default.ThumbDown,
                                label = "Não gostei",
                                active = rating == TitleRatingEntity.DISLIKE,
                                onClick = { detailsViewModel.rate(TitleRatingEntity.DISLIKE) }
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.widthIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (mediaType != "movie") {
                                TvEpisodeMenuAction(
                                    icon = Icons.Default.VideoLibrary,
                                    label = "Episódios e temporadas",
                                    supporting = "$allSeasonCount temporadas · $allEpisodeCount episódios",
                                    onClick = {
                                        scope.launch {
                                            // Hero + optional season heading and season rail precede episodes.
                                            listState.animateScrollToItem(
                                                if (otherSeasons.size > 1) 3 else 1
                                            )
                                        }
                                    }
                                )
                            }
                            TvEpisodeMenuAction(
                                icon = Icons.Default.Dns,
                                label = "Fontes",
                                supporting = "${servers.size} disponíveis" +
                                    if (serversState is ServersState.Resolving) " · A procurar" else "",
                                onClick = { activePanel = TvEpisodePanel.SOURCES }
                            )
                            TvEpisodeMenuAction(
                                icon = Icons.Default.ClosedCaption,
                                label = "Áudio e legendas",
                                supporting = subtitleSummary,
                                onClick = { activePanel = TvEpisodePanel.SUBTITLES }
                            )
                            if (inContinueWatching) {
                                TvEpisodeMenuAction(
                                    icon = Icons.Default.Close,
                                    label = "Remover de Continuar assistindo",
                                    supporting = "Manter o histórico dos episódios concluídos",
                                    onClick = { confirmRemove = true }
                                )
                            }
                        }
                    }
                }
            if (mediaType != "movie" && otherSeasons.size > 1) {
                item(key = "season-heading") {
                    TvRowHeader("Temporadas", start = TvLayout.PageStart)
                }
                item(key = "seasons") {
                    LazyRow(
                        state = seasonRailState,
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
                    Column(Modifier.padding(start = TvLayout.PageStart)) {
                        TvRowHeader("Episódios · Temporada $season", start = 0.dp)
                        Text("Carrega OK num episódio para começar a assistir",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "episode-rail") {
                    LazyRow(
                        state = episodeRailState,
                        contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                        horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap)
                    ) {
                        items(episodes, key = { it.episodeNumber }) { item ->
                            TvLandscapeCard(
                                title = "E" + item.episodeNumber + " · " + item.name,
                                subtitle = listOfNotNull(
                                    item.airDate?.takeIf { it.isNotBlank() },
                                    if (isUpcomingEpisode(item.airDate)) "Data de estreia por confirmar"
                                    else if (item.episodeNumber == episode) "Selecionado · OK para reproduzir"
                                    else "OK para reproduzir"
                                ).joinToString("  ·  "),
                                imageUrl = item.stillPath?.let { "$TV_STILL_BASE$it" },
                                onClick = {
                                    beginPlayback(season, item.episodeNumber, item.airDate) {
                                        onPlayEpisode(season, item.episodeNumber)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
        }
        TvEpisodeOptionsPanel(
            panel = activePanel,
            sources = servers,
            serversState = serversState,
            selectedServerId = selectedServer?.id,
            subtitleLanguages = languages,
            preferredSubtitle = preferredSubtitle,
            onChooseServer = playerViewModel::selectServer,
            onRetry = playerViewModel::retryResolution,
            onChooseLanguage = playerViewModel::setPreferredSubtitleLanguage,
            onAutomaticLanguage = playerViewModel::resetPreferredSubtitleLanguage,
            onDismiss = { activePanel = null }
        )
        if (confirmRemove) {
            TvConfirmPanel(
                title = "Remover de Continuar assistindo?",
                message = "O título deixa a fila, mas os episódios já vistos continuam marcados.",
                confirmLabel = "Remover",
                onConfirm = {
                    detailsViewModel.dismissFromContinueWatching()
                    confirmRemove = false
                },
                onCancel = { confirmRemove = false }
            )
        }
        pendingFutureEpisode?.let { selected ->
            TvReleaseConflictDialog(
                airDate = pendingDate,
                availableSources = if (selected.first == season && selected.second == episode) servers.size else 0,
                doNotWarn = doNotWarn,
                onToggleDoNotWarn = { checked ->
                    doNotWarn = checked
                    TvReleaseWarningPreferences.setSuppressed(appContext, mediaType, animeId, checked)
                },
                onBack = {
                    pendingFutureEpisode = null
                    pendingDate = null
                },
                onContinue = {
                    pendingFutureEpisode = null
                    pendingDate = null
                    if (selected.first == season && selected.second == episode) onPlay()
                    else onPlayEpisode(selected.first, selected.second)
                }
            )
        }
    }
}

internal fun isUpcomingEpisode(airDate: String?, today: LocalDate = LocalDate.now()): Boolean =
    airDate?.take(10)?.let { value ->
        runCatching { LocalDate.parse(value).isAfter(today) }.getOrDefault(false)
    } ?: false

@Composable
private fun TvEpisodeIconAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    if (active) {
        androidx.tv.material3.IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp))
        }
    } else {
        androidx.tv.material3.OutlinedIconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun TvEpisodeMenuAction(
    icon: ImageVector,
    label: String,
    supporting: String,
    onClick: () -> Unit
) {
    TvOutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.material3.Icon(
            icon, contentDescription = null,
            tint = androidx.compose.material3.LocalContentColor.current,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            androidx.compose.material3.Text(
                label,
                color = androidx.compose.material3.LocalContentColor.current,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.compose.material3.Text(
                supporting,
                color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Icon(
            Icons.Default.ChevronRight, contentDescription = null,
            tint = androidx.compose.material3.LocalContentColor.current,
            modifier = Modifier.size(20.dp)
        )
    }
}
