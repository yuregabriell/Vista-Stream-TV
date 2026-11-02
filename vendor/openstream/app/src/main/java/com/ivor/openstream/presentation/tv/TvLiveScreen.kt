package com.ivor.openstream.presentation.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import com.ivor.openstream.presentation.livetv.Channel
import com.ivor.openstream.presentation.livetv.Country
import com.ivor.openstream.presentation.livetv.countries
import com.ivor.openstream.presentation.livetv.readRemoteChannels
import com.ivor.openstream.presentation.livetv.LiveTvAddons
import com.ivor.openstream.presentation.livetv.LiveTvSourcesDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val liveGradient = Brush.verticalGradient(
    listOf(Color(0xFF090B18), Color(0xFF171027), Color(0xFF0C0E1C))
)
private val liveAccent = Color(0xFFDF9BFF)

/** Remote control first: separate ten-foot TV layout, shared channel catalog with mobile. */
@Composable
internal fun TvLiveScreen(onPlayChannel: (Channel, List<Channel>, Country) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vistastream_live_tv", Context.MODE_PRIVATE) }
    val initialCountry = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT)
            .takeIf { code -> countries.any { it.code == code } } ?: "PT"
    }
    var countryCode by rememberSaveable {
        mutableStateOf(prefs.getString("country", initialCountry) ?: initialCountry)
    }
    val country = countries.firstOrNull { it.code == countryCode } ?: countries.first()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("Todos") }
    var pickerOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var favorites by remember(countryCode) {
        mutableStateOf(prefs.getStringSet("favorites_" + countryCode, emptySet())?.toSet() ?: emptySet())
    }

    LaunchedEffect(countryCode, refreshToken) {
        loading = true
        loadError = null
        channels = emptyList()
        category = "Todos"
        try {
            val publicChannels = withContext(Dispatchers.IO) {
                runCatching { readRemoteChannels(countryCode) }.getOrDefault(emptyList())
            }
            val addOnChannels = LiveTvAddons.channels(context, countryCode)
            channels = (publicChannels + addOnChannels).distinctBy { it.id }
            if (channels.isEmpty()) loadError = "Nenhum canal disponível nesta fonte."
        } catch (_: Exception) {
            loadError = "Falha ao carregar canais. Verifica a ligação."
        } finally {
            loading = false
        }
    }

    fun selectCountry(newCode: String) {
        if (newCode != countryCode) {
            countryCode = newCode
            prefs.edit().putString("country", newCode).apply()
        }
        pickerOpen = false
    }
    fun toggleFavorite(channel: Channel) {
        favorites = if (channel.id in favorites) favorites - channel.id else favorites + channel.id
        prefs.edit().putStringSet("favorites_" + countryCode, favorites).apply()
    }
    val categories = remember(channels) {
        (listOf("Todos", "Favoritos") + channels.map { it.group }.distinct().sorted().take(22)).distinct()
    }
    val visible = remember(channels, category, favorites) {
        channels.filter {
            category == "Todos" || (category == "Favoritos" && it.id in favorites) || category == it.group
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(liveGradient)) {
        val columns = if (maxWidth >= 950.dp) 5 else if (maxWidth >= 690.dp) 4 else 3
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 30.dp, end = 42.dp, top = 26.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "live-hero") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("●  VISTASTREAM LIVE", color = liveAccent,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        TvPill("Fontes BR", onClick = { sourcesOpen = true }, icon = Icons.Default.Public)
                        Spacer(Modifier.width(10.dp))
                        TvPill("Atualizar", onClick = { refreshToken++ }, icon = Icons.Default.Refresh)
                    }
                    Spacer(Modifier.height(9.dp))
                    Text("Televisão ao vivo", color = Color.White,
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Escolhe o país e vê os canais disponíveis. Tudo num só lugar.",
                        color = Color(0xFFC3BAD3), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(23.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF482963), Color(0xFF211B38))),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("PAÍS SELECIONADO", color = Color(0xFFDCB5F0),
                                style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(country.flag + "  " + country.label, color = Color.White,
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Canais e categorias atualizados ao mudar de país.",
                                color = Color(0xFFCCC5D8), style = MaterialTheme.typography.bodySmall)
                        }
                        TvPill("Mudar país", onClick = { pickerOpen = true }, icon = Icons.Default.Public)
                    }
                }
            }
            item(key = "live-country-quick") {
                Column {
                    Text("PAÍSES", color = Color(0xFFB5ABCE), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(countries.take(9), key = { it.code }) { option ->
                            TvPill(option.flag + " " + option.label,
                                selected = option.code == countryCode,
                                onClick = { selectCountry(option.code) })
                        }
                    }
                }
            }
            item(key = "live-filters") {
                Column {
                    Text("CATEGORIAS", color = Color(0xFFB5ABCE),
                        style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(9.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        items(categories, key = { it }) { item ->
                            TvPill(item, selected = item == category, onClick = { category = item })
                        }
                    }
                }
            }
            item(key = "live-heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canais de " + country.label, color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(if (loading) "A carregar..." else visible.size.toString() + " canais",
                        color = Color(0xFFB5A9C9))
                }
            }
            if (loading) {
                item(key = "loading") {
                    TvLoading(Modifier.fillMaxWidth().height(200.dp))
                }
            } else if (loadError != null) {
                item(key = "error") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(loadError.orEmpty(), color = Color.White)
                        TvPill("Tentar novamente", onClick = { refreshToken++ },
                            icon = Icons.Default.Refresh)
                    }
                }
            } else if (visible.isEmpty()) {
                item(key = "empty") {
                    Text("Não existem canais nesta categoria.", color = Color(0xFFBFB5CE))
                }
            } else {
                itemsIndexed(visible.chunked(columns), key = { index, _ -> "row-" + index }) { _, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { channel ->
                            TvLiveChannelCard(
                                channel = channel,
                                favorite = channel.id in favorites,
                                onPlay = { onPlayChannel(channel, visible, country) },
                                onFavorite = { toggleFavorite(channel) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item(key = "note") {
                Text("Transmissões externas sujeitas a disponibilidade regional e autorizações.",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFF9990AC))
            }
        }
    }

    if (sourcesOpen) {
        LiveTvSourcesDialog(
            onDismiss = { sourcesOpen = false },
            onSaved = { sourcesOpen = false; refreshToken++ }
        )
    }
    if (pickerOpen) {
        Dialog(onDismissRequest = { pickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxWidth(.67f).fillMaxHeight(.83f)
                    .background(Color(0xFF191524), RoundedCornerShape(25.dp))
                    .padding(30.dp)
            ) {
                Text("Selecionar país", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Usa o comando para escolher um país.", color = Color(0xFFBCB2CE))
                Spacer(Modifier.height(18.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(countries, key = { it.code }) { option ->
                        TvPill(option.flag + "  " + option.label,
                            selected = option.code == countryCode,
                            onClick = { selectCountry(option.code) })
                    }
                }
            }
        }
    }


}

@Composable
private fun TvLiveChannelCard(
    channel: Channel,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().height(134.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(19.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF242037),
                contentColor = Color.White,
                focusedContainerColor = Color(0xFF654387),
                focusedContentColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("● AO VIVO", color = Color(0xFFFF9ABB),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (favorite) androidx.tv.material3.Icon(
                        Icons.Default.Favorite, contentDescription = "Favorito",
                        tint = Color(0xFFFFA5CD), modifier = Modifier.size(17.dp))
                }
                Box(Modifier.fillMaxWidth().height(61.dp), contentAlignment = Alignment.Center) {
                    if (channel.logo.isNotBlank()) {
                        AsyncImage(
                            model = channel.logo, contentDescription = "Logótipo " + channel.name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        androidx.tv.material3.Icon(
                            Icons.Default.Public, contentDescription = null,
                            tint = liveAccent, modifier = Modifier.size(33.dp)
                        )
                    }
                }
                Text(channel.name, color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        Text(channel.group + " · " + channel.sourceLabel, color = Color(0xFFAFA6C5),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        TvPill(if (favorite) "♥ Guardado" else "♡ Guardar", onClick = onFavorite)
    }
}
