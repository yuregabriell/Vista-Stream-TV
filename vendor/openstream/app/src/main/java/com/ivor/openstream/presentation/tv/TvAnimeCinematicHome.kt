package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    onSearch: (String) -> Unit,
    onOpenMovies: () -> Unit,
    onBrowseAll: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = remember(animeItems) {
        animeItems.firstOrNull { !it.background.isNullOrBlank() || !it.poster.isNullOrBlank() }
            ?: animeItems.firstOrNull()
    } ?: return
    var highlighted by remember(animeItems) { mutableStateOf<KeylessTitle?>(null) }
    val shown = highlighted ?: featured
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
        val featureHeight = maxHeight * 0.48f
        TvBackdrop(
            url = shown.background ?: shown.poster,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.78f).align(Alignment.TopEnd)
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
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 600.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvPill(
                            label = "Ver anime e episódios",
                            onClick = { onOpenTitle(shown) },
                            modifier = Modifier.focusRequester(heroAction)
                        )
                        TvPill("Atualizar", onClick = onRefresh)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 54.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "anime-search") {
                    TvDpadTextInput(
                        value = "",
                        placeholder = "Pesquisar animes por nome",
                        title = "Pesquisar animes",
                        onSubmit = onSearch,
                        modifier = Modifier
                            .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd)
                            .widthIn(max = 580.dp)
                    )
                }
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
                                TvPill(name, onClick = { onGenre(code) })
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
                        TvPill("EXPLORAR TODO O CATÁLOGO DE ANIMES", onClick = onBrowseAll)
                    }
                }
            }
        }
    }
}
