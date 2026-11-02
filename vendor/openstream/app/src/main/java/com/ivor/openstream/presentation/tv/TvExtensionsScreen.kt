package com.ivor.openstream.presentation.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.ivor.openstream.R
import com.ivor.openstream.data.settings.SourceSearchMode
import com.ivor.openstream.domain.model.MarketplaceExtension
import com.ivor.openstream.domain.model.ExtensionEngineType
import com.ivor.openstream.presentation.marketplace.MarketplaceViewModel
import com.ivor.openstream.presentation.marketplace.extensionSubtitle

/**
 * Sources as a list and a detail pane. The list on the left switches between what is installed
 * (in search order, with the search mode above it) and the whole catalog; focusing a source shows
 * it on the right with its actions. Removing asks first in a centred panel. Adding a repository by
 * URL and searching work with the Android TV on-screen keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvExtensionsScreen(
    modifier: Modifier = Modifier,
    viewModel: MarketplaceViewModel = hiltViewModel(),
    providersViewModel: TvProvidersViewModel = hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val state by viewModel.state.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val officialProvidersEnabled by providersViewModel.enabled.collectAsState()
    LaunchedEffect(viewModel) { viewModel.liveRefreshWhileVisible() }
    // Separate UX surfaces: native integrations, installed playback sources and discovery.
    var area by remember { mutableIntStateOf(0) }
    val showInstalled = area == 1
    var sourceCategory by remember { mutableStateOf(0) }
    var showAddRepo by remember { mutableStateOf(false) }
    var repoInput by remember { mutableStateOf("") }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<MarketplaceExtension?>(null) }
    var detailHasFocus by remember { mutableStateOf(false) }

    // The public repo may list hundreds of runtimes we cannot execute.
    // Hide those from Explore; keep installed legacy entries removable.
    val candidates = if (showInstalled) state.installed else state.results
    val visible = candidates.filter { extension ->
        (showInstalled || extension.manifest.isSupported) && when (sourceCategory) {
            1 -> extension.manifest.engine.type == ExtensionEngineType.STREMIO
            2 -> extension.manifest.engine.type != ExtensionEngineType.STREMIO
            else -> true
        }
    }
    val selected = visible.firstOrNull { it.key == selectedKey } ?: visible.firstOrNull()
    val rowRequesters = remember { mutableMapOf<String, FocusRequester>() }
    fun rowRequester(key: String) = rowRequesters.getOrPut(key) { FocusRequester() }
    val firstPill = remember { FocusRequester() }
    val detailAction = remember { FocusRequester() }

    LaunchedEffect(area) {
        withFrameNanos { }
        withFrameNanos { }
        runCatching { firstPill.requestFocus() }
    }
    BackHandler(enabled = area != 0 && detailHasFocus && pendingRemove == null && selected != null) {
        runCatching { rowRequester(selected!!.key).requestFocus() }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader(
                title = "Extensões e fontes",
                supporting = "Personaliza o catálogo e gere as fontes do player",
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { showAddRepo = true },
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Adicionar")
                        }
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            enabled = !state.catalog.isSyncing,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Atualizar")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.padding(
                    start = TvLayout.ContentStart,
                    top = 8.dp, bottom = 12.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TvPill(
                    "Nativas",
                    selected = area == 0,
                    onClick = { area = 0 },
                    modifier = Modifier.focusRequester(firstPill)
                )
                TvPill(
                    "Instaladas (${state.installed.size})",
                    selected = area == 1,
                    onClick = { area = 1 }
                )
                TvPill(
                    "Explorar",
                    selected = area == 2,
                    onClick = { area = 2 }
                )
            }

            if (area == 0) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(
                        start = TvLayout.ContentStart,
                        end = TvLayout.ContentEnd, bottom = 30.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Plataformas oficiais",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    "TMDB · Disponibilidade em Portugal",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            TvPill(
                                if (officialProvidersEnabled) "Ativado" else "Ativar",
                                selected = officialProvidersEnabled,
                                onClick = { providersViewModel.setEnabled(!officialProvidersEnabled) }
                            )
                        }
                        Text(
                            "Indica em que serviços uma obra está disponível e permite filtrar por plataforma. Não adiciona streams.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(
                            Modifier.weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(18.dp)
                        ) {
                            Text("Catálogo TMDB", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Títulos, capas, temporadas e episódios.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Integrado", color = MaterialTheme.colorScheme.primary)
                        }
                        Column(
                            Modifier.weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(18.dp)
                        ) {
                            Text("Curadoria AniList", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tendências, estreias e géneros de anime.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Integrado", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize()
                        .padding(start = TvLayout.ContentStart, end = TvLayout.ContentEnd),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Column(
                        Modifier.width(325.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        TvFilterDropdown(
                            label = "Tipo de fonte",
                            selection = listOf(
                                "Todas", "Stremio", "Web / Vista"
                            )[sourceCategory],
                            options = listOf("Todas", "Stremio", "Web / Vista"),
                            selectedIndex = sourceCategory,
                            onSelect = { sourceCategory = it }
                        )
                        if (area == 2) {
                            TvDpadTextInput(
                                value = state.query,
                                onSubmit = viewModel::setQuery,
                                title = "Procurar addon",
                                placeholder = "Nome ou categoria…",
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                "Ordem e ativação das fontes",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            if (area == 1) "${visible.size} fontes instaladas"
                            else "${visible.size} addons compatíveis",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (visible.isEmpty()) {
                            Text(
                                if (area == 1) "Sem fontes instaladas nesta categoria."
                                else "Não há addons compatíveis com este filtro.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f)
                                    .focusRestorer(selected?.let {
                                        rowRequester(it.key)
                                    } ?: FocusRequester.Default),
                                contentPadding = PaddingValues(bottom = 36.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                itemsIndexed(visible, key = { _, item -> item.key }) { _, extension ->
                                    ListItem(
                                        selected = extension.key == selected?.key,
                                        onClick = {
                                            selectedKey = extension.key
                                            runCatching { detailAction.requestFocus() }
                                        },
                                        headlineContent = {
                                            Text(
                                                extension.manifest.name, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                statusLabel(extension), maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.Extension, null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .focusRequester(rowRequester(extension.key))
                                            .onFocusChanged {
                                                if (it.isFocused) selectedKey = extension.key
                                            }
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(22.dp)
                            )
                            .onFocusChanged { detailHasFocus = it.hasFocus }
                            .padding(22.dp)
                    ) {
                        if (selected != null) {
                            val position = state.installed.indexOfFirst {
                                it.key == selected.key
                            }
                            ExtensionDetail(
                                extension = selected,
                                actionFocusRequester = detailAction,
                                canMoveUp = showInstalled && position > 0,
                                canMoveDown = showInstalled &&
                                    position in 0 until state.installed.lastIndex,
                                onInstall = { viewModel.install(selected) },
                                onUpdate = { viewModel.update(selected) },
                                onEnabledChange = { viewModel.setEnabled(selected, it) },
                                onMove = { offset -> viewModel.moveSource(selected, offset) },
                                onRemove = { pendingRemove = selected }
                            )
                            if (showInstalled && state.installed.size > 1) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "Ordem de procura", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SourceSearchMode.entries.forEach { mode ->
                                        TvPill(
                                            label = stringResource(mode.labelRes),
                                            selected = searchMode == mode,
                                            onClick = { viewModel.setSearchMode(mode) }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                "Seleciona uma fonte à esquerda.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }

        if (showAddRepo) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAddRepo = false },
                title = { androidx.compose.material3.Text("Adicionar repositório") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { androidx.compose.material3.Text("URL JSON ou Stremio manifest.json") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.material3.Text(
                            "Fontes OpenStream e add-ons Stremio compatíveis podem funcionar no nosso player.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        androidx.compose.material3.Text(
                            "AIOStreams PT-BR: a configuração do Reddit exige gerar " +
                                "um URL próprio; não é uma extensão pronta para importar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            runCatching { uriHandler.openUri("https://aiostreams.fortheweak.cloud/stremio/configure") }
                        }) { androidx.compose.material3.Text("Configurar AIOStreams PT-BR") }
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.addRepo("https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json")
                            area = 2
                            showAddRepo = false
                        }) { androidx.compose.material3.Text("Ver CloudStream oficial (.cs3): só catálogo") }
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.addRepo("https://raw.githubusercontent.com/admknight/CloudstreamExtensions/master/repo.json")
                            area = 2
                            showAddRepo = false
                        }) { androidx.compose.material3.Text("Ver mega biblioteca (.cs3): só catálogo") }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.addRepo(repoInput)
                            repoInput = ""
                            area = 2
                            showAddRepo = false
                        },
                        enabled = repoInput.isNotBlank()
                    ) { androidx.compose.material3.Text("Adicionar") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showAddRepo = false }) {
                        androidx.compose.material3.Text("Cancelar")
                    }
                }
            )
        }

        pendingRemove?.let { extension ->
            TvConfirmPanel(
                title = stringResource(R.string.action_remove) + "?",
                message = extension.manifest.name,
                confirmLabel = stringResource(R.string.action_remove),
                onConfirm = {
                    viewModel.uninstall(extension)
                    pendingRemove = null
                },
                onCancel = { pendingRemove = null }
            )
        }
    }
}

@Composable
private fun statusLabel(extension: MarketplaceExtension): String = when {
    !extension.manifest.isSupported -> stringResource(R.string.mk_unsupported)
    extension.hasUpdate -> stringResource(R.string.action_update)
    extension.isEnabled -> stringResource(R.string.er_active_player)
    extension.isInstalled -> stringResource(R.string.mk_paused)
    else -> extension.manifest.language
}

@Composable
private fun ExtensionDetail(
    extension: MarketplaceExtension,
    actionFocusRequester: FocusRequester,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val manifest = extension.manifest
    var showTechnical by remember(extension.key) { mutableStateOf(false) }
    val kind = when (manifest.engine.type) {
        ExtensionEngineType.STREMIO -> "ADDON STREMIO"
        ExtensionEngineType.UNSUPPORTED -> "INCOMPATÍVEL"
        else -> "FONTE DE REPRODUÇÃO"
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            kind, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            manifest.name,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                manifest.language,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (extension.isInstalled) {
                    if (extension.isEnabled) "● Ativa" else "○ Pausada"
                } else "Disponível",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            manifest.description.ifBlank {
                "Fonte externa compatível com o motor VistaStream."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3, overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!extension.isInstalled) {
                Button(
                    onClick = onInstall,
                    enabled = manifest.isSupported,
                    modifier = Modifier.focusRequester(actionFocusRequester)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Instalar")
                }
            } else {
                if (extension.hasUpdate) {
                    Button(
                        onClick = onUpdate,
                        modifier = Modifier.focusRequester(actionFocusRequester)
                    ) { Text("Atualizar") }
                }
                if (manifest.isSupported) {
                    TvPill(
                        label = if (extension.isEnabled) "Desativar" else "Ativar",
                        selected = extension.isEnabled,
                        onClick = { onEnabledChange(!extension.isEnabled) },
                        modifier = if (extension.hasUpdate) Modifier
                        else Modifier.focusRequester(actionFocusRequester)
                    )
                }
                OutlinedButton(
                    onClick = onRemove,
                    modifier = if (!manifest.isSupported) Modifier.focusRequester(actionFocusRequester)
                    else Modifier
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Remover")
                }
            }
        }
        if (extension.isInstalled && (canMoveUp || canMoveDown)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prioridade", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canMoveUp) {
                    IconButton(onClick = { onMove(-1) }) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Mover para cima")
                    }
                }
                if (canMoveDown) {
                    IconButton(onClick = { onMove(1) }) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Mover para baixo")
                    }
                }
            }
        }
        OutlinedButton(onClick = { showTechnical = !showTechnical }) {
            Text(if (showTechnical) "Ocultar informações" else "Informações técnicas")
        }
        if (showTechnical) {
            Column(
                Modifier.fillMaxWidth().background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(14.dp)
                ).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Motor: ${manifest.engine.type.key} · Autor: ${manifest.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (manifest.contentTypes.isNotEmpty()) {
                    Text(
                        "Conteúdos: ${manifest.contentTypes.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "Addons externos só fornecem os recursos que o VistaStream consegue interpretar; não modificam os menus nem o layout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }
        }
    }
}
