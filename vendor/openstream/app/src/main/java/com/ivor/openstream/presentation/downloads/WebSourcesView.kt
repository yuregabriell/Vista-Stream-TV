package com.ivor.openstream.presentation.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ImageNotSupported
import com.ivor.openstream.data.catalog.KeylessTitle
import com.ivor.openstream.presentation.catalog.KeylessCatalogScreen
import com.ivor.openstream.presentation.catalog.KeylessCatalogViewModel
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
    var openTitle by remember { mutableStateOf<NativeSavedStream?>(null) }
    val catalog: KeylessCatalogViewModel = hiltViewModel()
    var details by remember { mutableStateOf<Map<String,KeylessTitle>>(emptyMap()) }

    // A confirmed match already has IMDb/TMDB/AniList catalog identity.
    // Retrieve artwork for older saved entries that predate stored posters.
    LaunchedEffect(entries.map { it.url to it.savedAt }) {
        for (entry in entries.filter {
            !it.catalogSourceId.isNullOrBlank() || !it.imdbId.isNullOrBlank()
                || it.tmdbId!=null || it.anilistId!=null
        }.take(30)) {
            if(!details.containsKey(entry.url)) {
                val linked=catalog.resolveImportedTitle(entry)
                details=details+(entry.url to linked)
            }
        }
    }
    if(openTitle!=null){
        val selected=openTitle!!
        val resolved=details[selected.url]
        if(resolved!=null){
            KeylessCatalogScreen(
                initialTitle=resolved,
                initialSeason=if(selected.kind=="movie")1 else selected.season,
                initialEpisode=if(selected.kind=="movie")1 else selected.episode,
                initialSourceUrl=selected.url,
                onInitialDismiss={ openTitle=null }
            )
        } else {
            Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement=Arrangement.spacedBy(16.dp)) {
                TextButton(onClick={openTitle=null}) { Text("← Voltar a Downloads + Web") }
                CircularProgressIndicator()
                Text("A encontrar o título no catálogo do VistaStream…")
            }
        }
        return
    }

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
            val metadata=details[entry.url]
            val linked= !entry.catalogSourceId.isNullOrBlank() ||
                entry.tmdbId!=null || !entry.imdbId.isNullOrBlank() ||
                entry.anilistId!=null
            ElevatedCard(
                Modifier.fillMaxWidth().padding(bottom=12.dp)
                    .then(if(linked)Modifier.clickable {openTitle=entry} else Modifier)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement=Arrangement.spacedBy(14.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    val poster=metadata?.poster ?: entry.poster
                    Box(Modifier.size(width=86.dp,height=125.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment=Alignment.Center) {
                        if(!poster.isNullOrBlank()){
                            AsyncImage(
                                model=poster,contentDescription="Cartaz de ${entry.title}",
                                contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                        }else {
                            Icon(Icons.Default.Movie,contentDescription=null,
                                tint=MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.size(35.dp))
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text(metadata?.title ?: entry.title,
                            fontWeight=FontWeight.SemiBold,maxLines=2,
                            overflow=TextOverflow.Ellipsis)
                        if(entry.kind!="movie")Text(
                            "Temporada ${entry.season} · Episódio ${entry.episode}",
                            style=MaterialTheme.typography.labelMedium,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when {
                                entry.webOnly -> "Referência Web · sem vídeo confirmado"
                                entry.temporary -> "VistaBrowser · fonte temporária"
                                linked -> "VistaBrowser · fonte associada"
                                else -> "Vídeo da Web · não associado"
                            },
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(linked)Row(verticalAlignment=Alignment.CenterVertically){
                            Text("Abrir título e episódio",
                                style=MaterialTheme.typography.labelMedium,
                                color=MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward,null,
                                tint=MaterialTheme.colorScheme.primary,
                                modifier=Modifier.size(15.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,bottom=10.dp),
                    verticalAlignment=Alignment.CenterVertically){
                    TextButton(onClick={
                        val link=if(entry.webOnly)entry.pageUrl else entry.url
                        val intent=if(entry.webOnly)Intent(Intent.ACTION_VIEW,Uri.parse(link))
                        else Intent(HarvesterContract.ACTION_PLAY).apply{
                            setClassName(context.packageName,
                                "com.ivor.openstream.harvester.HarvesterPlaybackActivity")
                            putExtra(HarvesterContract.EXTRA_URL,entry.url)
                            putExtra(HarvesterContract.EXTRA_TITLE,entry.title)
                            putExtra(HarvesterContract.EXTRA_PAGE_URL,entry.pageUrl)
                            putExtra(HarvesterContract.EXTRA_MIME_TYPE,entry.mime)
                        }
                        runCatching {context.startActivity(intent)}
                    }) {
                        Icon(if(entry.webOnly)Icons.Default.OpenInBrowser
                            else Icons.Default.PlayArrow,contentDescription=null)
                        Spacer(Modifier.width(6.dp))
                        Text(if(entry.webOnly)"Abrir página" else "Reproduzir")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick={pendingRemove=entry}){
                        Icon(Icons.Default.DeleteOutline,contentDescription="Remover referência")
                    }
                }
            }
        }
    }
}
