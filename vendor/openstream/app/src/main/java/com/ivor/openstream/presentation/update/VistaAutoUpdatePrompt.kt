package com.ivor.openstream.presentation.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Checks on application launch. Never downloads or installs an APK without consent.
 * Installation is always delegated to the Android package installer.
 */
@Composable
fun VistaAutoUpdatePrompt(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    if (dismissed) return

    when (val current = state) {
        is UpdateUiState.Available -> {
            if (current.asset == null) return
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("Nova versão do Vista Stream") },
                text = {
                    Text(
                        "Está disponível a ${current.release.tagName}. " +
                            "Queres descarregar esta atualização do GitHub e depois " +
                            "confirmar a instalação no Android?"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        accepted = true
                        viewModel.download(current.release, current.asset)
                    }) { Text("Descarregar atualização") }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) { Text("Mais tarde") }
                }
            )
        }
        is UpdateUiState.Downloading -> {
            if (!accepted) return
            AlertDialog(
                onDismissRequest = { },
                title = { Text("A descarregar atualização") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(current.release.tagName)
                        LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(current.progress * 100).toInt()}%")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancelDownload()
                        dismissed = true
                    }) { Text("Cancelar") }
                }
            )
        }
        is UpdateUiState.ReadyToInstall -> {
            if (!accepted) return
            // Never launch an installer that we already know will reject this APK.
            val signatureOk = remember(current.apk) { viewModel.signingCompatible(current.apk) }
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = {
                    Text(if (signatureOk == false) "Assinatura incompatível" else "Atualização pronta")
                },
                text = {
                    Text(
                        when (signatureOk) {
                            false -> "Esta APK tem uma assinatura diferente da versão instalada. " +
                                "Por segurança, o Android não permite instalá-la por cima. " +
                                "Não desinstales sem guardar primeiro os teus dados: " +
                                "o backup da biblioteca pode não incluir o progresso do catálogo online."
                            null -> "A APK foi descarregada, mas não foi possível confirmar a assinatura. " +
                                "O Android poderá recusar a instalação se a chave for diferente."
                            true -> "A APK foi descarregada e a assinatura é compatível. " +
                                "Confirma a instalação no Android para atualizar sem desinstalar."
                        }
                    )
                },
                confirmButton = {
                    if (signatureOk != false) {
                        Button(onClick = {
                            if (viewModel.canInstall()) viewModel.install(current.apk)
                            else context.startActivity(viewModel.installPermissionIntent())
                        }) { Text(if (viewModel.canInstall()) "Instalar agora" else "Autorizar instalação") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) {
                        Text(if (signatureOk == false) "Manter versão instalada" else "Mais tarde")
                    }
                }
            )
        }
        is UpdateUiState.Failed -> {
            if (!accepted) return
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("Falha na atualização") },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { dismissed = true }) { Text("Fechar") }
                }
            )
        }
        else -> Unit
    }
}
