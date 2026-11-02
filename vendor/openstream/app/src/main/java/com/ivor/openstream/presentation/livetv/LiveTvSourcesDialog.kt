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

/** Stremio TV sources are manifests, never untrusted code executed inside VistaStream. */
@Composable
internal fun LiveTvSourcesDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var ricos by remember { mutableStateOf(LiveTvAddons.ricosEnabled(context)) }
    var brasil by remember { mutableStateOf(LiveTvAddons.configured(context, "iptvbr")) }
    var nova by remember { mutableStateOf(LiveTvAddons.configured(context, "nova")) }
    var aio by remember { mutableStateOf(LiveTvAddons.configured(context, "aio")) }
    var custom by remember { mutableStateOf(LiveTvAddons.configured(context, "custom")) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fontes brasileiras · TV ao vivo") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("IPTV-org · Canais públicos por país — fonte integrada.")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Canal Ricos · Stremio")
                        Text("Fonte brasileira regional integrada; disponibilidade variável.")
                    }
                    Switch(checked = ricos, onCheckedChange = { ricos = it })
                }
                Text("IPTV Brasil · addon Stremio. O servidor antigo está indisponível; não ativa automaticamente canais que não funcionam.")
                OutlinedTextField(
                    value = brasil, onValueChange = { brasil = it; invalid = false },
                    label = { Text("IPTV Brasil · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                TextButton(onClick = { brasil = LiveTvAddons.IPTV_BR_LEGACY_MANIFEST; invalid = false }) {
                    Text("Usar o endereço histórico (experimental)")
                }
                Text("Nova Channels · requer manifesto configurado com a tua conta/chave no site do addon.")
                OutlinedTextField(
                    value = nova, onValueChange = { nova = it; invalid = false },
                    label = { Text("Nova Channels · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("AIOLiveTV · exige uma instância própria e configuração de fontes/EPG. Não existe servidor público oficial.")
                OutlinedTextField(
                    value = aio, onValueChange = { aio = it; invalid = false },
                    label = { Text("AIOLiveTV · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Outra fonte Stremio Live TV")
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it; invalid = false },
                    label = { Text("Manifest personalizado · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("As fontes são carregadas por catálogo e os streams são resolvidos ao reproduzir. Canais pagos exigem acesso autorizado.")
                if (invalid) Text("Utiliza URLs HTTPS válidas terminadas em /manifest.json.")
            }
        },
        confirmButton = {
            Button(onClick = {
                val entries = listOf(
                    "iptvbr" to brasil, "nova" to nova, "aio" to aio, "custom" to custom
                )
                if (entries.any { (key, value) -> !LiveTvAddons.configure(context, key, value) }) {
                    invalid = true
                } else {
                    LiveTvAddons.setRicosEnabled(context, ricos)
                    onSaved()
                }
            }) { Text("Guardar fontes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
