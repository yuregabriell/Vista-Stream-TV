package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TitleImagesDto(
    @SerialName("logos") val logos: List<TitleLogoDto> = emptyList()
)

@Serializable
data class TitleLogoDto(
    @SerialName("file_path") val filePath: String,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0
)

/** Prefer an official Portuguese logo, English second, or language-neutral. */
fun TitleImagesDto.preferredLogoPath(): String? =
    logos.filter { it.filePath.startsWith("/") && it.width > 0 && it.height > 0 }
        .sortedWith(
            compareBy<TitleLogoDto> {
                when (it.language?.lowercase()) {
                    "pt" -> 0
                    "en" -> 1
                    null -> 2
                    else -> 3
                }
            }.thenByDescending { it.voteAverage }
                .thenByDescending { it.width }
        )
        .firstOrNull()?.filePath
