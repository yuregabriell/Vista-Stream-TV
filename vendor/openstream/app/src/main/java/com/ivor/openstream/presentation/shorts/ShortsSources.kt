package com.ivor.openstream.presentation.shorts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Metadata registry, NOT a list of playable or licensed streams.
 * Provider APIs/TVBox/manifest endpoints are deliberately never executed
 * as arbitrary scripts, nor used to unlock subscription-only episodes.
 */
private data class ShortSource(
    val name: String,
    val type: String,
    val description: String,
    val url: String,
    val status: String
)

private val shortSources = listOf(
    ShortSource("YouTube · canais oficiais", "Plataformas",
        "Descoberta de dramas completos publicados oficialmente. O YouTube pode conter anúncios.",
        "https://www.youtube.com/results?search_query=official+short+drama+full+episodes", "Descoberta"),
    ShortSource("ReelShort", "Plataformas",
        "Catálogo de dramas verticais; alguns episódios são pagos. Não desbloqueia conteúdo.",
        "https://www.reelshort.com/", "Acesso externo"),
    ShortSource("ShortDrama Player", "Agregadores",
        "Projeto MIT com busca em Hongguo, ReelShort, DramaBox, DramaWave, NetShort e GoodShort. A versão aberta não liberta episódios pagos.",
        "https://github.com/zuomeizuoyang/shortdrama-player", "Código disponível"),
    ShortSource("SekaiDrama", "Agregadores",
        "Aplicação Next.js de catálogo e player de dramas verticais; disponibilidade do vídeo ainda não validada.",
        "https://github.com/Sansekai/SekaiDrama", "Referência"),
    ShortSource("Dracin Viewer", "Agregadores",
        "Exemplo Flask com catálogo ReelShort e player HLS; APIs não oficiais podem deixar de funcionar.",
        "https://github.com/AmmarrBN/dracinviewer", "Referência"),
    ShortSource("Sansekai API", "APIs",
        "Documenta descoberta de DramaBox, ReelShort, ShortMax, GoodShort, NetShort e outros. Demonstração sujeita a limite de pedidos.",
        "https://api.sansekai.my.id/", "Catálogo externo"),
    ShortSource("DramaBos API", "APIs",
        "Anuncia 42 plataformas e episódios HLS. Exige chave API; direitos e reprodução integral não verificados.",
        "https://github.com/dramabosapi/dramabos-api", "Requer configuração"),
    ShortSource("Hoshiyomi API", "APIs",
        "API agregadora com episódios de dramas curtos; requer chave e verificação de termos.",
        "https://github.com/beranalpa/dramabox-asia-api", "Requer configuração"),
    ShortSource("TVBox · Short dramas", "TVBox",
        "Configuração JSON com adaptadores para dramas curtos. Não é uma playlist M3U nem um addon Stremio nativo.",
        "https://github.com/hebijunge/tvbox-config/blob/main/short.json", "Adaptador não implementado"),
    ShortSource("TVBox · Coleção", "TVBox",
        "Configurações adicionais para Hongguo e sites de dramas. Requer ambiente próprio TVBox.",
        "https://github.com/qist/tvbox/blob/master/dianshi.json", "Adaptador não implementado"),
    ShortSource("Yukistreams · Stremio", "Stremio",
        "Addon que anuncia catálogo de dramas asiáticos e curtos. Fontes e direitos de episódios completos não confirmados.",
        "https://github.com/kunolabs/yukistreams-addon", "Manifesto não validado"),
    ShortSource("GuoguoApp", "Agregadores",
        "Cliente Flutter/Go de dramas curtos; referência para UX e integração de catálogos.",
        "https://github.com/loongmikey/guoguoapp", "Código disponível"),
    ShortSource("PeerTube · Sepia Search", "Livres",
        "Pesquisa aberta em redes PeerTube, com filtros por licença no painel Descobrir.",
        "https://sepiasearch.org/", "Descoberta integrada"),
    ShortSource("Internet Archive", "Livres",
        "Coleções de vídeos independentes; licenças e integridade dos episódios devem ser confirmadas obra a obra.",
        "https://archive.org/details/movies", "Descoberta externa"),
    ShortSource("LibreTube", "Ferramentas",
        "Referência técnica para UI e player de YouTube; não é licença para redistribuir vídeos.",
        "https://github.com/libre-tube/LibreTube", "Código de referência"),
    ShortSource("NewPipe", "Ferramentas",
        "Biblioteca de extração de metadados e referências de player; políticas e direitos permanecem aplicáveis.",
        "https://github.com/TeamNewPipe/NewPipe", "Código de referência")
)

