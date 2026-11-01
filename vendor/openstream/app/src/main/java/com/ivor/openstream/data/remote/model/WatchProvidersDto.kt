package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Availability is regional. TMDB/JustWatch data must be attributed in the app. */
@Serializable
data class WatchProvidersDto(
    val id: Int = 0,
    val results: Map<String, WatchProviderRegionDto> = emptyMap()
)

@Serializable
data class WatchProviderRegionDto(
    val link: String? = null,
    val flatrate: List<WatchProviderDto> = emptyList(),
    val free: List<WatchProviderDto> = emptyList(),
    val ads: List<WatchProviderDto> = emptyList(),
    val rent: List<WatchProviderDto> = emptyList(),
    val buy: List<WatchProviderDto> = emptyList()
)

@Serializable
data class WatchProviderDto(
    @SerialName("provider_id") val providerId: Int,
    @SerialName("provider_name") val providerName: String,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("display_priority") val displayPriority: Int = 999
)

@Serializable
data class WatchProviderDirectoryDto(val results: List<WatchProviderDto> = emptyList())
