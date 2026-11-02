package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import com.ivor.openstream.domain.model.VideoServer
import com.ivor.openstream.presentation.player.ServersState
import com.ivor.openstream.presentation.player.components.PanelHeader
import com.ivor.openstream.presentation.player.components.PlayerPanelHost
import com.ivor.openstream.presentation.player.components.SUBTITLES_OFF
import com.ivor.openstream.presentation.player.sourceSummary
import java.util.Locale

internal enum class TvEpisodePanel { SOURCES, SUBTITLES }

/**
 * TV side panels share the actual player's full-screen panel host. They do not start
 * playback: choosing a source remembers its preference, and choosing subtitles
 * changes the shared preference applied by Media3 when playback begins.
 */
@Composable
internal fun TvEpisodeOptionsPanel(
    panel: TvEpisodePanel?,
    sources: List<VideoServer>,
    serversState: ServersState,
    selectedServerId: String?,
    subtitleLanguages: List<String>,
    preferredSubtitle: String?,
    onChooseServer: (String) -> Unit,
    onRetry: () -> Unit,
    onChooseLanguage: (String) -> Unit,
    onAutomaticLanguage: () -> Unit,
    onDismiss: () -> Unit
) {
    PlayerPanelHost(
        visible = panel != null,
        isFullscreen = true,
        tvControls = true,
        onDismiss = onDismiss
    ) { listModifier, onClose ->
        if (panel == TvEpisodePanel.SOURCES) {
            val providerErrors = when (val state = serversState) {
                is ServersState.Resolving -> state.failedProviders
                is ServersState.Ready -> state.failedProviders
                is ServersState.Empty -> state.failedProviders
                ServersState.Idle -> emptyList()
            }
            PanelHeader(
                title = "Fontes",
                subtitle = when (serversState) {
                    is ServersState.Resolving -> "${sources.size} encontradas · A pesquisar…"
                    else -> "${sources.size} disponíveis"
                },
                onClose = onClose
            )
            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "refresh") {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        androidx.tv.material3.Text("Procurar mais fontes")
                    }
                }
                if (sources.isEmpty()) {
                    item(key = "none") {
                        Text(
                            when {
                                serversState is ServersState.Resolving -> "A procurar fontes…"
                                serversState is ServersState.Empty && serversState.totalProviders == 0 ->
                                    "Nenhuma fonte de reprodução ativa. Verifica a secção Extensões > Instaladas."
                                providerErrors.isNotEmpty() ->
                                    "Algumas fontes falharam. Consulta os erros abaixo; o problema pode ser externo."
                                else -> "As fontes consultadas não devolveram vídeos para este episódio."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (providerErrors.isNotEmpty()) {
                    item(key = "provider-errors") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Diagnóstico · ${providerErrors.size} fonte(s) com erro",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            providerErrors.distinct().take(10).forEach { error ->
                                Text(
                                    error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(sources, key = { it.id }) { server ->
                    val chosen = server.id == selectedServerId
                    val selectedLabel = if (chosen) "Fonte escolhida" else "Selecionar fonte"
                    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                        Icon(
                            if (chosen) Icons.Default.Check else Icons.Default.Dns,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            androidx.tv.material3.Text(
                                server.name,
                                maxLines = 2,
                                style = androidx.tv.material3.MaterialTheme.typography.titleSmall
                            )
                            androidx.tv.material3.Text(
                                server.providerName + " · " + server.sourceSummary(),
                                maxLines = 2,
                                style = androidx.tv.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (chosen) {
                        Button(onClick = { onChooseServer(server.id); onDismiss() },
                            modifier = Modifier.fillMaxWidth(), content = content)
                    } else {
                        OutlinedButton(onClick = { onChooseServer(server.id); onDismiss() },
                            modifier = Modifier.fillMaxWidth(), content = content)
                    }
                }
            }
        } else if (panel == TvEpisodePanel.SUBTITLES) {
            PanelHeader(
                title = "Áudio e legendas",
                subtitle = "Preferência para a próxima reprodução",
                onClose = onClose
            )
            LazyColumn(
                modifier = listModifier,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "automatic") {
                    val select: () -> Unit = { onAutomaticLanguage(); onDismiss() }
                    if (preferredSubtitle == null) {
                        Button(onClick = select, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text("Automático")
                        }
                    } else {
                        OutlinedButton(onClick = select, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text("Automático")
                        }
                    }
                }
                item(key = "off") {
                    val select: () -> Unit = { onChooseLanguage(SUBTITLES_OFF); onDismiss() }
                    if (preferredSubtitle == SUBTITLES_OFF) {
                        Button(onClick = select, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text("Desligadas")
                        }
                    } else {
                        OutlinedButton(onClick = select, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text("Desligadas")
                        }
                    }
                }
                items(subtitleLanguages, key = { it }) { language ->
                    val selected = preferredSubtitle.equals(language, ignoreCase = true)
                    val onSelect: () -> Unit = { onChooseLanguage(language); onDismiss() }
                    if (selected) {
                        Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text(tvEpisodeLanguageName(language))
                        }
                    } else {
                        OutlinedButton(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ClosedCaption, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.tv.material3.Text(tvEpisodeLanguageName(language))
                        }
                    }
                }
                if (subtitleLanguages.isEmpty()) {
                    item(key = "not-loaded") {
                        Text(
                            "Sem legendas externas identificadas. As faixas embutidas podem aparecer no player.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

internal fun tvEpisodeLanguageName(code: String): String {
    val locale = Locale.forLanguageTag(code.replace('_', '-'))
    val name = locale.getDisplayLanguage(Locale.forLanguageTag("pt-PT"))
    return if (name.isBlank() || name.equals(code, ignoreCase = true)) code.uppercase(Locale.ROOT)
        else name.replaceFirstChar { it.titlecase(Locale.forLanguageTag("pt-PT")) } + " ($code)"
}
