package com.ivor.openstream.presentation.livetv

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** TV ao vivo: catálogo remoto por país, sem armazenar streams no app/servidor. */
internal data class Country(val code: String, val flag: String, val label: String)
internal data class Channel(val id: String, val name: String, val group: String, val logo: String, val url: String)

internal val countries = listOf(
    Country("PT", "🇵🇹", "Portugal"), Country("BR", "🇧🇷", "Brasil"),
    Country("US", "🇺🇸", "Estados Unidos"), Country("GB", "🇬🇧", "Reino Unido"),
    Country("ES", "🇪🇸", "Espanha"), Country("FR", "🇫🇷", "França"),
    Country("DE", "🇩🇪", "Alemanha"), Country("IT", "🇮🇹", "Itália"),
    Country("JP", "🇯🇵", "Japão"), Country("CA", "🇨🇦", "Canadá"),
    Country("MX", "🇲🇽", "México"), Country("AR", "🇦🇷", "Argentina"),
    Country("AO", "🇦🇴", "Angola"), Country("MZ", "🇲🇿", "Moçambique")
)

private val pageGradient = Brush.verticalGradient(
    listOf(Color(0xFF0A0B17), Color(0xFF171329), Color(0xFF090D18))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("vistastream_live_tv", Context.MODE_PRIVATE)
    }
    val deviceCountry = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT)
            .takeIf { code -> countries.any { it.code == code } } ?: "PT"
    }
    var countryCode by rememberSaveable {
        mutableStateOf(prefs.getString("country", deviceCountry) ?: deviceCountry)
    }
    val country = countries.firstOrNull { it.code == countryCode } ?: countries.first()
    var countryPickerOpen by remember { mutableStateOf(false) }
    var countrySearch by remember { mutableStateOf("") }
    var channelSearch by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf("Todos") }
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var favorites by remember(countryCode) {
        mutableStateOf(prefs.getStringSet("favorites_" + countryCode, emptySet())?.toSet() ?: emptySet())
    }
    val player = remember(context) { ExoPlayer.Builder(context).build() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = "A transmissão não está disponível nesta fonte."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(selectedChannel?.url) {
        playbackError = null
        player.stop()
        selectedChannel?.let {
            player.setMediaItem(MediaItem.fromUri(it.url))
            player.prepare()
            player.playWhenReady = true
        }
    }
    LaunchedEffect(countryCode, reload) {
        loading = true
        error = null
        channels = emptyList()
        selectedChannel = null
        selectedGroup = "Todos"
        try {
            channels = withContext(Dispatchers.IO) { readRemoteChannels(countryCode) }
            if (channels.isEmpty()) error = "Não foram encontrados canais reproduzíveis para este país."
        } catch (_: Exception) {
            error = "Não foi possível carregar os canais agora. Verifica a ligação e tenta novamente."
        } finally {
            loading = false
        }
    }

    val groups = remember(channels) {
        listOf("Todos", "Favoritos") + channels.map { it.group }.distinct().sorted().take(24)
    }
    val visible = remember(channels, selectedGroup, channelSearch, favorites) {
        channels.filter { ch ->
            (selectedGroup == "Todos" || (selectedGroup == "Favoritos" && ch.id in favorites)
                || selectedGroup == ch.group) &&
                (channelSearch.isBlank() || ch.name.contains(channelSearch, ignoreCase = true))
        }
    }
    fun setCountry(code: String) {
        countryCode = code
        prefs.edit().putString("country", code).apply()
        channelSearch = ""
        countryPickerOpen = false
    }
    fun toggleFavorite(ch: Channel) {
        favorites = if (ch.id in favorites) favorites - ch.id else favorites + ch.id
        prefs.edit().putStringSet("favorites_" + countryCode, favorites).apply()
    }

    Column(Modifier.fillMaxSize().background(pageGradient)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("VISTASTREAM • LIVE", color = Color(0xFFEBA8F7), style = MaterialTheme.typography.labelSmall)
                Text("TV ao vivo", color = Color.White, fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.headlineSmall)
            }
            IconButton(onClick = { reload++ }) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar canais", tint = Color.White)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF211A37))
        ) {
            Column(Modifier.padding(17.dp)) {
                Text("ESCOLHE O TEU PAÍS", color = Color(0xFFC3B3E9),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF34274D), RoundedCornerShape(16.dp))
                        .clickable { countrySearch = ""; countryPickerOpen = true }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(country.flag, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(country.label, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Ver canais disponíveis", color = Color(0xFFCFC6E0),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Selecionar país",
                        tint = Color.White)
                }
            }
        }

        if (selectedChannel != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141626))
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { activityContext -> PlayerView(activityContext).apply { this.player = player } }
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = Color(0xFFF16EA8))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selectedChannel!!.name, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("AO VIVO · " + country.label, color = Color(0xFFC4BED0),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { selectedChannel = null }) {
                        Text("✕", color = Color.White)
                    }
                }
                if (playbackError != null) {
                    Text(playbackError!!, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        color = Color(0xFFFFB4B4), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●  EM DIRETO", color = Color(0xFFFF76A6), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("Canais públicos · fonte externa", color = Color(0xFFB0A8C6),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        OutlinedTextField(
            value = channelSearch,
            onValueChange = { channelSearch = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Pesquisar canais", color = Color(0xFFAFA8BC)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { group ->
                FilterChip(
                    selected = selectedGroup == group,
                    onClick = { selectedGroup = group },
                    label = { Text(group) },
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF9D4AE0),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Canais de " + country.label, color = Color.White,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            Text(if (loading) "A carregar" else visible.size.toString() + " canais",
                color = Color(0xFFC4BCD4), style = MaterialTheme.typography.labelSmall)
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            error != null -> Column(
                Modifier.fillMaxWidth().weight(1f).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error!!, color = Color.White)
                Spacer(Modifier.height(15.dp))
                Button(onClick = { reload++ }) { Text("Tentar novamente") }
            }
            visible.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Não há canais nesta seleção.", color = Color(0xFFCFCCDB))
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(145.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { ch ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedChannel = ch },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedChannel?.id == ch.id) Color(0xFF352650) else Color(0xFF1B2030))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.CenterStart) {
                                    if (ch.logo.isNotBlank()) {
                                        AsyncImage(
                                            model = ch.logo,
                                            contentDescription = "Logótipo " + ch.name,
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        )
                                    } else Icon(Icons.Default.Public, null, tint = Color(0xFFD3B0EF))
                                }
                                IconButton(onClick = { toggleFavorite(ch) }, modifier = Modifier.size(31.dp)) {
                                    Icon(
                                        if (ch.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (ch.id in favorites) "Retirar favorito" else "Adicionar favorito",
                                        tint = if (ch.id in favorites) Color(0xFFFF73A9) else Color(0xFFB8B7CB),
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(9.dp))
                            Text(ch.name, color = Color.White, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                                minLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(ch.group, color = Color(0xFFB9B4C8),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (countryPickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { countryPickerOpen = false },
            containerColor = Color(0xFF141426)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text("Selecionar país", style = MaterialTheme.typography.headlineSmall,
                    color = Color.White, fontWeight = FontWeight.Bold)
                Text("Os canais são carregados do país escolhido.", color = Color(0xFFBEB7CA))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = countrySearch,
                    onValueChange = { countrySearch = it },
                    placeholder = { Text("Pesquisar país") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 36.dp)) {
                    items(countries.filter {
                        it.label.contains(countrySearch, ignoreCase = true) || it.code.contains(countrySearch, ignoreCase = true)
                    }) { option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { setCountry(option.code) }
                                .padding(vertical = 15.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(option.flag, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(14.dp))
                            Text(option.label, color = Color.White, modifier = Modifier.weight(1f))
                            if (countryCode == option.code) Text("✓", color = Color(0xFFDF9BFF))
                        }
                    }
                }
            }
        }
    }
}

