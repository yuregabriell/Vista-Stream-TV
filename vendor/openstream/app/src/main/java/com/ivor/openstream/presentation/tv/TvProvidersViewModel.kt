package com.ivor.openstream.presentation.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivor.openstream.data.remote.TvStreamingProvidersAddon
import com.ivor.openstream.data.remote.model.WatchProviderDto
import com.ivor.openstream.data.remote.model.WatchProviderRegionDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TvProvidersViewModel @Inject constructor(
    val addon: TvStreamingProvidersAddon
) : ViewModel() {
    val enabled: StateFlow<Boolean> = addon.enabled
    private val _availability = MutableStateFlow<WatchProviderRegionDto?>(null)
    val availability: StateFlow<WatchProviderRegionDto?> = _availability
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _providerDirectory = MutableStateFlow<List<WatchProviderDto>>(emptyList())
    val providerDirectory: StateFlow<List<WatchProviderDto>> = _providerDirectory

    fun setEnabled(enabled: Boolean) { addon.setEnabled(enabled) }

    fun loadTitle(mediaType: String, id: Int) {
        _availability.value = null
        if (!addon.enabled.value) return
        viewModelScope.launch {
            _loading.value = true
            _availability.value = runCatching { addon.availability(mediaType, id) }.getOrNull()
            _loading.value = false
        }
    }

    fun loadDirectory() {
        if (!addon.enabled.value) {
            _providerDirectory.value = emptyList()
            return
        }
        viewModelScope.launch {
            _providerDirectory.value = runCatching { addon.directory() }.getOrDefault(emptyList())
        }
    }
}
