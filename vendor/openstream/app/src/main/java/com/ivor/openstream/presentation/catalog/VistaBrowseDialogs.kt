package com.ivor.openstream.presentation.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding

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
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    content()
                }
                Button(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
                    onClick = onApply
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SelectableRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = checked, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun VistaBrowseFiltersDialog(
    type: String,
    genre: String,
    year: Int?,
    onDismiss: () -> Unit,
    onApply: (String, String, Int?) -> Unit
) {
    var localType by remember { mutableStateOf(type) }
    var localGenre by remember { mutableStateOf(genre) }
    var localYear by remember { mutableStateOf(year) }
    val options = if (localType == "anime") animeGenres else generalGenres
    DialogShell("Filtros", onDismiss, "Aplicar filtros", {
        onApply(localType, localGenre, localYear)
    }) {
        Text("CONTEÚDO", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
        listOf(
            "all" to "Todos", "movie" to "Filmes", "series" to "Séries", "anime" to "Animes"
        ).forEach { (id, label) ->
            SelectableRow(label, localType == id) {
                if (localType != id) { localType = id; localGenre = "" }
            }
        }
        Text("GÉNERO", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
        SelectableRow("Todos os géneros", localGenre.isBlank()) { localGenre = "" }
        options.forEach { (value, label) ->
            SelectableRow(label, localGenre == value) { localGenre = value }
        }
        Text("ANO", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
        SelectableRow("Todos os anos", localYear == null) { localYear = null }
        (2026 downTo 1980).forEach { value ->
            SelectableRow(value.toString(), localYear == value) { localYear = value }
        }
        Text(
            "Idioma: nem Cinemeta nem AniList informam de forma fiável " +
                "que filmes possuem dublagem ou legendas PT-BR. " +
                "Podes selecionar essas faixas no player, quando a fonte as disponibiliza.",
            modifier = Modifier.padding(vertical = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun VistaBrowseSortDialog(
    selected: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var local by remember { mutableStateOf(selected) }
    DialogShell("Ordenação", onDismiss, "Ordenar", { onApply(local) }) {
        Text(
            "ORDENAR POR",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
        )
        listOf(
            "popular" to "Popularidade",
            "recent" to "Data de lançamento",
            "alphabetical" to "Ordem alfabética"
        ).forEach { (id, label) ->
            SelectableRow(label, local == id) { local = id }
        }
        Text(
            "A ordenação é aplicada aos títulos carregados. " +
                "Continua a descer para carregar mais páginas do catálogo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
