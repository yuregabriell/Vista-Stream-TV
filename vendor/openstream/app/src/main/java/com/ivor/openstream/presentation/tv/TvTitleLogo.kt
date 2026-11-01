package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Box
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

    fun load(mediaType: String, id: Int) {
        val key = "$mediaType:$id"
        if (requestedKey == key) return
        requestedKey = key
        _logoPath.value = null
        if (id <= 0 || mediaType !in listOf("movie", "tv")) return
        viewModelScope.launch {
            try {
                val response = if (mediaType == "movie") api.movieTitleImages(id)
                    else api.tvTitleImages(id)
                if (requestedKey == key) _logoPath.value = response.preferredLogoPath()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // Never break a title page because an image endpoint is unavailable.
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
    viewModel: TvTitleLogoViewModel = hiltViewModel()
) {
    val path by viewModel.logoPath.collectAsState()
    var failed by remember(path) { mutableStateOf(false) }
    var loaded by remember(path) { mutableStateOf(false) }
    LaunchedEffect(titleId, mediaType) { viewModel.load(mediaType, titleId) }

    Box(modifier.widthIn(max = 570.dp).heightIn(min = 46.dp, max = 112.dp)) {
        // Keep text visible while image is loading, or when no regional logo exists.
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
                modifier = Modifier.widthIn(max = 500.dp).heightIn(max = 105.dp)
            )
        }
    }
}
