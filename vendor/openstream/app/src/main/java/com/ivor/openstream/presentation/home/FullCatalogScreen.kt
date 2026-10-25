package com.ivor.openstream.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.item
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.catalog.KeylessCatalogScreen
import com.ivor.openstream.presentation.catalog.KeylessCatalogViewModel
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.presentation.components.bottomContentPadding
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun FullCatalogScreen(
    initialCategory: String,
    onBack: () -> Unit,
    onOpenDetails: (String, Int) -> Unit,
    viewModel: FullCatalogViewModel = hiltViewModel(),
    keylessViewModel: KeylessCatalogViewModel = hiltViewModel()
) {
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    var query by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var yearInput by rememberSaveable { mutableStateOf("") }
    var selectedAniList by remember { mutableStateOf<KeylessTitle?>(null) }
    val state by viewModel.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(category, query, yearInput) {
        viewModel.setFilters(category, query, yearInput.toIntOrNull())
    }

    if (selectedAniList != null) {
        KeylessCatalogScreen(
            viewModel = keylessViewModel,
            initialTitle = selectedAniList,
            onInitialDismiss = { selectedAniList = null }
        )
        return
    }

    BackHandler(onBack = onBack)
    val gridState = rememberLazyGridState()
    // Each page is requested only when the viewport approaches its end.
    LaunchedEffect(state.page, state.loading, state.hasMore, state.items.size) {
        if (!state.loading && state.hasMore && state.items.isNotEmpty()) {
            snapshotFlow {
                val layout = gridState.layoutInfo
                val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
                val count = layout.totalItemsCount
                count > 0 && last >= count - 8
            }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
        }
    }

    val localized = remember { NumberFormat.getIntegerInstance(Locale("pt", "PT")) }
    val count = when(category) {
        "movie" -> state.moviesTotal
        "tv" -> state.seriesTotal
        "anime" -> state.animeTmdbTotal
        else -> state.moviesTotal + state.seriesTotal
    }
    val countText = when {
        count == 0 -> "A descobrir títulos no catálogo..."
        category == "anime" -> localized.format(count) + " animações japonesas no TMDB" +
            if (state.hasAniList) " · AniList adicional" else ""
        count >= 20_001 && category != "all" -> "Mais de 20.000 títulos encontrados · TMDB"
        category == "all" && (state.moviesTotal >= 20_001 || state.seriesTotal >= 20_001) ->
            "Mais de 20.000 filmes e mais de 20.000 séries no TMDB"
        else -> localized.format(count) + " resultados · TMDB"
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar à página inicial")
            }
            Column(Modifier.weight(1f)) {
                Text("Catálogo completo", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text("Filmes · Séries · Anime", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val categories = listOf("all" to "Tudo", "movie" to "Filmes",
                "tv" to "Séries", "anime" to "Animes")
            items(categories.size) { index ->
                val (id, label) = categories[index]
                FilterChip(
                    selected = category == id, onClick = {
                        category = id
                        typed = ""
                        query = ""
                    },
                    label = { Text(label) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pesquisar no catálogo...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    query = typed.trim()
                    keyboard?.hide()
                })
            )
            OutlinedButton(onClick = { query = typed.trim(); keyboard?.hide() }) {
                Text("Buscar")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = yearInput, onValueChange = { yearInput = it.filter(Char::isDigit).take(4) },
                label = { Text("Ano") }, placeholder = { Text("Todos") },
                modifier = Modifier.width(105.dp), singleLine = true
            )
            Text(
                "Filtra por ano para explorar além do limite de páginas do TMDB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            countText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
        )
        Text(
            localized.format(state.items.size) + " títulos carregados · Desliza para ver mais",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(125.dp), state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 15.dp, end = 15.dp,
                top = 9.dp, bottom = bottomContentPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp)
        ) {
            items(state.items, key = { it.key }) { item ->
                Column(modifier = Modifier
                    .clickable {
                        if (item.tmdbId != null) onOpenDetails(item.mediaType, item.tmdbId)
                        else item.anilist?.let { selectedAniList = it }
                    },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AsyncImage(
                        model = item.poster,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.68f)
                            .background(MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(12.dp))
                    )
                    Text(item.title, maxLines = 2, minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge)
                    Text(
                        (item.year ?: "") + " · " + when {
                            item.anilist != null -> "AniList"
                            item.mediaType == "movie" -> "Filme"
                            else -> "Série"
                        },
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.loading || state.hasMore || state.error != null) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.loading) CircularProgressIndicator()
                        else {
                            state.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedButton(onClick = viewModel::loadMore) {
                                Text(if (state.error != null) "Tentar novamente" else "Carregar mais")
                            }
                        }
                    }
                }
            }
        }
    }
}
