package com.ivor.openstream.presentation.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

/** Mobile and Android TV share add-on preferences, installed in the TV source library. */
@Composable
internal fun LiveTvSourcesDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    var ricos by remember { mutableStateOf(LiveTvAddons.ricosEnabled(context)) }
    var nova by remember { mutableStateOf(LiveTvAddons.configured(context, "nova")) }
    var custom by remember { mutableStateOf(LiveTvAddons.configured(context, "custom")) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fontes de TV ao vivo") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("IPTV-org · Canais públicos por país (já instalado).")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Canal Ricos (Brasil) · Stremio", modifier = Modifier.weight(1f))
                    Switch(checked = ricos, onCheckedChange = { ricos = it })
                }
                Text("Nova Channels · exige chave LiveDebrid. Configura em nova.livebx.net e cola o manifest personalizado.")
                OutlinedTextField(
                    value = nova,
                    onValueChange = { nova = it; invalid = false },
                    label = { Text("Nova Channels · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("AIOLiveTV ou outro addon Stremio TV: configura no respetivo serviço e cola o manifest.")
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it; invalid = false },
                    label = { Text("Outro addon Live TV · manifest.json") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("IPTV Brasil: o manifest antigo está offline (Not Found). Não é ativado automaticamente.")
                Text("As fontes são consultadas quando abres TV ao vivo. Algumas exigem conta ou direitos de transmissão.")
                if (invalid) Text("Usa endereços HTTPS válidos terminados em /manifest.json.")
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!LiveTvAddons.configure(context, "nova", nova) ||
                    !LiveTvAddons.configure(context, "custom", custom)) {
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
