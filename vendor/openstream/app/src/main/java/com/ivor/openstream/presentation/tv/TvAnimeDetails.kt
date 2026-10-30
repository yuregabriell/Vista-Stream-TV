package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import coil3.compose.AsyncImage
import com.ivor.openstream.data.catalog.KeylessEpisode
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.domain.model.VideoServer

/**
 * Remote-first, full-width anime details. Uses the SAME actual keyless
 * episode/source/playback callbacks as the phone; no duplicated player state.
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
    val detailFocus = remember(title.sourceId) { FocusRequester() }
    LaunchedEffect(title.sourceId) {
        runCatching { detailFocus.requestFocus() }
    }
    val seasons = episodes.map { it.season }.distinct().sorted()
    val episodeChoices = episodes.filter { it.season == season }.ifEmpty {
        listOf(KeylessEpisode(season, episode, "Episódio $episode"))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "anime-title") {
            Box(
                Modifier.fillMaxWidth()
                    .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, top = 20.dp)
                    .height(250.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                AsyncImage(
                    model = title.background ?: title.poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.99f),
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                        1f to Color.Transparent
                    )
                ))
                Column(
                    modifier = Modifier.align(Alignment.CenterStart).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("ANIME  /  VISTASTREAM", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(title.title, style = MaterialTheme.typography.headlineLarge,
                        color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(520.dp))
                    Text(
                        listOfNotNull(
                            title.year?.toString(),
                            title.format?.replace('_',' '),
                            title.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() }
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = onWatch, enabled = servers.isNotEmpty()) {
                            androidx.tv.material3.Text(
                                when {
                                    resumePositionMs > 15_000L -> "▶  Continuar a assistir"
                                    servers.isNotEmpty() -> "▶  Assistir"
                                    resolving -> "A procurar fontes..."
                                    else -> "Sem fonte disponível"
                                }
                            )
                        }
                        TvPill("Voltar aos animes", onClick = onBack, modifier = Modifier.focusRequester(detailFocus))
                    }
                }
            }
        }

        item(key = "anime-description") {
            Column(Modifier.fillMaxWidth().padding(
                start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    title.description.ifBlank { "Sinopse não disponível." },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        resolving -> "A verificar fontes: $providersChecked/$providersTotal"
                        servers.isEmpty() && resolved ->
                            "Nenhuma fonte compatível disponível neste momento. O catálogo não garante reprodução."
                        servers.isNotEmpty() -> "${servers.size} fontes disponíveis para este episódio."
                        else -> "A carregar episódios e fontes de reprodução…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (resolving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        if (seasons.size > 1) {
            item(key = "anime-seasons") {
                TvRail(title = "Temporadas") {
                    items(seasons, key = { it }) { n ->
                        TvPill(
                            "Temporada $n",
                            selected = season == n,
                            onClick = {
                                episodes.firstOrNull { it.season == n }?.let { first ->
                                    onEpisode(n, first.number)
                                }
                            }
                        )
                    }
                }
            }
        }

        item(key = "anime-episodes") {
            TvRail("Episódios") {
                items(episodeChoices, key = { "${it.season}:${it.number}" }) { ep ->
                    TvLandscapeCard(
                        title = "E${ep.number} · ${ep.title}",
                        imageUrl = title.background ?: title.poster,
                        subtitle = if (ep.season == season && ep.number == episode)
                            "Selecionado" else "Temporada ${ep.season}",
                        width = 210.dp,
                        onClick = { onEpisode(ep.season, ep.number) }
                    )
                }
            }
        }

        if (servers.isNotEmpty()) {
            item(key = "anime-sources") {
                TvRail("Fontes disponíveis") {
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
