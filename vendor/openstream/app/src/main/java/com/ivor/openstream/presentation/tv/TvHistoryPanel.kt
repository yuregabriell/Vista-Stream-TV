package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ivor.openstream.presentation.watch_history.WatchHistoryViewModel

/**
 * TV watch history must be reachable from the Saved tab; previously the TV had
 * no history destination at all, even when progress was correctly recorded.
 */
@Composable
fun TvHistoryPanel(
    onOpenTitle: (id: Int, mediaType: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val records = state.groups.flatMap { it.items }
    if (records.isEmpty()) {
        TvEmptyPage(
            "Ainda não há histórico. Assiste pelo menos alguns segundos " +
                "a um filme ou episódio para começar a registar."
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TvLayout.PosterWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TvLayout.ContentStart,
            end = TvLayout.ContentEnd,
            top = 18.dp,
            bottom = 54.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(TvLayout.CardGap),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(records, key = { "${it.mediaType}:${it.tmdbId}:${it.season}:${it.episode}" }) { entry ->
            TvCaptionedPoster(
                title = entry.title,
                posterPath = entry.posterPath ?: entry.backdropPath,
                caption = if (entry.completed) "Visto ✓"
                    else if (entry.isMovie) "Filme · Em progresso"
                    else "T${entry.season} · E${entry.episode} · Em progresso",
                onClick = { onOpenTitle(entry.tmdbId, entry.mediaType) }
            )
        }
    }
}
