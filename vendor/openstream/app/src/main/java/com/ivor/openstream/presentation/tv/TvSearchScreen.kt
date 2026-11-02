package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import com.ivor.openstream.R
import com.ivor.openstream.presentation.catalog.KeylessCatalogScreen
import com.ivor.openstream.presentation.catalog.KeylessCatalogViewModel
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.BrowseGenre
import com.ivor.openstream.presentation.search.SearchViewModel
import com.ivor.openstream.presentation.search.SearchFilter

/** Letters, then digits: six to a row so the keyboard fits a narrow column. */
private val KEYBOARD_KEYS: List<List<Char>> = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890").toList().chunked(6)
private val KEY_SIZE: Dp = 40.dp
private val KEY_GAP: Dp = 6.dp

/**
 * Search the way TV apps do it: a narrow keyboard column on the left and the results filling the
 * rest of the screen, updating as letters are added (Android TV has no keyboard for a text field to
 * open). Before anything is typed, the right side offers recent searches and genres.
 * Opening a result remembers the query as a recent search.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TvSearchScreen(
    onTitleClick: (animeId: Int, mediaType: String) -> Unit,
    onAnimeBrowse: (genre: String, query: String) -> Unit = { _, _ -> },
    onOpenPlatform: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    onOpenSources: () -> Unit = {},
    keylessViewModel: KeylessCatalogViewModel = hiltViewModel(),
    viewModel: SearchViewModel = hiltViewModel(),
    providersViewModel: TvProvidersViewModel = hiltViewModel()
) {
    if (!keylessViewModel.hasTmdbKey) {
        KeylessCatalogScreen(modifier = modifier, onOpenSources = onOpenSources, searchFirst = true, viewModel = keylessViewModel)
        return
    }
    val platformsEnabled by providersViewModel.enabled.collectAsState()
    val platforms by providersViewModel.providerDirectory.collectAsState()
    LaunchedEffect(platformsEnabled) {
        if (platformsEnabled) providersViewModel.loadDirectory()
    }
    val state by viewModel.uiState.collectAsState()
    val query = state.query
    val firstKey = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // After navigating back to Search, focus is requested only once the
    // on-screen D-pad keyboard has actually been laid out.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstKey.requestFocus() }
    }

    fun type(next: String) = viewModel.onQueryChange(next)
    val open: (AnimeDto) -> Unit = { item ->
        if (query.isNotBlank()) viewModel.submit(query)
        onTitleClick(item.id, item.mediaType ?: if (item.isMovie) "movie" else "tv")
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 6
        }
    }
    val results = state.visibleResults
    LaunchedEffect(nearEnd, results.size) {
        if (nearEnd && results.isNotEmpty()) viewModel.loadMore()
    }

    Row(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // The keyboard column.
        Column(
            Modifier
                .padding(start = TvLayout.ContentStart, top = 36.dp, end = 24.dp)
                .width(KEY_SIZE * 6 + KEY_GAP * 5)
                .fillMaxHeight()
        ) {
            QueryLine(query = query, genreLabel = state.genre?.let { stringResource(it.labelRes) })
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.height(8.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(KEY_GAP),
                modifier = Modifier.focusRestorer(firstKey)
            ) {
                KEYBOARD_KEYS.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                        row.forEachIndexed { column, key ->
                            Key(
                                width = KEY_SIZE,
                                label = key.toString(),
                                modifier = if (rowIndex == 0 && column == 0) Modifier.focusRequester(firstKey) else Modifier,
                                onClick = { type(query + key.lowercaseChar()) }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                    Key(
                        width = KEY_SIZE * 2 + KEY_GAP,
                        icon = Icons.Default.SpaceBar,
                        contentDescription = stringResource(R.string.tv_key_space),
                        onClick = { if (query.isNotEmpty() && !query.endsWith(' ')) type("$query ") }
                    )
                    Key(
                        width = KEY_SIZE * 2 + KEY_GAP,
                        icon = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = stringResource(R.string.pin_backspace),
                        onClick = { if (query.isNotEmpty()) type(query.dropLast(1)) }
                    )
                    Key(
                        width = KEY_SIZE * 2 + KEY_GAP,
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_clear_search),
                        onClick = viewModel::clear
                    )
                }
            }
        }

        // Results, or something to browse before anything is typed.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = TvLayout.ContentEnd)
        ) {
            val typeOptions = listOf("Todos", "Anime", "Filmes", "Séries")
            val typeValues = listOf(SearchFilter.ALL, SearchFilter.ANIME,
                SearchFilter.MOVIES, SearchFilter.SERIES)
            val genreOptions = listOf("Todos os géneros") +
                BrowseGenre.entries.filterNot { it.isAnime }.map { it.label }
            val chosenGenre = state.genre
            val platformOptions = listOf("Todas as plataformas") +
                if (platformsEnabled) platforms.map { it.providerName } else emptyList()
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvFilterDropdown(
                    label = "Plataforma",
                    selection = "Todas",
                    options = platformOptions,
                    selectedIndex = 0,
                    onSelect = { index ->
                        if (index > 0) platforms.getOrNull(index - 1)?.let {
                            onOpenPlatform(it.providerId)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                TvFilterDropdown(
                    label = "Tipo",
                    selection = typeOptions[typeValues.indexOf(state.filter).coerceAtLeast(0)],
                    options = typeOptions,
                    selectedIndex = typeValues.indexOf(state.filter).coerceAtLeast(0),
                    onSelect = { index ->
                        typeValues.getOrNull(index)?.let(viewModel::onFilterSelected)
                    },
                    modifier = Modifier.weight(1f)
                )
                TvFilterDropdown(
                    label = "Género",
                    selection = chosenGenre?.label ?: "Todos",
                    options = genreOptions,
                    selectedIndex = (BrowseGenre.entries.filterNot { it.isAnime }
                        .indexOf(chosenGenre) + 1).coerceAtLeast(0),
                    onSelect = { index ->
                        if (index == 0) {
                            viewModel.clear()
                            if (query.isNotBlank()) viewModel.submit(query)
                        } else {
                            BrowseGenre.entries.filterNot { it.isAnime }
                                .getOrNull(index - 1)?.let { selectedGenre ->
                                    if (state.filter == SearchFilter.ANIME) {
                                        val mapped = when (selectedGenre) {
                                            BrowseGenre.ACTION -> "Action"
                                            BrowseGenre.SCI_FI -> "Sci-Fi"
                                            BrowseGenre.COMEDY -> "Comedy"
                                            BrowseGenre.DRAMA -> "Drama"
                                            BrowseGenre.ROMANCE -> "Romance"
                                            BrowseGenre.HORROR -> "Horror"
                                            BrowseGenre.MYSTERY -> "Mystery"
                                            else -> ""
                                        }
                                        onAnimeBrowse(mapped, query)
                                    } else viewModel.selectGenre(selectedGenre)
                                }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isBrowsing -> BrowsePane(
                    recent = state.recent,
                    onSearchRecent = viewModel::submit,
                    onGenre = viewModel::selectGenre,
                    onAnimeBrowse = onAnimeBrowse,
                    providers = if (platformsEnabled) platforms else emptyList(),
                    onOpenPlatform = onOpenPlatform
                )
                state.error != null && results.isEmpty() -> TvErrorState(onRetry = viewModel::retry)
                state.isLoading && results.isEmpty() -> TvLoading()
                results.isEmpty() -> TvEmptyPage(
                    if (query.trim().length < 2) stringResource(R.string.search_hint)
                    else stringResource(R.string.search_no_results_hint)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = TvLayout.PosterWidth),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = TvLayout.ContentEnd,
                        top = 36.dp,
                        bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(results, key = { _, item -> "${item.mediaType}:${item.id}" }) { _, item ->
                        TvCaptionedPoster(
                            title = item.name,
                            posterPath = item.posterPath ?: item.backdropPath,
                            caption = item.date.take(4).takeIf { it.length == 4 },
                            onClick = { open(item) }
                        )
                    }
                    if (state.isLoadingMore) {
                        item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/** What has been typed, large, with a line under it like a text field. */
