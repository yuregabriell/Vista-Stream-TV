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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.presentation.catalog.KeylessCatalogViewModel
import com.ivor.openstream.presentation.catalog.KeylessCatalogScreen

/**
 * Dedicated anime experience for Android TV, backed by the same AniList / public
 * catalog and player as the phone anime home. Works without a TMDB key.
 *
 * The first screen is TV-native: D-pad rails, genres, and a searchable 10-foot
 * catalog. The existing keyless Details + Media3 playback remain shared, so
 * favorites, sources and watch-memory are not copied or lost.
 */
// The content root already starts after the permanent 84dp TV navigation rail.
// Extra internal safe space is needed for focused cards/buttons (1.06x scale).
private val AnimeSafeStart = 52.dp

private val TV_ANIME_GENRES = listOf(
    "Action" to "Ação",
    "Adventure" to "Aventura",
    "Comedy" to "Comédia",
    "Drama" to "Drama",
    "Fantasy" to "Fantasia",
    "Romance" to "Romance",
    "Sci-Fi" to "Ficção científica",
    "Mystery" to "Mistério",
    "Horror" to "Terror",
    "Slice of Life" to "Slice of Life",
    "Sports" to "Desporto",
    "Supernatural" to "Sobrenatural",
    "Music" to "Música"
)

