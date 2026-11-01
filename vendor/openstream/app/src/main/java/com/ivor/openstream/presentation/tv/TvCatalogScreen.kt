package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import java.time.Year

/** Dedicated paginated catalogue, unlike the limited Home recommendation rails. */
@Composable
fun TvCatalogScreen(
    onOpenTitle: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val grid = rememberLazyGridState()
    LaunchedEffect(state.mediaType, state.genre, state.year, state.providerId) {
        grid.scrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = grid,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            TvPageHeader(
                title = "Todo o catálogo",
                supporting = "Explora filmes e séries. Filtra e carrega mais títulos, além dos destaques da Home.",
                actions = {
                    OutlinedButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        androidx.tv.material3.Text("Atualizar")
                    }
                }
            )
        }
        item(key = "content-type", span = { GridItemSpan(maxLineSpan) }) {
            FilterRail("Tipo de conteúdo") {
                TvPill("Todos", selected = state.mediaType == "all", onClick = { viewModel.selectType("all") })
                TvPill("Filmes", selected = state.mediaType == "movie", onClick = { viewModel.selectType("movie") })
                TvPill("Séries", selected = state.mediaType == "tv", onClick = { viewModel.selectType("tv") })
            }
        }
        item(key = "genres", span = { GridItemSpan(maxLineSpan) }) {
            FilterRail("Género") {
                TV_CATALOG_GENRES.forEach { genre ->
                    TvPill(
                        genre.label,
                        selected = state.genre == genre.key,
                        onClick = { viewModel.selectGenre(genre.key) }
                    )
                }
            }
        }
        item(key = "year", span = { GridItemSpan(maxLineSpan) }) {
            FilterRail("Ano") {
                TvPill("Todos os anos", selected = state.year == null, onClick = { viewModel.selectYear(null) })
                (Year.now().value downTo 1980).forEach { year ->
                    TvPill(year.toString(), selected = state.year == year, onClick = { viewModel.selectYear(year) })
                }
            }
        }
        if (state.providersEnabled) {
            item(key = "platforms", span = { GridItemSpan(maxLineSpan) }) {
                FilterRail("Plataforma · Portugal") {
                    TvPill("Todas as plataformas", selected = state.providerId == null,
                        onClick = { viewModel.selectProvider(null) })
                    state.providers.forEach { provider ->
                        TvPill(
                            provider.providerName,
                            selected = state.providerId == provider.providerId,
                            onClick = { viewModel.selectProvider(provider.providerId) }
                        )
                    }
                }
            }
        }
        item(key = "results-heading", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = TvLayout.ContentStart)) {
                Text(
                    "Resultados · ${state.items.size} títulos carregados",
                    style = MaterialTheme.typography.titleLarge
                )
                if (state.loading) {
                    Text("A carregar resultados…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        items(
            items = state.items,
            key = { "${it.mediaType}:${it.id}" }
        ) { title ->
            Box(Modifier.padding(horizontal = 3.dp)) {
                TvPosterCard(
                    title = title.name,
                    posterPath = title.posterPath,
                    onClick = {
                        onOpenTitle(title.mediaType ?: if (title.isMovie) "movie" else "tv", title.id)
                    }
                )
            }
        }
        if (state.items.isEmpty() && !state.loading) {
            item(key = "no-results", span = { GridItemSpan(maxLineSpan) }) {
                TvEmptyPage(if (state.error != null) "Não foi possível carregar." else "Nenhum título com estes filtros.")
            }
        }
        if (state.hasMore && !state.loading) {
            item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = viewModel::loadMore) {
                        Icon(Icons.Default.GridView, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        androidx.tv.material3.Text("Carregar mais títulos")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRail(
    label: String,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(start = TvLayout.ContentStart),
            style = MaterialTheme.typography.titleMedium
        )
        // Use an ordinary horizontally scrollable row with TV pill focus. Do not
        // truncate provider/year choices to the titles visible on Home.
        androidx.compose.foundation.horizontalScroll(rememberScrollState()).let { scrollModifier ->
            Row(
                modifier = Modifier.fillMaxWidth().then(scrollModifier)
                    .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content
            )
        }
    }
}