@Composable
private fun QueryLine(query: String, genreLabel: String?) {
    Column {
        Text(
            text = when {
                query.isNotEmpty() -> query
                genreLabel != null -> genreLabel
                else -> stringResource(R.string.search_hint)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = if (query.isEmpty() && genreLabel == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun Key(
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width, KEY_SIZE)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            focusedContentColor = MaterialTheme.colorScheme.surface
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            } else if (label != null) {
                androidx.tv.material3.Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BrowsePane(
    recent: List<String>,
    onSearchRecent: (String) -> Unit,
    onGenre: (BrowseGenre) -> Unit,
    onAnimeBrowse: (genre: String, query: String) -> Unit,
    providers: List<com.ivor.openstream.data.remote.model.WatchProviderDto>,
    onOpenPlatform: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(top = 36.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (providers.isNotEmpty()) {
            item(key = "browse-platforms") {
                PillRow(title = "Descobrir por plataforma · Portugal") {
                    items(providers.take(30), key = { it.providerId }) { provider ->
                        TvPill(
                            label = provider.providerName,
                            onClick = { onOpenPlatform(provider.providerId) }
                        )
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                PillRow(title = stringResource(R.string.search_recent)) {
                    items(recent, key = { it }) { query ->
                        TvPill(label = query, icon = Icons.Default.History, onClick = { onSearchRecent(query) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PillRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(start = 8.dp)
                .semantics { heading() }
        )
        LazyRow(
            contentPadding = PaddingValues(start = 8.dp, end = TvLayout.ContentEnd, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}
