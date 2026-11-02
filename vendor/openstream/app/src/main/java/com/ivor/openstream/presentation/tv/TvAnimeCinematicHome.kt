package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivor.openstream.data.catalog.KeylessTitle

/**
 * Shares the cinematic layout of TV Films & Series: a full-bleed feature with
 * fixed hero height above lazily rendered horizontal rails. Genre navigation
 * remains one remote click away, directly under the hero.
 */
@Composable
internal fun TvAnimeCinematicHome(
    animeItems: List<KeylessTitle>,
    genres: List<Pair<String, String>>,
    editorialRails: Map<String, List<KeylessTitle>> = emptyMap(),
    onOpenTitle: (KeylessTitle) -> Unit,
    onGenre: (String) -> Unit,
    onOpenMovies: () -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = remember(animeItems) {
        animeItems.firstOrNull { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
            ?: animeItems.firstOrNull()
    } ?: return
    var highlighted by remember(animeItems) { mutableStateOf<KeylessTitle?>(null) }
    val shown = highlighted ?: featured
    val artworkViewModel: TvAnimeHeroArtworkViewModel = hiltViewModel()
    val sharpBackdrop by artworkViewModel.artwork.collectAsState()
    LaunchedEffect(shown.sourceId) { artworkViewModel.show(shown) }
    val heroAction = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { heroAction.requestFocus() } }

    // The rails are server-ranked TMDB queries (weekly trends, premieres,
    // genre-specific popularity). Anime lists no longer depend on a different
    // AniList catalog or scrape a few shared popular cards to invent rankings.
    val rails = remember(animeItems, editorialRails) {
        listOf(
            "Em alta no mundo dos animes",
            "Novidades e estreias",
            "Ação e aventura",
            "Fantasia e outros mundos",
            "Romance e emoções",
            "Filmes de anime"
        ).map { name -> name to editorialRails[name].orEmpty() }
            .filter { it.second.isNotEmpty() }
            .ifEmpty { listOf("Animes em destaque" to animeItems.take(30)) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // Reserve enough vertical space for the official title artwork, metadata
        // and primary action on a 960x540dp TV canvas.
        val featureHeight = maxHeight * 0.53f
        // Use the SAME full-bleed frame for every title: never switch to an
        // isolated small portrait when TMDB hasn't resolved the 16:9 artwork.
        // AniList's banner/cover remain only visual fallbacks, not separate layouts.
        TvBackdrop(
            url = sharpBackdrop ?: shown.background ?: shown.poster,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f)
                .align(Alignment.TopEnd)
        )
        TvBackdropScrims()

        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(featureHeight)
                    .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd,
                        top = 14.dp, bottom = 8.dp),
                // Bottom anchoring pushed tall logos ABOVE the TV viewport.
                // Keep the hero's title pinned visibly at the top on D-pad return.
                contentAlignment = Alignment.TopStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("ANIMES",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        TvPill("FILMES & SÉRIES", onClick = onOpenMovies)
                    }
                    // The title must ALWAYS be in composition. Its official
                    // TMDB logo can load asynchronously, with readable text as
                    // a fallback, but may never vanish when focus moves upward.
                    TvOfficialTitleLogo(
                        title = shown.title,
                        mediaType = if (shown.format.equals("MOVIE", ignoreCase = true))
                            "movie" else "tv",
                        titleId = shown.tmdbId ?: 0,
                        compact = true
                    )
                    Text(
                        listOfNotNull(
                            shown.year?.toString(),
                            shown.format?.takeIf { it.isNotBlank() },
                            shown.genres.take(2).joinToString(" · ").takeIf { it.isNotBlank() }
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (shown.description.isNotBlank()) {
                        Text(
                            shown.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 590.dp)
                        )
                    }
                    // Match the Films & Series behavior: the featured action is
                    // visible only before a card takes focus. Focused cards update
                    // the hero details but don't retain a misleading featured CTA.
                    val actionAlpha by animateFloatAsState(
                        targetValue = if (highlighted == null) 1f else 0f,
                        label = "tvAnimeFeaturedAction"
                    )
                    TvPill(
                        label = "Ver anime e episódios",
                        icon = Icons.Rounded.Info,
                        onClick = { onOpenTitle(featured) },
                        modifier = Modifier.alpha(actionAlpha)
                            .focusRequester(heroAction)
                            .onFocusChanged { if (it.hasFocus) highlighted = null }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 62.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "anime-genres") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvRowHeader("Explorar por género")
                        LazyRow(
                            contentPadding = PaddingValues(
                                start = TvLayout.ContentStart,
                                end = TvLayout.ContentEnd,
                                top = 4.dp,
                                bottom = 6.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(genres, key = { it.first }) { (code, name) ->
                                TvPill(name, onClick = { onGenre(code) },
                                    modifier = Modifier.onFocusChanged {
                                        if (it.hasFocus) highlighted = null
                                    })
                            }
                        }
                    }
                }
                rails.forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item(key = "rail-$title") {
                            TvRail(title = title) {
                                items(list, key = { it.sourceId }) { anime ->
                                    TvCaptionedPoster(
                                        title = anime.title,
                                        posterPath = anime.poster ?: anime.background,
                                        caption = anime.year?.toString() ?: "Anime",
                                        posterWidth = 94.dp,
                                        posterHeight = 141.dp,
                                        onClick = { onOpenTitle(anime) },
                                        modifier = Modifier.onFocusChanged {
                                            if (it.hasFocus) highlighted = anime
                                            else if (highlighted?.sourceId == anime.sourceId) highlighted = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "anime-browse-footer") {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(start = TvLayout.ContentStart, top = 22.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("À procura de mais animes?",
                            style = MaterialTheme.typography.titleLarge)
                        TvPill("EXPLORAR TODO O CATÁLOGO DE ANIMES", onClick = onBrowseAll,
                            modifier = Modifier.onFocusChanged {
                                if (it.hasFocus) highlighted = null
                            })
                    }
                }
            }
        }
    }
}
