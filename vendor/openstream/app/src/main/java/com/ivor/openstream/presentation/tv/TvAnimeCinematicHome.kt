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

    val rails = remember(animeItems) {
        listOf(
            "Em alta no mundo dos animes" to animeItems.take(35),
            "Novidades e estreias" to animeItems
                .filter { it.year != null }.sortedByDescending { it.year }.take(30),
            "Ação e aventura" to animeItems.filter {
                it.genres.any { g -> g.equals("Action", true) || g.equals("Adventure", true) }
            }.take(24),
            "Fantasia e outros mundos" to animeItems.filter {
                it.genres.any { g -> g.equals("Fantasy", true) || g.equals("Supernatural", true) }
            }.take(24),
            "Romance e emoções" to animeItems.filter {
                it.genres.any { g -> g.equals("Romance", true) || g.equals("Drama", true) }
            }.take(24),
            "Filmes de anime" to animeItems.filter { it.format.equals("MOVIE", true) }.take(24)
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val featureHeight = maxHeight * 0.50f
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
                    .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, bottom = 14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("ANIMES",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        TvPill("FILMES & SÉRIES", onClick = onOpenMovies)
                    }
                    Text(
                        shown.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 590.dp)
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
                            modifier = Modifier.widthIn(max = 600.dp)
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 54.dp),
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
