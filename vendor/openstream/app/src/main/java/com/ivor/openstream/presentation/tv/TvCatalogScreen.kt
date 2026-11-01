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
            FilterRail(
                label = "Tipo de conteúdo",
                choices = listOf(
                    TvFilterChoice("all", "Todos"),
                    TvFilterChoice("movie", "Filmes"),
                    TvFilterChoice("tv", "Séries")
                ),
                selected = state.mediaType,
                onSelect = viewModel::selectType
            )
        }
        item(key = "genres", span = { GridItemSpan(maxLineSpan) }) {
            FilterRail(
                label = "Género",
                choices = TV_CATALOG_GENRES.map { TvFilterChoice(it.key, it.label) },
                selected = state.genre,
                onSelect = viewModel::selectGenre
            )
        }
        item(key = "year", span = { GridItemSpan(maxLineSpan) }) {
            FilterRail(
                label = "Ano",
                choices = listOf(TvFilterChoice("all", "Todos os anos")) +
                    (Year.now().value downTo 1980).map { TvFilterChoice(it.toString(), it.toString()) },
                selected = state.year?.toString() ?: "all",
                onSelect = { viewModel.selectYear(it.toIntOrNull()) }
            )
        }
        if (state.providersEnabled) {
            item(key = "platforms", span = { GridItemSpan(maxLineSpan) }) {
                FilterRail(
                    label = "Plataforma · Portugal",
                    choices = listOf(TvFilterChoice("all", "Todas as plataformas")) +
                        state.providers.map { TvFilterChoice(it.providerId.toString(), it.providerName) },
                    selected = state.providerId?.toString() ?: "all",
                    onSelect = { viewModel.selectProvider(it.toIntOrNull()) }
                )
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

private data class TvFilterChoice(val id: String, val label: String)

@Composable
private fun FilterRail(
    label: String,
    choices: List<TvFilterChoice>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            modifier = Modifier.padding(start = TvLayout.ContentStart),
            style = MaterialTheme.typography.titleMedium
        )
        // LazyRow keeps provider catalogs (often 200+ brands) lightweight on
        // low-memory TV hardware, and D-pad focus scrolls the selected pill into view.
        LazyRow(
            contentPadding = PaddingValues(start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowItems(choices, key = { it.id }) { option ->
                TvPill(
                    option.label,
                    selected = selected == option.id,
                    onClick = { onSelect(option.id) }
                )
            }
        }
    }
}
