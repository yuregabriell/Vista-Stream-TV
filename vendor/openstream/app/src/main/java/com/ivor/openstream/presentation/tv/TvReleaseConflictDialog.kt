package com.ivor.openstream.presentation.tv

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton

/** Never mix up two TMDB titles or different profiles when remembering date-warning choices. */
internal object TvReleaseWarningPreferences {
    private const val FILE = "vista_tv_release_warning"
    fun isSuppressed(context: Context, mediaType: String, titleId: Int): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean("${mediaType}_${titleId}", false)

    fun setSuppressed(context: Context, mediaType: String, titleId: Int, suppressed: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("${mediaType}_${titleId}", suppressed).apply()
    }
}

/**
 * Air dates are supplied by a metadata provider, not proof that a resolved stream
 * cannot play. TV users may play anyway, and opt out of warnings for this title.
 */
@Composable
internal fun TvReleaseConflictDialog(
    airDate: String?,
    availableSources: Int,
    doNotWarn: Boolean,
    onToggleDoNotWarn: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val cancelFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 550.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Data de estreia por confirmar",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "A estreia indicada é ${airDate ?: "desconhecida"}, mas o catálogo pode ter " +
                        "uma data desatualizada. ${if (availableSources > 0) "Encontrámos $availableSources fonte(s) para este episódio." else "Ainda não há fontes verificadas nesta página."} " +
                        "Podes continuar e deixar o player verificar a reprodução.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onToggleDoNotWarn(!doNotWarn) }
                ) {
                    Icon(
                        if (doNotWarn) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    androidx.tv.material3.Text("Não voltar a avisar para este título")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.focusRequester(cancelFocus)) {
                        androidx.tv.material3.Text("Voltar")
                    }
                    Button(onClick = onContinue) {
                        androidx.tv.material3.Text("Continuar mesmo assim")
                    }
                }
            }
        }
    }
}