@Composable
fun TvAnimeScreen(
    onOpenSources: () -> Unit = {},
    onOpenMovies: () -> Unit = {},
    initialGenre: String = "",
    initialQuery: String = "",
    initialBrowse: Boolean = false,
    modifier: Modifier = Modifier,
    onOpenUnifiedTitle: (String, Int) -> Unit,
    viewModel: KeylessCatalogViewModel = hiltViewModel(),
    titleNavigation: TvAnimeTitleNavigationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var genre by rememberSaveable(initialGenre, initialQuery) { mutableStateOf(initialGenre) }
    var showingAll by rememberSaveable(initialBrowse, initialGenre, initialQuery) {
        mutableStateOf(initialBrowse && initialGenre.isBlank() && initialQuery.isBlank())
    }
    var searchText by rememberSaveable(initialGenre, initialQuery) { mutableStateOf(initialQuery) }
    var submittedQuery by rememberSaveable(initialGenre, initialQuery) { mutableStateOf(initialQuery) }
    var resolvingTitle by remember { mutableStateOf<String?>(null) }
    var fallbackTitle by remember { mutableStateOf<KeylessTitle?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val firstAction = remember { FocusRequester() }

    // Anime cards now use the same TMDB-backed details, seasons, sources and
    // player as global search. AniList IDs are never passed as TMDB IDs.
    fun openAnime(item: KeylessTitle) {
        if (resolvingTitle != null) return
        fallbackTitle = null
        resolvingTitle = item.title
        scope.launch {
            val route = titleNavigation.resolve(item)
            resolvingTitle = null
            if (route != null) onOpenUnifiedTitle(route.first, route.second)
            else fallbackTitle = item
        }
    }

    fun browseGenre(value: String) {
        showingAll = false
        genre = value
        searchText = ""
        submittedQuery = ""
    }

    fun searchAnime() {
        showingAll = false
        genre = ""
        submittedQuery = searchText.trim()
        keyboard?.hide()
    }

    val browsing = showingAll || genre.isNotBlank() || submittedQuery.isNotBlank()
    LaunchedEffect(genre, submittedQuery, showingAll) {
        if (browsing) {
            viewModel.browse("anime", query = submittedQuery, genre = genre)
        }
    }
    // Refresh automatically when stale, including if the TV remains on this page
    // for hours. The repository has a 3h cache; this check itself does no network
    // request until the cache expires.
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refreshIfStale()
            kotlinx.coroutines.delay(15L * 60 * 1000)
        }
    }
    // Home manages its own hero focus. Entering browse focuses the genre strip.
    LaunchedEffect(browsing) {
        if (browsing) runCatching { firstAction.requestFocus() }
    }

    BackHandler(enabled = browsing) { browseGenre("") }
    if (resolvingTitle != null) {
        TvLoading(modifier.fillMaxSize())
        return
    }
    if (fallbackTitle != null) {
        // A missing TMDB match must NOT block an AniList title. Keep the
        // existing keyless source resolver and episode information available.
        // This fallback uses the same TV-native player instead of an error page.
        KeylessCatalogScreen(
            modifier = modifier,
            initialTitle = fallbackTitle,
            onInitialDismiss = { fallbackTitle = null },
            onOpenSources = onOpenSources,
            viewModel = viewModel
        )
        return
    }

    // The TV anime Home shares the exact same cinematic structure as Movies:
    // backdrop hero + dedicated genre row + horizontal rails + browse footer.
    val homeAnimes = state.sections["anime"].orEmpty()
    if (!browsing && homeAnimes.isNotEmpty()) {
        TvAnimeCinematicHome(
            animeItems = homeAnimes,
            genres = TV_ANIME_GENRES,
            onOpenTitle = ::openAnime,
            onGenre = ::browseGenre,
            onOpenMovies = onOpenMovies,
            onBrowseAll = {
                showingAll = true
                genre = ""
                searchText = ""
                submittedQuery = ""
            },
            modifier = modifier
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Genre-first TV experience: no manual refresh or duplicate search field.
        LazyRow(
            contentPadding = PaddingValues(
                start = AnimeSafeStart,
                end = TvLayout.ContentEnd,
                top = 24.dp,
                bottom = 20.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "anime-genre-all") {
                TvPill(
                    label = "Todos",
                    selected = genre.isBlank() && submittedQuery.isBlank(),
                    onClick = {
                        showingAll = true
                        genre = ""
                        searchText = ""
                        submittedQuery = ""
                    },
                    modifier = Modifier.focusRequester(firstAction)
                )
            }
            lazyItems(TV_ANIME_GENRES, key = { it.first }) { (code, label) ->
                TvPill(
                    label = label,
                    selected = genre == code,
                    onClick = { browseGenre(code) }
                )
            }
        }
        if (submittedQuery.isNotBlank()) {
            Text(
                text = "Resultados para: $submittedQuery",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = AnimeSafeStart, bottom = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (browsing) {
            val filtered = state.browseItems.filter { it.type == "anime" }
            val waiting = state.browseType != "anime" ||
                state.browseGenre != genre || state.browseQuery != submittedQuery
            val gridState = rememberLazyGridState()
            LaunchedEffect(genre, submittedQuery) { gridState.scrollToItem(0) }

            if (state.browseLoading && filtered.isEmpty() || waiting) {
                TvLoading(Modifier.weight(1f))
            } else if (state.browseError != null && filtered.isEmpty()) {
                TvAnimeMessage(
                    message = state.browseError ?: "Não foi possível carregar os animes.",
                    action = "Tentar novamente",
                    onAction = viewModel::loadMore,
                    modifier = Modifier.weight(1f)
                )
            } else if (filtered.isEmpty()) {
                TvAnimeMessage(
                    message = "Nenhum anime encontrado. Experimenta outro género ou pesquisa.",
                    action = "Ver todos os animes",
                    onAction = { browseGenre("") },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 185.dp),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = AnimeSafeStart,
                        end = TvLayout.ContentEnd,
                        top = 12.dp,
                        bottom = 52.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(filtered, key = { it.sourceId }) { anime ->
                        TvCaptionedPoster(
                            title = anime.title,
                            posterPath = anime.poster ?: anime.background,
                            caption = listOfNotNull(anime.year?.toString(), anime.format)
                                .joinToString(" · ").ifBlank { "Anime" },
                            onClick = { openAnime(anime) }
                        )
                    }
                    if (state.browseLoading || state.browseHasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.browseLoading) {
                                    androidx.compose.material3.CircularProgressIndicator()
                                } else {
                                    TvPill("Carregar mais animes", onClick = viewModel::loadMore)
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        val animeItems = state.sections["anime"].orEmpty()
        if (state.loading && animeItems.isEmpty()) {
            TvLoading(Modifier.weight(1f))
        } else if (animeItems.isEmpty()) {
            TvAnimeMessage(
                message = state.error ?: "Não foi possível carregar o AniList. Verifica a Internet.",
                action = "Tentar novamente",
                onAction = viewModel::refresh,
                modifier = Modifier.weight(1f)
            )
        } else {
            val featured = animeItems.firstOrNull {
                !it.background.isNullOrBlank() || !it.poster.isNullOrBlank()
            }
            val railData = listOf(
                "Em alta no mundo dos animes" to animeItems.take(35),
                "Novidades e estreias" to animeItems
                    .filter { it.year != null }
                    .sortedByDescending { it.year }.take(30),
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 70.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (featured != null) {
                    item(key = "anime-featured") {
                        TvAnimeHero(featured) { openAnime(featured) }
                    }
                }
                railData.forEach { (title, titles) ->
                    if (titles.isNotEmpty()) {
                        item(key = title) {
                            TvRail(title = title, start = AnimeSafeStart) {
                                lazyItems(titles, key = { it.sourceId }) { anime ->
                                    TvCaptionedPoster(
                                        title = anime.title,
                                        posterPath = anime.poster ?: anime.background,
                                        caption = anime.year?.toString() ?: "Anime",
                                        onClick = { openAnime(anime) }
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "browse-all-anime") {
                    Box(
                        Modifier.fillMaxWidth().padding(start = AnimeSafeStart, top = 8.dp)
                    ) {
                        TvPill("Explorar todo o catálogo de animes", onClick = {
                            // Display the paged AniList catalog rather than only Home previews.
                            showingAll = true
                            genre = ""
                            searchText = ""
                            submittedQuery = ""
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAnimeHero(anime: KeylessTitle, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = AnimeSafeStart, end = TvLayout.ContentEnd)
            .height(235.dp)
            .clip(RoundedCornerShape(18.dp))
    ) {
        val image = anime.background ?: anime.poster
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    0.45f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                    1f to Color.Transparent
                )
            )
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(25.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ANIME EM DESTAQUE",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold)
            Text(
                anime.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(450.dp)
            )
            Text(
                listOfNotNull(anime.year?.toString(), anime.genres.take(2).joinToString(" · ")
                    .takeIf { it.isNotBlank() }).joinToString(" · "),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            TvPill(label = "Ver anime e episódios", onClick = onClick)
        }
    }
}

@Composable
private fun TvAnimeMessage(
    message: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        TvPill(action, onClick = onAction)
    }
}
