package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.preferredLogoPath
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Only the visible title requests an official TMDB logo. Failed/absent image
 * or missing anime<->TMDB mapping falls back to accessible, readable title text.
 */
@HiltViewModel
class TvTitleLogoViewModel @Inject constructor(
    private val api: TmdbApi
) : ViewModel() {
    private val _logoPath = MutableStateFlow<String?>(null)
    val logoPath: StateFlow<String?> = _logoPath
    private var requestedKey: String? = null
    private val successfulLogos = mutableMapOf<String, String>()

    fun load(mediaType: String, id: Int) {
        val key = "$mediaType:$id"
        if (requestedKey == key) return
        requestedKey = key
        _logoPath.value = successfulLogos[key]
        if (_logoPath.value != null || id <= 0 || mediaType !in listOf("movie", "tv")) return
        viewModelScope.launch {
            try {
                // Make one comprehensive image request instead of a serial
                // PT/EN request plus a second fallback call for every title.
                // The response is cached by the TMDB metadata HTTP cache.
                val images = if (mediaType == "movie")
                    api.movieTitleImages(id, "pt,en,ja,es,fr,null")
                else api.tvTitleImages(id, "pt,en,ja,es,fr,null")
                val best = images.preferredLogoPath()
                if (requestedKey == key) {
                    if (best != null) {
                        if (successfulLogos.size > 60) successfulLogos.clear()
                        successfulLogos[key] = best
                    }
                    _logoPath.value = best
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // A missing gateway endpoint or failed request must never break details.
            }
        }
    }
}

@Composable
fun TvOfficialTitleLogo(
    title: String,
    mediaType: String,
    titleId: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    viewModel: TvTitleLogoViewModel = hiltViewModel()
) {
    val path by viewModel.logoPath.collectAsState()
    var failed by remember(path, titleId, mediaType) { mutableStateOf(false) }
    var loaded by remember(path, titleId, mediaType) { mutableStateOf(false) }
    LaunchedEffect(titleId, mediaType) { viewModel.load(mediaType, titleId) }

    // Fixed 110dp reserved for the logo prevents lazy-scroll remeasurement
    // from clipping its top when the official artwork arrives asynchronously.
    Box(modifier.widthIn(max = if (compact) 470.dp else 570.dp)
        .height(if (compact) 76.dp else 110.dp)) {
        if (path == null || failed || !loaded) {
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (path != null && !failed) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500$path",
                contentDescription = "Logótipo oficial: $title",
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.widthIn(max = if (compact) 450.dp else 520.dp).fillMaxHeight()
            )
        }
    }
}
