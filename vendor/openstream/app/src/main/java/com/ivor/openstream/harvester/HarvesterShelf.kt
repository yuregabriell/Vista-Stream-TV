package com.ivor.openstream.harvester

import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun HarvesterShelf() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { HarvesterLibraryStore(context) }
    val nativeStore = remember(context) { NativeHarvesterStore(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var nativeEntries by remember { mutableStateOf(nativeStore.all()) }
    DisposableEffect(lifecycleOwner, store) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                entries = store.all()
                nativeEntries = nativeStore.all()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (entries.isEmpty() && nativeEntries.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Vídeos do Harvester",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                (entries.size + nativeEntries.size).toString() + " guardados",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (nativeEntries.isNotEmpty()) {
            Text("Do Harvester Browser · Guardados em Downloads + Web",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(nativeEntries.take(30), key = { it.url }) { video ->
                    Column(
                        modifier = Modifier.width(208.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(15.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(92.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(video.title, maxLines = 2, minLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleSmall)
                        Text(if(video.webOnly) "Página web · redescobrir fonte" else "Vídeo online",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                        // Link a captured source to the title's canonical metadata and episode.
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(context, HarvesterCatalogMatchActivity::class.java)
                                    .putExtra("url", video.url)
                            )
                        }) {
                            Text("Adicionar ao catálogo")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                val action = if(video.webOnly)
                                    Intent(Intent.ACTION_VIEW, Uri.parse(video.pageUrl))
                                else HarvesterContract.directPlay(
                                    HarvesterMedia(url=video.url,title=video.title,
                                        pageUrl=video.pageUrl,mimeType=video.mime),
                                    context.packageName
                                )
                                runCatching { context.startActivity(action) }
                            }, modifier = Modifier.weight(1f)) {
                                Text(if(video.webOnly) "Abrir página" else "Assistir")
                            }
                            IconButton(onClick = {
                                nativeStore.remove(video.url)
                                nativeEntries = nativeStore.all()
                            }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remover") }
                        }
                    }
                }
            }
        }
        if(entries.isNotEmpty()) LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(entries.take(30), key = { it.url }) { video ->
                Column(
                    modifier = Modifier.width(208.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer,
                            RoundedCornerShape(15.dp)
                        )
                        .clickable {
                            context.startActivity(HarvesterContract.directPlay(video, context.packageName))
                        }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Reproduzir vídeo do navegador",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        video.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        runCatching { Uri.parse(video.url).host }.getOrNull()
                            ?: "Vídeo online",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (video.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (video.positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    HarvesterContract.directPlay(video, context.packageName)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (video.resumable) "Continuar" else "Assistir")
                        }
                        IconButton(onClick = {
                            store.delete(video.url)
                            entries = store.all()
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Remover da biblioteca")
                        }
                    }
                }
            }
        }
    }
}
