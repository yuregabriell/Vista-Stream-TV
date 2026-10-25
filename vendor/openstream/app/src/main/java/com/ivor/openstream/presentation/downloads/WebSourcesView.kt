package com.ivor.openstream.presentation.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ivor.openstream.harvester.NativeHarvesterStore
import com.ivor.openstream.harvester.NativeSavedStream
import com.ivor.openstream.harvester.HarvesterContract

/** Web references are distinct from offline downloads. Expiring URLs become source page bookmarks. */
@Composable
fun WebSourcesView(onHeader: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val store = remember(context) { NativeHarvesterStore(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var pendingRemove by remember { mutableStateOf<NativeSavedStream?>(null) }

    DisposableEffect(lifecycle) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) { entries = store.all() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    if (pendingRemove != null) {
        val item = pendingRemove!!
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remover da biblioteca Web?") },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = {
                    store.remove(item.url)
                    entries = store.all()
                    pendingRemove = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancelar") } }
        )
    }
    LazyColumn(modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item { onHeader() }
        item {
            Text("Web · " + entries.size + " fontes guardadas",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp))
            Text("Referências da Web, não downloads offline. Para fontes temporárias, abre a página de origem e volta a analisar o vídeo no Harvester.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp))
        }
        if (entries.isEmpty()) item {
            Text("Ainda não há vídeos da Web. No Harvester Browser, abre Vídeos encontrados e toca em + para confirmar a importação.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp))
        }
        items(entries, key = { it.url }) { entry ->
            ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.title, fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (entry.webOnly) "Página guardada · fonte a redescobrir"
                        else "Vídeo direto · " + entry.quality.ifBlank { "qualidade automática" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.kind != "movie")
                        Text("Temporada " + entry.season + " · Episódio " + entry.episode,
                            style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val link = if (entry.webOnly) entry.pageUrl else entry.url
                            val intent = if (entry.webOnly) Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                else Intent(HarvesterContract.ACTION_PLAY).apply {
                                    setClassName(context.packageName,
                                        "com.ivor.openstream.harvester.HarvesterPlaybackActivity")
                                    putExtra(HarvesterContract.EXTRA_URL, entry.url)
                                    putExtra(HarvesterContract.EXTRA_TITLE, entry.title)
                                    putExtra(HarvesterContract.EXTRA_PAGE_URL, entry.pageUrl)
                                    putExtra(HarvesterContract.EXTRA_MIME_TYPE, entry.mime)
                                }
                            runCatching { context.startActivity(intent) }
                        }) {
                            Icon(if (entry.webOnly) Icons.Default.OpenInBrowser else Icons.Default.PlayArrow,
                                contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (entry.webOnly) "Abrir página" else "Reproduzir")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { pendingRemove = entry }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover referência")
                        }
                    }
                }
            }
        }
    }
}
