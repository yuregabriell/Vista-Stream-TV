package com.ivor.openstream.presentation.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.focus.onFocusChanged
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
import com.ivor.openstream.presentation.livetv.LiveTvAddons
import com.ivor.openstream.presentation.livetv.LiveTvGuide
import com.ivor.openstream.presentation.livetv.LiveProgramme
import com.ivor.openstream.presentation.livetv.LiveTvSourcesDialog
import com.ivor.openstream.presentation.livetv.countries
import com.ivor.openstream.presentation.livetv.readRemoteChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tvLiveGradient = Brush.horizontalGradient(
    listOf(Color(0xFF0B1020),Color(0xFF101427),Color(0xFF1B1430))
)
private val tvAccent = Color(0xFFE6A2FE)
private fun tvTime(t: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))

/** Premium 10-foot channel browser: categories, channel rail and cinematic channel preview. */
@Composable
internal fun TvLiveScreen(onPlayChannel: (Channel, List<Channel>, Country) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vistastream_live_tv", Context.MODE_PRIVATE) }
    val initial = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT)
            .takeIf { c -> countries.any { it.code == c } } ?: "PT"
    }
    var countryCode by rememberSaveable { mutableStateOf(prefs.getString("country", initial) ?: initial) }
    val country = countries.firstOrNull { it.code == countryCode } ?: countries.first()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var category by rememberSaveable { mutableStateOf("Todos") }
    var query by rememberSaveable { mutableStateOf("") }
    var countryPicker by remember { mutableStateOf(false) }
    var sourcesPicker by remember { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var schedules by remember { mutableStateOf<Map<String,List<LiveProgramme>>>(emptyMap()) }
    var favorites by remember(countryCode) {
        mutableStateOf(prefs.getStringSet("favorites_" + countryCode, emptySet())?.toSet() ?: emptySet())
    }

    LaunchedEffect(countryCode, refresh) {
        loading = true
        error = null
        channels = emptyList()
        previewId = null
        category = "Todos"
        val base = withContext(Dispatchers.IO) {
            runCatching { readRemoteChannels(countryCode) }.getOrDefault(emptyList())
        }
        val addons = runCatching { LiveTvAddons.channels(context, countryCode) }.getOrDefault(emptyList())
        channels = (base + addons).distinctBy { it.id }
        if (channels.isEmpty()) error = "Não foi possível carregar canais neste país."
        loading = false
    }
    LaunchedEffect(countryCode, refresh) {
        schedules = LiveTvGuide.programmes(context, countryCode)
    }

    fun changeCountry(code: String) {
        countryCode = code
        prefs.edit().putString("country", code).apply()
        countryPicker = false
        query = ""
    }

    fun favorite(ch: Channel) {
        favorites = if (ch.id in favorites) favorites - ch.id else favorites + ch.id
        prefs.edit().putStringSet("favorites_" + countryCode, favorites).apply()
    }
    val categories = remember(channels) {
        (listOf("Todos","Favoritos") + channels.map { it.group }.filter { it.isNotBlank() }
            .distinct().sorted()).distinct()
    }
    val filtered = remember(channels, category, query, favorites) {
        channels.filter { ch ->
            (category == "Todos" || category == ch.group ||
                (category == "Favoritos" && ch.id in favorites)) &&
                (query.isBlank() || ch.name.contains(query, ignoreCase = true))
        }
    }
    val preview = filtered.firstOrNull { it.id == previewId } ?: filtered.firstOrNull()
    val now = System.currentTimeMillis()
    val programmes = preview?.let { LiveTvGuide.forChannel(it, schedules) }.orEmpty()
    val onNow = programmes.firstOrNull { it.startMs <= now && it.endMs > now }
    val next = programmes.firstOrNull { it.startMs > now }

    Column(Modifier.fillMaxSize().background(tvLiveGradient).padding(start = 28.dp, top = 20.dp, end = 28.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VISTASTREAM  /  LIVE", color = tvAccent,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                Text("Televisão ao vivo", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            }
            TvPill(country.flag + " " + country.label + "  ▾", onClick = { countryPicker = true })
            Spacer(Modifier.width(10.dp))
            TvPill("Fontes", onClick = { sourcesPicker = true })
            Spacer(Modifier.width(10.dp))
            TvPill("Atualizar", onClick = { refresh++ }, icon = Icons.Default.Refresh)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // Column 1: easy category navigation like a television receiver.
            Column(Modifier.width(152.dp).fillMaxHeight()
                .background(Color(0x831B2033), RoundedCornerShape(19.dp))
                .padding(vertical = 18.dp, horizontal = 10.dp)) {
                Text("CATEGORIAS", color = Color(0xFFADA8C8),
                    modifier = Modifier.padding(start = 9.dp, bottom = 13.dp),
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories, key = { it }) { item ->
                        Surface(
                            onClick = { category = item; previewId = null },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(11.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (category == item) Color(0xFF5C3C72) else Color.Transparent,
                                contentColor = Color.White,
                                focusedContainerColor = Color(0xFFEEE8F9),
                                focusedContentColor = Color(0xFF161426)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f)
                        ) {
                            Text(item, Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Column 2: compact channel rows, not a grid of poster cards.
            Column(Modifier.width(325.dp).fillMaxHeight()
                .background(Color(0xAE151B2B), RoundedCornerShape(19.dp))
                .padding(horizontal = 13.dp, vertical = 14.dp)) {
                TvDpadTextInput(
                    value = query, placeholder = "Pesquisar canais",
                    title = "Pesquisar TV ao vivo", modifier = Modifier.fillMaxWidth(),
                    onSubmit = { query = it; previewId = null }
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canais", color = Color.White, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (loading) "…" else filtered.size.toString(),
                        color = Color(0xFFC6BBD2), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                when {
                    loading -> TvLoading(Modifier.fillMaxWidth().height(160.dp))
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(error.orEmpty(), color = Color(0xFFD8C8E2))
                        TvPill("Tentar novamente", onClick = { refresh++ })
                    }
                    filtered.isEmpty() -> Text("Nenhum canal nesta seleção.",
                        color = Color(0xFFB2AEC5))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(filtered, key = { it.id }) { ch ->
                            val isSelected = ch.id == preview?.id
                            Surface(
                                onClick = { onPlayChannel(ch, filtered, country) },
                                modifier = Modifier.fillMaxWidth().height(70.dp)
                                    .onFocusChanged { if (it.isFocused) previewId = ch.id },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color(0xFF463355) else Color(0xFF25283A),
                                    contentColor = Color.White,
                                    focusedContainerColor = Color(0xFFF0E9FC),
                                    focusedContentColor = Color(0xFF1A1725)
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f)
                            ) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text((filtered.indexOf(ch)+1).toString().padStart(3,'0'),
                                        modifier = Modifier.width(36.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold)
                                    if (ch.logo.isNotBlank()) AsyncImage(
                                        model = ch.logo, contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(width = 53.dp,height = 48.dp)
                                    ) else androidx.tv.material3.Icon(
                                        Icons.Default.Public, contentDescription = null,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ch.name, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text(ch.group, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (ch.id in favorites) Text("♥", color = Color(0xFFFF91BA))
                                }
                            }
                        }
                    }
                }
            }

            // Column 3: editorial details and live action, without fabricating screenshots.
            Box(Modifier.weight(1f).fillMaxHeight().background(
                Brush.verticalGradient(listOf(Color(0xFF342346),Color(0xFF1D1C34),Color(0xFF101726))),
                RoundedCornerShape(23.dp)
            )) {
                Column(Modifier.fillMaxSize().padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● EM DIRETO", color = Color(0xFFFF8DBB),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        Text(country.flag + " " + country.label, color = Color(0xFFCBC1D8),
                            style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(13.dp))
                    if (preview != null) {
                        Box(Modifier.fillMaxWidth().height(105.dp),
                            contentAlignment = Alignment.CenterStart) {
                            if (preview.logo.isNotBlank()) AsyncImage(
                                model = preview.logo, contentDescription = "Logótipo do canal",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(width = 180.dp, height = 95.dp)
                            ) else androidx.tv.material3.Icon(
                                Icons.Default.Public, contentDescription = null,
                                tint = Color(0xFFDFC3FF),modifier = Modifier.size(70.dp)
                            )
                        }
                        Text(preview.name, color = Color.White,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(preview.group + "  ·  " + preview.sourceLabel,
                            color = Color(0xFFC9BCD3), style = MaterialTheme.typography.bodyMedium)
                        TvPill("▶  Assistir em direto",
                            onClick = { onPlayChannel(preview, filtered, country) })
                        Text("AGORA NA PROGRAMAÇÃO", color = tvAccent,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text(onNow?.title ?: "Programação ainda não disponível",
                            color = Color.White, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold)
                        if (onNow != null) {
                            Text(tvTime(onNow.startMs) + " – " + tvTime(onNow.endMs),
                                color = Color(0xFFC7BED1))
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { ((now-onNow.startMs).toFloat() /
                                    (onNow.endMs-onNow.startMs).coerceAtLeast(1L)).coerceIn(0f,1f) },
                                color = tvAccent, trackColor = Color(0xFF62516B),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (next != null) Text(
                            "A seguir: " + tvTime(next.startMs) + " · " + next.title,
                            color = Color(0xFFBAB1C8),style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TvPill(
                            if (preview.id in favorites) "♥ Nos favoritos" else "♡ Adicionar favorito",
                            onClick = { favorite(preview) }
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Escolhe um canal para ver os detalhes.",
                                color = Color(0xFFCEC2D9))
                        }
                    }
                }
            }
        }
    }
    if (sourcesPicker) LiveTvSourcesDialog(
        onDismiss = { sourcesPicker = false },
        onSaved = { sourcesPicker = false; refresh++ }
    )
    if (countryPicker) {
        Dialog(
            onDismissRequest = { countryPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(Modifier.fillMaxWidth(.62f).fillMaxHeight(.81f)
                .background(Color(0xFF1C192D), RoundedCornerShape(24.dp))
                .padding(26.dp)) {
                Text("Escolhe um país", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(countries, key = { it.code }) { item ->
                        TvPill(item.flag + "  " + item.label,
                            selected = item.code == countryCode,
                            onClick = { changeCountry(item.code) })
                    }
                }
            }
        }
    }
}
