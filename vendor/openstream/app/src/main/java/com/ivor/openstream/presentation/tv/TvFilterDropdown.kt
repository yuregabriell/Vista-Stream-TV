package com.ivor.openstream.presentation.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Button

/**
 * D-pad accessible TV dropdown: bounded to the viewport, independently
 * scrolling options and an always-visible Voltar action. On opening, focus
 * and scroll are restored to the CURRENT selection (even for 200 providers).
 */
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
    val trigger = remember { FocusRequester() }
    OutlinedButton(
        onClick = { open = true },
        modifier = modifier.fillMaxWidth().focusRequester(trigger)
    ) {
        Column(Modifier.weight(1f)) {
            androidx.tv.material3.Text(label, style = androidx.tv.material3.MaterialTheme.typography.labelMedium)
            androidx.tv.material3.Text(
                selection, maxLines = 1,
                style = androidx.tv.material3.MaterialTheme.typography.titleMedium
            )
        }
        Icon(
            Icons.Rounded.ArrowDropDown, contentDescription = "Escolher $label",
            modifier = Modifier.size(24.dp)
        )
    }
    LaunchedEffect(open) {
        if (!open) {
            withFrameNanos { }
            runCatching { trigger.requestFocus() }
        }
    }
    if (open) {
        val currentIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
        val currentFocus = remember { FocusRequester() }
        val backFocus = remember { FocusRequester() }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex)
        LaunchedEffect(currentIndex, options.size) {
            if (options.isNotEmpty()) {
                listState.scrollToItem(currentIndex)
                withFrameNanos { }
                withFrameNanos { }
                runCatching { currentFocus.requestFocus() }
            } else {
                withFrameNanos { }
                runCatching { backFocus.requestFocus() }
            }
        }
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.87f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${options.size} opções · utiliza ↑ ↓ para navegar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 6.dp)
                    ) {
                        itemsIndexed(options, key = { index, _ -> index }) { index, option ->
                            val isSelected = index == currentIndex
                            val controlModifier = Modifier.fillMaxWidth()
                                .then(if (isSelected) Modifier.focusRequester(currentFocus) else Modifier)
                            if (isSelected) {
                                Button(
                                    onClick = { onSelect(index); open = false },
                                    modifier = controlModifier
                                ) {
                                    androidx.tv.material3.Text(
                                        option, maxLines = 1, modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { onSelect(index); open = false },
                                    modifier = controlModifier
                                ) {
                                    androidx.tv.material3.Text(option, maxLines = 1)
                                }
                            }
                        }
                    }
                    // Not inside the scroll list. Never hidden behind overscan.
                    OutlinedButton(
                        onClick = { open = false },
                        modifier = Modifier.fillMaxWidth().focusRequester(backFocus)
                    ) {
                        androidx.tv.material3.Text("Voltar")
                    }
                }
            }
        }
    }
}
