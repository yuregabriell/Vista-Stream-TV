package com.ivor.openstream.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CatalogSetupError(
    onRetry: () -> Unit,
    onSaveKey: (String) -> Boolean,
    onOpenSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var key by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Catálogo indisponível", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "O catálogo de títulos usa o TMDB. As extensões instalam fontes de vídeo, mas não substituem o catálogo. " +
                    "Uma chave TMDB API v3 pode ser necessária nesta instalação. Verifica também a Internet.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("A chave gratuita é criada na tua própria conta do TMDB:", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { uriHandler.openUri("https://www.themoviedb.org/settings/api") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Abrir TMDB para obter chave API v3") }
            OutlinedTextField(
                value = key,
                onValueChange = { key = it; invalid = false },
                label = { Text("Chave TMDB v3") },
                placeholder = { Text("32 caracteres hexadecimais") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError = invalid,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            )
            if (invalid) Text("Usa a chave API v3 (32 caracteres), não o token v4.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { if (!onSaveKey(key)) invalid = true },
                enabled = key.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar e carregar catálogo") }
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Tentar novamente")
            }
            if (onOpenSources != null) {
                OutlinedButton(onClick = onOpenSources, modifier = Modifier.fillMaxWidth()) {
                    Text("Definições e extensões")
                }
            }
            Text("As fontes de reprodução podem ser configuradas em Definições → Extensões.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
