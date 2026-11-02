package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import com.ivor.openstream.data.catalog.KeylessEpisode
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.domain.model.VideoServer

/**
 * AniList fallback detail screen: same premium TV visual language as the main
 * TMDB route. Uses the real keyless source resolver (never invents a TMDB ID).
 * This is a graceful detail experience, not a dead-end matching error screen.
 */
@Composable
fun TvAnimeDetails(
    title: KeylessTitle,
    episodes: List<KeylessEpisode>,
    season: Int,
    episode: Int,
    servers: List<VideoServer>,
    resolving: Boolean,
    resolved: Boolean,
    providersChecked: Int,
    providersTotal: Int,
    resumePositionMs: Long,
    onBack: () -> Unit,
    onEpisode: (Int, Int) -> Unit,
    onWatch: () -> Unit,
    onPlay: (VideoServer) -> Unit
) {
    BackHandler(onBack = onBack)
    val initialFocus = remember(title.sourceId) { FocusRequester() }
    val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
    val episodeChoices = remember(episodes, season, episode) {
        episodes.filter { it.season == season }.ifEmpty {
            listOf(KeylessEpisode(season, episode, "Episódio $episode"))
        }
    }
    LaunchedEffect(title.sourceId) {
        // Back always remains available even if a provider returns no streams.
        runCatching { initialFocus.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        // Cover the same TV canvas as the main episode detail page. Prefer a
        // landscape image; an AniList cover is still usable as a visual fallback.
        TvBackdrop(
            url = title.background ?: title.poster,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f)
        )
        TvBackdropScrims()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "anime-premium-header") {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd,
                            top = 54.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "ANIME · VISTASTREAM",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        title.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                    Text(
                        listOfNotNull(
                            title.year?.toString(),
                            title.format?.replace('_', ' '),
                            "T$season · E$episode",
                            title.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() }
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (title.description.isNotBlank()) {
                        Text(
                            title.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 600.dp)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onWatch,
                            enabled = servers.isNotEmpty(),
                            modifier = Modifier.width(270.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            androidx.tv.material3.Text(
                                if (resumePositionMs > 15_000L) "Continuar"
                                else if (resolving) "A procurar fontes"
                                else "Reproduzir"
                            )
                        }
                        TvPill(
                            label = "Voltar aos animes",
                            onClick = onBack,
                            modifier = Modifier.focusRequester(initialFocus)
                        )
                    }
                    Column(
                        modifier = Modifier.widthIn(max = 470.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Episódios e temporadas · ${seasons.size.coerceAtLeast(1)} temporadas",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (resolving) "Fontes · A procurar $providersChecked/$providersTotal"
                                else "Fontes · ${servers.size} disponíveis",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    if (resolving) {
                        LinearProgressIndicator(Modifier.widthIn(max = 470.dp).fillMaxWidth())
                    } else if (resolved && servers.isEmpty()) {
                        Text(
                            "Ainda sem fontes compatíveis. Podes consultar episódios e voltar a procurar mais tarde.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (seasons.size > 1) {
                item(key = "anime-premium-seasons") {
                    TvRail(title = "Temporadas", start = TvLayout.PageStart) {
                        items(seasons, key = { it }) { number ->
                            TvPill(
                                label = "Temporada $number",
                                selected = season == number,
                                onClick = {
                                    val first = episodes.firstOrNull { it.season == number }
                                    onEpisode(number, first?.number ?: 1)
                                }
                            )
                        }
                    }
                }
            }

            item(key = "anime-premium-episodes") {
                TvRail("Episódios · Temporada $season", start = TvLayout.PageStart) {
                    items(episodeChoices, key = { "${it.season}:${it.number}" }) { item ->
                        TvLandscapeCard(
                            title = "E${item.number} · ${item.title}",
                            imageUrl = title.background ?: title.poster,
                            subtitle = if (item.number == episode) "Selecionado" else "Temporada ${item.season}",
                            width = 210.dp,
                            onClick = { onEpisode(item.season, item.number) }
                        )
                    }
                }
            }

            if (servers.isNotEmpty()) {
                item(key = "anime-premium-sources") {
                    TvRail("Fontes disponíveis", start = TvLayout.PageStart) {
                        itemsIndexed(servers, key = { index, _ -> "source-$index" }) { _, server ->
                            TvLandscapeCard(
                                title = server.providerName,
                                imageUrl = title.background ?: title.poster,
                                subtitle = listOfNotNull(
                                    server.name, server.streamType, server.audioLanguage
                                ).joinToString(" · "),
                                width = 240.dp,
                                onClick = { onPlay(server) }
                            )
                        }
                    }
                }
            }
        }
    }
}
