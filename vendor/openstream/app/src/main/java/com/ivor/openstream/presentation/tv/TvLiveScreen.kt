package com.ivor.openstream.presentation.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
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
import com.ivor.openstream.presentation.livetv.LiveTvGuideProvider
import com.ivor.openstream.presentation.livetv.LiveTvSourcesDialog
import com.ivor.openstream.presentation.livetv.TvProgramme
import com.ivor.openstream.presentation.livetv.clock
import com.ivor.openstream.presentation.livetv.countries
import com.ivor.openstream.presentation.livetv.readRemoteChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private val liveBlack = Color(0xFF0A0C15)
private val livePanel = Color(0xFF191B28)
private val liveAccent = Color(0xFFE1A5FF)
private val muted = Color(0xFFAFACC2)
private val liveBackdrop = Brush.horizontalGradient(
    listOf(Color(0xFF0B1020), Color(0xFF18142A), Color(0xFF0E101E))
)

/** Ten-foot channel browser: library, linear guide and details, no mobile card grid. */
@Composable
internal fun TvLiveScreen(onPlayChannel: (Channel, List<Channel>, Country) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vistastream_live_tv", Context.MODE_PRIVATE) }
    val localeCountry = remember {
        Locale.getDefault().country.uppercase(Locale.ROOT)
            .takeIf { code -> countries.any { it.code == code } } ?: "BR"
    }
    var countryCode by rememberSaveable { mutableStateOf(prefs.getString("country", localeCountry) ?: localeCountry) }
    val country = countries.firstOrNull { it.code == countryCode } ?: countries.first()
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var guide by remember(countryCode) { mutableStateOf(LiveTvGuide.empty()) }
    var loading by remember { mutableStateOf(true) }
    var loadingGuide by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf("Todos") }
    var highlightedId by remember { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var sourcesOpen by remember { mutableStateOf(false) }
    var favourites by remember(countryCode) {
        mutableStateOf(prefs.getStringSet("favorites_" + countryCode, emptySet())?.toSet() ?: emptySet())
    }

    LaunchedEffect(countryCode, refresh) {
        loading = true; failed = false; channels = emptyList()
        selectedCategory = "Todos"; highlightedId = ""
        val public = withContext(Dispatchers.IO) {
            runCatching { readRemoteChannels(countryCode) }.getOrDefault(emptyList())
        }
        val addons = LiveTvAddons.channels(context, countryCode)
        channels = (public + addons).distinctBy { it.id }
        failed = channels.isEmpty()
        loading = false
    }
    LaunchedEffect(countryCode, refresh) {
        loadingGuide = true
        guide = LiveTvGuideProvider.load(countryCode)
        loadingGuide = false
    }

    val groups = remember(channels) {
        (listOf("Todos", "Favoritos") + channels.map { it.group }.distinct().sorted()).distinct()
    }
    val visible = remember(channels, selectedCategory, favourites) {
        channels.filter {
            selectedCategory == "Todos" ||
                (selectedCategory == "Favoritos" && it.id in favourites) ||
                selectedCategory == it.group
        }
    }
    LaunchedEffect(visible) {
        if (visible.none { it.id == highlightedId }) highlightedId = visible.firstOrNull()?.id.orEmpty()
    }
    val active = visible.firstOrNull { it.id == highlightedId } ?: visible.firstOrNull()

    fun changeCountry(code: String) {
        if (countryCode != code) {
            countryCode = code
            prefs.edit().putString("country", code).apply()
        }
        pickerOpen = false
    }
    fun toggleFavourite(channel: Channel) {
        favourites = if (channel.id in favourites) favourites - channel.id else favourites + channel.id
        prefs.edit().putStringSet("favorites_" + countryCode, favourites).apply()
    }

    Column(Modifier.fillMaxSize().background(liveBackdrop).padding(start = 23.dp, end = 32.dp, top = 15.dp, bottom = 17.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("VISTASTREAM  /  LIVE", color = liveAccent,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                Text("Televisão ao vivo", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            TvPill("Canais", selected = !showGuide, onClick = { showGuide = false })
            Spacer(Modifier.width(9.dp))
            TvPill("Guia TV", selected = showGuide, onClick = { showGuide = true })
            Spacer(Modifier.width(18.dp))
            TvPill(country.flag + " " + country.label, icon = Icons.Default.Public, onClick = { pickerOpen = true })
            Spacer(Modifier.width(9.dp))
            TvPill("Fontes", onClick = { sourcesOpen = true })
            Spacer(Modifier.width(9.dp))
            TvPill("Atualizar", icon = Icons.Default.Refresh, onClick = { refresh++ })
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (loading) "A carregar canais…" else visible.size.toString() + " canais", color = muted)
            Spacer(Modifier.weight(1f))
            Text(
                if (loadingGuide) "A consultar programação…" else
                    if (guide.schedule(active ?: Channel("", "", "", "", "")).isEmpty())
                        "Guia de programação opcional" else "Guia atualizado",
                color = muted, style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(15.dp))

        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            // First vertical rail: no horizontal chip overflow on a television.
            Column(
                Modifier.width(162.dp).fillMaxHeight()
                    .background(Color(0x9F0D1020), RoundedCornerShape(20.dp))
                    .padding(11.dp)
            ) {
                Text("CATEGORIAS", modifier = Modifier.padding(7.dp),
                    color = muted, style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it }) { group ->
                        TvPill(
                            group, selected = selectedCategory == group,
                            onClick = { selectedCategory = group }
                        )
                    }
                }
            }
            if (showGuide) {
                // Full-size programme grid: genuine XMLTV slots, empty data stays empty.
                TvProgrammeGuide(
                    channels = visible, guide = guide,
                    onChannel = { onPlayChannel(it, visible, country) },
                    onHighlight = { highlightedId = it.id },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            } else {
                Column(
                    Modifier.width(302.dp).fillMaxHeight()
                        .background(Color(0xA1151625), RoundedCornerShape(20.dp))
                        .padding(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("CANAIS", color = Color.White,
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(country.code, color = liveAccent, style = MaterialTheme.typography.labelMedium)
                    }
                    if (loading) {
                        TvLoading(Modifier.fillMaxSize())
                    } else if (failed || visible.isEmpty()) {
                        Text("Sem canais disponíveis nesta seleção.", color = muted, modifier = Modifier.padding(12.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(visible, key = { it.id }) { channel ->
                                TvChannelRow(
                                    channel = channel,
                                    programme = guide.now(channel),
                                    selected = channel.id == highlightedId,
                                    onFocused = { highlightedId = channel.id },
                                    onClick = { onPlayChannel(channel, visible, country) }
                                )
                            }
                        }
                    }
                }
                TvChannelDetails(
                    channel = active,
                    country = country,
                    programme = active?.let(guide::now),
                    next = active?.let(guide::next),
                    favourite = active?.id in favourites,
                    onFavourite = { active?.let(::toggleFavourite) },
                    onPlay = { active?.let { onPlayChannel(it, visible, country) } },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }
    if (pickerOpen) {
        Dialog(onDismissRequest = { pickerOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxWidth(.58f).fillMaxHeight(.8f)
                    .background(Color(0xFF1A1827), RoundedCornerShape(24.dp)).padding(26.dp)
            ) {
                Text("Selecionar país", color = Color.White,
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Escolhe a região para atualizar canais e programação.", color = muted)
                Spacer(Modifier.height(18.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(countries, key = { it.code }) { option ->
                        TvPill(option.flag + "  " + option.label,
                            selected = option.code == countryCode,
                            onClick = { changeCountry(option.code) })
                    }
                }
            }
        }
    }
    if (sourcesOpen) {
        LiveTvSourcesDialog(
            onDismiss = { sourcesOpen = false },
            onSaved = { sourcesOpen = false; refresh++ }
        )
    }
}

@Composable
private fun TvChannelRow(
    channel: Channel, programme: TvProgramme?, selected: Boolean,
    onFocused: () -> Unit, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused || it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(15.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xFF3C3154) else Color(0xFF242435),
            contentColor = Color.White,
            focusedContainerColor = Color(0xFF7850A7),
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.035f)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 57.dp, height = 49.dp)
                )
            } else {
                Text("◉", color = liveAccent, modifier = Modifier.width(57.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = Color.White,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(programme?.title ?: "Sem programação",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

@Composable
private fun TvChannelDetails(
    channel: Channel?, country: Country, programme: TvProgramme?, next: TvProgramme?,
    favourite: Boolean, onFavourite: () -> Unit, onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF282039), Color(0xFF141726), Color(0xFF111320))),
            RoundedCornerShape(23.dp)
        ).padding(27.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("AGORA NA TV", color = liveAccent,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (channel == null) {
            Text("Escolhe um canal para ver os detalhes.", color = muted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (channel.logo.isNotBlank()) AsyncImage(
                model = channel.logo, contentDescription = null,
                modifier = Modifier.size(width = 96.dp, height = 70.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(channel.name, color = Color.White,
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(country.flag + "  " + country.label + "  ·  " + channel.sourceLabel,
                    color = muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(programme?.title ?: "Sem programação disponível",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = Color.White)
        if (programme != null) {
            Text(programme.startLabel() + " — " + programme.endLabel(),
                color = Color(0xFFE9D0F5), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { programme.progress() },
                modifier = Modifier.fillMaxWidth(),
                color = liveAccent,
                trackColor = Color(0xFF39354A)
            )
            if (programme.description.isNotBlank()) Text(
                programme.description, color = Color(0xFFD1C9DA),
                style = MaterialTheme.typography.bodyMedium, maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text("Esta fonte ainda não tem horários associados no guia.",
                color = muted, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        TvPill("▶  Assistir canal", onClick = onPlay)
        TvPill(if (favourite) "♥  Remover favorito" else "♡  Adicionar aos favoritos",
            onClick = onFavourite)
        Spacer(Modifier.height(12.dp))
        if (next != null) {
            Text("A SEGUIR  ·  " + next.startLabel(), color = liveAccent,
                style = MaterialTheme.typography.labelMedium)
            Text(next.title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.weight(1f))
        Text("OK: assistir  ·  Guia TV: programação  ·  CH+/CH−: mudar canal",
            color = muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TvProgrammeGuide(
    channels: List<Channel>, guide: LiveTvGuide,
    onChannel: (Channel) -> Unit, onHighlight: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(Color(0xC1161826), RoundedCornerShape(21.dp)).padding(17.dp)) {
        Text("GUIA DE PROGRAMAÇÃO", color = Color.White,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Horários da fonte EPG · Seleciona uma faixa para assistir em direto",
            color = muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))
        if (channels.isEmpty()) {
            Text("Nenhum canal nesta seleção.", color = muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels, key = { it.id }) { channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { onHighlight(channel); onChannel(channel) },
                            modifier = Modifier.width(226.dp).height(76.dp),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF272639),
                                focusedContainerColor = Color(0xFF674888)
                            )
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (channel.logo.isNotBlank()) AsyncImage(
                                    model = channel.logo, contentDescription = null,
                                    modifier = Modifier.size(width = 45.dp, height = 40.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(channel.name, color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.width(7.dp))
                        val now = System.currentTimeMillis()
                        val schedule = guide.schedule(channel).filter {
                            it.endMs > now && it.startMs < now + 3 * 3600_000L
                        }.take(5)
                        if (schedule.isEmpty()) {
                            Text("  Sem programação", color = muted)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(schedule, key = { it.channelId + it.startMs }) { program ->
                                    TvGuideProgramme(program, onClick = {
                                        onHighlight(channel); onChannel(channel)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGuideProgramme(programme: TvProgramme, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(204.dp).height(76.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A293B),
            focusedContainerColor = Color(0xFF825BA4)
        )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(programme.startLabel() + " – " + programme.endLabel(),
                color = liveAccent, style = MaterialTheme.typography.labelSmall)
            Text(programme.title, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
