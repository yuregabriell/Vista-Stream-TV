package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Button
import androidx.tv.material3.Surface

/** TV-native selection: one compact D-pad button, with a focusable modal list on OK. */
@Composable
internal fun TvFilterDropdown(
    label: String,
    selection: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            androidx.tv.material3.Text(label, style = androidx.tv.material3.MaterialTheme.typography.labelMedium)
            androidx.tv.material3.Text(selection, style = androidx.tv.material3.MaterialTheme.typography.titleMedium)
        }
        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Escolher $label", modifier = Modifier.size(24.dp))
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier.widthIn(max = 440.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(options) { index, option ->
                        if (index == selectedIndex) {
                            Button(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) {
                                androidx.tv.material3.Text(option, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            OutlinedButton(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) {
                                androidx.tv.material3.Text(option, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) {
                    androidx.tv.material3.Text("Voltar")
                }
            }
        }
    }
}
