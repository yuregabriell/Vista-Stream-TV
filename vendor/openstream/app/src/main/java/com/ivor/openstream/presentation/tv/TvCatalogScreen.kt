package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
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
    initialProviderId: Int? = null,
    viewModel: TvCatalogViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val grid = rememberLazyGridState()
    LaunchedEffect(initialProviderId, state.providersEnabled) {
        if (initialProviderId != null && state.providersEnabled) {
            viewModel.selectProvider(initialProviderId)
        }
    }
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
        item(key = "catalog-dropdowns", span = { GridItemSpan(maxLineSpan) }) {
            val types = listOf(
                TvFilterChoice("all", "Todos"),
                TvFilterChoice("movie", "Filmes"),
                TvFilterChoice("tv", "Séries")
            )
            val genres = TV_CATALOG_GENRES.map { TvFilterChoice(it.key, it.label) }
            val years = listOf(TvFilterChoice("all", "Todos os anos")) +
                (Year.now().value downTo 1980).map {
                    TvFilterChoice(it.toString(), it.toString())
                }
            val providers = listOf(TvFilterChoice("all", "Todas as plataformas")) +
                state.providers.map {
                    TvFilterChoice(it.providerId.toString(), it.providerName)
                }
            // Compact four-column filter bar: no more oversized rails that
            // consume the whole TV viewport before results can be seen.
            Row(
                Modifier.fillMaxWidth().padding(
                    start = TvLayout.ContentStart, end = TvLayout.ContentEnd,
                    top = 4.dp, bottom = 8.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CatalogDropdown("Tipo", types, state.mediaType, viewModel::selectType,
                    Modifier.weight(1f))
                CatalogDropdown("Género", genres, state.genre, viewModel::selectGenre,
                    Modifier.weight(1f))
                CatalogDropdown(
                    "Ano", years, state.year?.toString() ?: "all",
                    { viewModel.selectYear(it.toIntOrNull()) }, Modifier.weight(1f)
                )
                if (state.providersEnabled) {
                    CatalogDropdown(
                        "Plataforma", providers, state.providerId?.toString() ?: "all",
                        { viewModel.selectProvider(it.toIntOrNull()) }, Modifier.weight(1.15f)
                    )
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

private data class TvFilterChoice(val id: String, val label: String)

@Composable
private fun CatalogDropdown(
    label: String,
    choices: List<TvFilterChoice>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier
) {
    val index = choices.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    TvFilterDropdown(
        label = label,
        selection = choices.getOrNull(index)?.label ?: "Todos",
        options = choices.map { it.label },
        selectedIndex = index,
        onSelect = { position -> choices.getOrNull(position)?.let { onSelect(it.id) } },
        modifier = modifier
    )
}
