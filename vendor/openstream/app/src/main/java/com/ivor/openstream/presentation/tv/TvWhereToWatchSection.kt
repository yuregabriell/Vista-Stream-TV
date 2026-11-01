package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.OutlinedButton
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.model.WatchProviderDto

/** Official subscription availability, separate from unofficial Vista playback sources. */
@Composable
fun TvWhereToWatchSection(
    mediaType: String,
    titleId: Int,
    viewModel: TvProvidersViewModel = hiltViewModel()
) {
    val enabled by viewModel.enabled.collectAsState()
    val availability by viewModel.availability.collectAsState()
    val loading by viewModel.loading.collectAsState()
    LaunchedEffect(mediaType, titleId, enabled) {
        if (enabled) viewModel.loadTitle(mediaType, titleId)
    }
    if (!enabled) return

    val region = availability
    val groups = buildList {
        region?.flatrate?.takeIf { it.isNotEmpty() }?.let { add("Subscrição" to it) }
        (region?.free.orEmpty() + region?.ads.orEmpty()).distinctBy { it.providerId }
            .takeIf { it.isNotEmpty() }?.let { add("Grátis / com anúncios" to it) }
        (region?.rent.orEmpty() + region?.buy.orEmpty()).distinctBy { it.providerId }
            .takeIf { it.isNotEmpty() }?.let { add("Alugar / comprar" to it) }
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvRowHeader("Onde assistir oficialmente · Portugal", start = TvLayout.PageStart)
        if (loading && region == null) {
            Text(
                "A verificar plataformas de streaming…",
                modifier = Modifier.padding(start = TvLayout.PageStart),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (groups.isEmpty()) {
            Text(
                "Sem disponibilidade confirmada para Portugal.",
                modifier = Modifier.padding(start = TvLayout.PageStart),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        for ((label, providers) in groups) {
            Text(
                label,
                modifier = Modifier.padding(start = TvLayout.PageStart),
                style = MaterialTheme.typography.titleSmall
            )
            LazyRow(
                contentPadding = PaddingValues(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(providers.distinctBy { it.providerId }, key = { it.providerId }) { provider ->
                    val handler = LocalUriHandler.current
                    val url = region?.link
                    OutlinedButton(
                        enabled = !url.isNullOrBlank(),
                        onClick = { url?.let(handler::openUri) }
                    ) {
                        provider.logoPath?.let {
                            AsyncImage(
                                model = "https://image.tmdb.org/t/p/w92$it",
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        androidx.tv.material3.Text(
                            provider.providerName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Text(
            "Informações de TMDB / JustWatch. A disponibilidade varia por país e pode mudar. " +
                "Estas plataformas são independentes das fontes do VistaStream.",
            modifier = Modifier.padding(start = TvLayout.PageStart, end = TvLayout.ContentEnd),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