@Composable
internal fun ShortsSourcesPanel(modifier: Modifier = Modifier) {
    var selectedType by remember { mutableStateOf("Todos") }
    val filters = listOf("Todos", "Plataformas", "Agregadores", "APIs", "TVBox",
        "Stremio", "Livres", "Ferramentas")
    val filtered = remember(selectedType) {
        shortSources.filter { selectedType == "Todos" || it.type == selectedType }
    }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 115.dp)
    ) {
        item {
            Text("Fontes de Shorts", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Fontes identificadas: pesquisa, metadados, APIs e manifestos. " +
                    "Estar listado não significa que os episódios pagos sejam gratuitos " +
                    "ou que já funcionem no nosso player.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filters) { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type) }
                    )
                }
            }
        }
        items(filtered, key = { it.url }) { source ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(source.type.uppercase(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(source.name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(source.description, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(source.status, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(source.url))
                        }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Copiar")
                        }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source.url)))
                        }) {
                            Text("Referência")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class ShortDiscoveryVideo(
    val title: String,
    val description: String,
    val pageUrl: String,
    val licence: String,
    val duration: Int
)

/** Searches a public, unauthenticated PeerTube INDEX, not paid drama players. */
private suspend fun discoverOpenVideos(query: String): List<ShortDiscoveryVideo> =
    withContext(Dispatchers.IO) {
        val url = URL("https://sepiasearch.org/api/v1/search/videos?search=" +
            URLEncoder.encode(query, "UTF-8") + "&start=0&count=20&nsfw=false")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 8500
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VistaStream/Shorts-Discovery")
        }
        try {
            if (connection.responseCode != 200) return@withContext emptyList()
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            val items = Json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray.orEmpty()
            items.mapNotNull { el ->
                val obj = el.jsonObject
                val licenceObj = obj["licence"]?.jsonObject
                val licenceId = licenceObj?.get("id")?.jsonPrimitive?.intOrNull ?: 9
                // 1-8: Creative Commons or public domain; 9 = all rights reserved.
                if (licenceId !in 1..8 || obj["isLive"]?.jsonPrimitive?.booleanOrNull == true)
                    return@mapNotNull null
                val pageUrl = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (!pageUrl.startsWith("https://")) return@mapNotNull null
                ShortDiscoveryVideo(
                    title = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    description = obj["description"]?.jsonPrimitive?.contentOrNull
                        ?: obj["truncatedDescription"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    pageUrl = pageUrl,
                    licence = licenceObj?.get("label")?.jsonPrimitive?.contentOrNull ?: "Creative Commons",
                    duration = obj["duration"]?.jsonPrimitive?.intOrNull ?: 0
                )
            }.filter { it.title.isNotBlank() }.take(16)
        } finally {
            connection.disconnect()
        }
    }

@Composable
internal fun ShortsDiscoverPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("short drama") }
    var submitted by remember { mutableStateOf("short drama") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<ShortDiscoveryVideo>>(emptyList()) }
    LaunchedEffect(submitted) {
        loading = true
        failed = false
        videos = try { discoverOpenVideos(submitted.trim().take(90)) }
        catch (_: Exception) { failed = true; emptyList() }
        loading = false
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Descobrir vídeos livres", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Text("Pesquisa no PeerTube. Resultados não são necessariamente séries completas; " +
                "ainda não entram automaticamente na biblioteca Shorts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Procurar dramas, curtas, séries…") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (query.isNotBlank()) submitted = query.trim() },
                enabled = !loading && query.isNotBlank()
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pesquisar")
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (failed || (!loading && videos.isEmpty())) {
            item {
                Text(
                    if (failed) "Pesquisa indisponível. Podes tentar novamente."
                    else "Não foram encontrados resultados com licença aberta nesta pesquisa.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(videos, key = { it.pageUrl }) { video ->
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(video.title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    Text(video.licence + if (video.duration > 0) " · ${video.duration / 60} min" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    if (video.description.isNotBlank()) {
                        Text(video.description, maxLines = 3,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.pageUrl)))
                    }) {
                        Text("Ver publicação original")
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.OpenInNew, null, Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}
