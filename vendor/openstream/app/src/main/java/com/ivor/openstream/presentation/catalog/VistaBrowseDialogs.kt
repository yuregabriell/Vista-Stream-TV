package com.ivor.openstream.presentation.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val animeGenres = listOf(
    "Action" to "Ação", "Adventure" to "Aventura", "Comedy" to "Comédia",
    "Drama" to "Drama", "Fantasy" to "Fantasia", "Mystery" to "Mistério",
    "Romance" to "Romance", "Sci-Fi" to "Ficção científica",
    "Slice of Life" to "Vida quotidiana", "Sports" to "Desporto",
    "Supernatural" to "Sobrenatural", "Horror" to "Terror"
)
private val generalGenres = listOf(
    "Action" to "Ação", "Adventure" to "Aventura", "Animation" to "Animação",
    "Comedy" to "Comédia", "Crime" to "Crime", "Documentary" to "Documentário",
    "Drama" to "Drama", "Family" to "Família", "Fantasy" to "Fantasia",
    "Horror" to "Terror", "Mystery" to "Mistério", "Romance" to "Romance",
    "Sci-Fi" to "Ficção científica", "Thriller" to "Suspense", "War" to "Guerra"
)

@Composable
private fun DialogShell(
    title: String,
    onClose: () -> Unit,
    actionLabel: String,
    onApply: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    content()
                }
                Button(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    onClick = onApply
                ) { Text(actionLabel) }
            }
        }
    }
}

/** Compact, scrollable popup. All category, genre, year and sort controls share
 *  the same interaction, while their selected values remain visible when closed.
 */
@Composable
private fun VistaDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    options.firstOrNull { it.first == selected }?.second ?: selected,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Abrir $label")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 260.dp, max = 520.dp)
                    .heightIn(max = 340.dp)
            ) {
                options.forEach { (value, name) ->
                    DropdownMenuItem(
                        text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                        trailingIcon = if (value == selected) {
                            { Icon(Icons.Default.Check, contentDescription = "Selecionado") }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun VistaBrowseFiltersDialog(
    type: String,
    genre: String,
    year: Int?,
    onDismiss: () -> Unit,
    onApply: (String, String, Int?) -> Unit
) {
    var localType by remember(type) { mutableStateOf(type) }
    var localGenre by remember(genre) { mutableStateOf(genre) }
    var localYear by remember(year) { mutableStateOf(year) }
    val genres = if (localType == "anime") animeGenres else generalGenres
    DialogShell(
        title = "Filtros",
        onClose = onDismiss,
        actionLabel = "Aplicar filtros",
        onApply = { onApply(localType, localGenre, localYear) }
    ) {
        Spacer(Modifier.padding(top = 3.dp))
        VistaDropdown(
            label = "CONTEÚDO",
            selected = localType,
            options = listOf(
                "all" to "Todos",
                "movie" to "Filmes",
                "series" to "Séries",
                "anime" to "Animes"
            ),
            onSelect = { next ->
                if (next != localType) {
                    localType = next
                    // Genres differ between anime and film catalogs.
                    localGenre = ""
                }
            }
        )
        VistaDropdown(
            label = "GÉNERO",
            selected = localGenre,
            options = listOf("" to "Todos os géneros") + genres,
            onSelect = { localGenre = it }
        )
        VistaDropdown(
            label = "ANO",
            selected = localYear?.toString().orEmpty(),
            options = listOf("" to "Todos os anos") +
                (2026 downTo 1980).map { it.toString() to it.toString() },
            onSelect = { localYear = it.toIntOrNull() }
        )
        Text(
            "As informações de dublagem e legendas variam de acordo com a fonte. " +
                "Seleciona os idiomas disponíveis no player.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
fun VistaBrowseSortDialog(
    selected: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var local by remember(selected) { mutableStateOf(selected) }
    DialogShell(
        title = "Ordenação",
        onClose = onDismiss,
        actionLabel = "Ordenar",
        onApply = { onApply(local) }
    ) {
        Spacer(Modifier.padding(top = 3.dp))
        VistaDropdown(
            label = "ORDENAR POR",
            selected = local,
            options = listOf(
                "popular" to "Popularidade",
                "recent" to "Data de lançamento",
                "alphabetical" to "Ordem alfabética"
            ),
            onSelect = { local = it }
        )
        Text(
            "A ordenação aplica-se aos títulos carregados. " +
                "Desce na lista para carregar mais resultados.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
