package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.R
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.presentation.watch_later.SavedFilter
import com.ivor.openstream.presentation.watch_later.SavedSort
import com.ivor.openstream.presentation.watch_later.WatchLaterViewModel

/**
 * Saved titles as a poster grid. Filters and sort orders are one row of pills under the title;
 * OK on a poster opens its details and holding OK asks to remove it from Saved.
 */
@Composable
fun TvSavedScreen(
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchLaterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingRemove by remember { mutableStateOf<WatchLaterEntity?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val firstPoster = remember { FocusRequester() }
    val firstPill = remember { FocusRequester() }

    LaunchedEffect(state.isLoading, state.items.size, showHistory) {
        if (!showHistory && state.isLoading) return@LaunchedEffect
        if (showHistory || state.items.isEmpty()) {
            runCatching { firstPill.requestFocus() }
        } else {
            runCatching { firstPoster.requestFocus() }.recoverCatching { firstPill.requestFocus() }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader(
                title = if (showHistory) "Histórico" else stringResource(R.string.nav_saved),
                supporting = if (showHistory) "Vídeos e episódios reproduzidos neste perfil"
                    else if (state.totalCount > 0) stringResource(R.string.tv_hold_to_remove)
                    else "Guarda títulos com o marcador na página de detalhes"
            )
            Row(
                modifier = Modifier.padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvPill(
                    label = "Minha lista",
                    selected = !showHistory,
                    onClick = { showHistory = false },
                    modifier = Modifier.focusRequester(firstPill)
                )
                TvPill(
                    label = "Histórico",
                    selected = showHistory,
                    onClick = { showHistory = true }
                )
            }
            if (!showHistory) LazyRow(
                contentPadding = PaddingValues(start = TvLayout.ContentStart, end = TvLayout.ContentEnd, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SavedFilter.entries, key = { "filter:${it.name}" }) { filter ->
                    TvPill(
                        label = stringResource(filter.labelRes),
                        selected = state.filter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                        modifier = Modifier
                    )
                }
                item(key = "divider") {
                    Box(
                        Modifier
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                items(SavedSort.entries, key = { "sort:${it.name}" }) { sort ->
                    TvPill(
                        label = stringResource(sort.labelRes),
                        selected = state.sort == sort,
                        onClick = { viewModel.onSortChange(sort) }
                    )
                }
            }

            if (showHistory) {
                TvHistoryPanel(onOpenTitle = onOpenTitle, modifier = Modifier.weight(1f))
            } else when {
                state.isLoading -> TvLoading()
                state.items.isEmpty() -> TvEmptyPage(
                    if (state.totalCount == 0) "Ainda não guardaste títulos. Usa o marcador na página de detalhes."
                    else "Nenhum título corresponde a este filtro. Escolhe Tudo para ver todos os salvos."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = TvLayout.PosterWidth),
                    contentPadding = PaddingValues(
                        start = TvLayout.ContentStart,
                        end = TvLayout.ContentEnd,
                        top = 16.dp,
                        bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.items, key = { "${it.entry.id}:${it.entry.mediaType}" }) { item ->
                        val entry = item.entry
                        TvCaptionedPoster(
                            title = entry.title,
                            posterPath = entry.posterPath,
                            caption = item.latest?.let {
                                stringResource(R.string.li_episode_code, it.season, it.episode)
                            } ?: stringResource(
                                if (entry.mediaType == "movie") R.string.li_saved_movies else R.string.li_saved_series
                            ),
                            modifier = if (item == state.items.first()) Modifier.focusRequester(firstPoster) else Modifier,
                            onClick = { onOpenTitle(entry.id, entry.mediaType) },
                            onLongClick = { pendingRemove = entry }
                        )
                    }
                }
            }
        }

        pendingRemove?.let { entry ->
            TvConfirmPanel(
                title = stringResource(R.string.action_remove_from_saved),
                message = entry.title,
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = {
                    viewModel.remove(entry)
                    pendingRemove = null
                },
                onCancel = { pendingRemove = null }
            )
        }
    }
}