/** IPTV-org provides public playlists by country; no local channel catalog is bundled. */
internal fun readRemoteChannels(countryCode: String): List<Channel> {
    require(countries.any { it.code == countryCode })
    val endpoint = URL("https://iptv-org.github.io/iptv/countries/" +
        countryCode.lowercase(Locale.ROOT) + ".m3u")
    val connection = (endpoint.openConnection() as HttpURLConnection).apply {
        connectTimeout = 12000
        readTimeout = 16000
        setRequestProperty("User-Agent", "VistaStream/LiveTV")
    }
    try {
        if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
        val result = mutableListOf<Channel>()
        var metadata: String? = null
        connection.inputStream.bufferedReader().use { reader ->
            reader.lineSequence().take(25000).forEach { raw ->
                val line = raw.trim()
                if (line.startsWith("#EXTINF:")) {
                    metadata = line
                } else if (metadata != null && line.isNotBlank() && !line.startsWith("#")) {
                    val uri = runCatching { Uri.parse(line) }.getOrNull()
                    val host = uri?.host?.lowercase(Locale.ROOT).orEmpty()
                    if ((uri?.scheme == "https" || uri?.scheme == "http") &&
                        host.isNotBlank() && host != "localhost" && host != "127.0.0.1" &&
                        !host.startsWith("192.168.") && !host.startsWith("10.")) {
                        val info = metadata.orEmpty()
                        val name = info.substringAfterLast(",").trim()
                            .ifBlank { readAttribute(info, "tvg-name") }
                        val group = readAttribute(info, "group-title").ifBlank { "Geral" }
                        val logo = readAttribute(info, "tvg-logo").takeIf { it.startsWith("https://") }.orEmpty()
                        if (name.isNotBlank() && line.length < 4096) {
                            val id = countryCode + "|" + name.lowercase(Locale.ROOT)
                            result.add(Channel(id, name.take(120), group.take(50), logo, line))
                        }
                    }
                    metadata = null
                }
            }
        }
        return result.distinctBy { it.id }.take(1500)
    } finally {
        connection.disconnect()
    }
}

private fun readAttribute(metadata: String, name: String): String =
    metadata.substringAfter(name + "=\"", "").substringBefore("\"").trim()
