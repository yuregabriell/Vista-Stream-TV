package com.ivor.openstream.presentation.livetv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** User-selectable live addons: only public streams or sources the viewer is entitled to use. */
@Composable
internal fun LiveTvSourcesDialog(
    countryCode: String = "BR",
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var ricos by remember { mutableStateOf(LiveTvAddons.ricosEnabled(context)) }
    var iptvBrasil by remember { mutableStateOf(LiveTvAddons.iptvBrasilEnabled(context)) }
    var brManifest by remember { mutableStateOf(LiveTvAddons.configured(context, "iptvbr")) }
    var nova by remember { mutableStateOf(LiveTvAddons.configured(context, "nova")) }
    var aiolive by remember { mutableStateOf(LiveTvAddons.configured(context, "aiolive")) }
    var custom by remember { mutableStateOf(LiveTvAddons.configured(context, "custom")) }
    var epg by remember(countryCode) { mutableStateOf(LiveTvGuide.guideUrl(context, countryCode)) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fontes e guia de TV") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("IPTV-org · canais públicos por país · ativo")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canal Ricos (Stremio Brasil)", modifier = Modifier.weight(1f))
                    Switch(checked = ricos, onCheckedChange = { ricos = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("IPTV Brasil (Stremio) · experimental", modifier = Modifier.weight(1f))
                    Switch(checked = iptvBrasil, onCheckedChange = { iptvBrasil = it })
                }
                Text("O manifest público IPTV Brasil pode estar offline. A fonte só mostra canais se o serviço responder e oferecer streams compatíveis.")
                OutlinedTextField(
                    value = brManifest,
                    onValueChange = { brManifest = it; invalid = false },
                    label = { Text("IPTV Brasil · URL alternativa (opcional)") },
                    placeholder = { Text(LiveTvAddons.IPTV_BR_PRESET) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Nova Channels · requer manifest personalizado gerado com conta/chave LiveDebrid em nova.livebx.net.")
                OutlinedTextField(
                    value = nova,
                    onValueChange = { nova = it; invalid = false },
                    label = { Text("Nova · manifest personalizado") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("AIOLiveTV · requer uma instância configurada. Não existe servidor público oficial.")
                OutlinedTextField(
                    value = aiolive,
                    onValueChange = { aiolive = it; invalid = false },
                    label = { Text("AIOLiveTV · manifest da tua instância") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it; invalid = false },
                    label = { Text("Outro addon Stremio Live TV") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Programação (EPG) · " + countryCode + ": para ver horários reais no guia, cola uma fonte XMLTV autorizada. O guia não inventa programas.")
                OutlinedTextField(
                    value = epg,
                    onValueChange = { epg = it; invalid = false },
                    label = { Text("Guia XMLTV HTTPS (.xml ou .xml.gz)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (invalid) Text("Confere as URLs. Manifests devem usar HTTPS e terminar em /manifest.json; o guia XMLTV deve usar HTTPS.")
                Text("Estas fontes não contornam assinaturas, restrições regionais ou DRM. A instalação de um manifest não garante canais reproduzíveis.")
            }
        },
        confirmButton = {
            Button(onClick = {
                val ok =
                    LiveTvAddons.configure(context, "iptvbr", brManifest) &&
                    LiveTvAddons.configure(context, "nova", nova) &&
                    LiveTvAddons.configure(context, "aiolive", aiolive) &&
                    LiveTvAddons.configure(context, "custom", custom) &&
                    LiveTvGuide.saveGuideUrl(context, countryCode, epg)
                if (ok) {
                    LiveTvAddons.setRicosEnabled(context, ricos)
                    LiveTvAddons.setIptvBrasilEnabled(context, iptvBrasil)
                    onSaved()
                } else invalid = true
            }) { Text("Guardar fontes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
