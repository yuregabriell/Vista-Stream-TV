package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton

/**
 * Android TV D-pad text input. The non-editing surface is a normal TV button,
 * so focus traversal never invokes the system IME. Only pressing OK enters
 * editing; Back closes the dialog without modifying the current search.
 */
@Composable
fun TvDpadTextInput(
    value: String,
    placeholder: String,
    title: String,
    modifier: Modifier = Modifier,
    maxLength: Int = 160,
    onSubmit: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(value) }
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val commit: () -> Unit = {
        val selected = draft.trim()
        keyboard?.hide()
        editing = false
        onSubmit(selected)
    }
    OutlinedButton(
        modifier = modifier,
        onClick = {
            draft = value
            editing = true
        }
    ) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        androidx.tv.material3.Text(
            text = value.ifBlank { placeholder },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
    }
    if (editing) {
        LaunchedEffect(editing) {
            requester.requestFocus()
            keyboard?.show()
        }
        AlertDialog(
            onDismissRequest = {
                keyboard?.hide()
                editing = false
            },
            title = { Text(title) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(maxLength) },
                    modifier = Modifier.fillMaxWidth().focusRequester(requester),
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { commit() })
                )
            },
            confirmButton = { TextButton(onClick = commit) { Text("Pesquisar") } },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { draft = "" }) {
                        Text("Limpar")
                    }
                    TextButton(onClick = {
                        keyboard?.hide()
                        editing = false
                    }) { Text("Cancelar") }
                }
            }
        )
    }
}
