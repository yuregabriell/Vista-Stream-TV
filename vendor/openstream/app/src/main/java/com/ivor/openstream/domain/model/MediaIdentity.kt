package com.ivor.openstream.domain.model

data class MediaIdentity(
    val tmdbId: Int,
    val tmdbType: String,
    val imdbId: String? = null,
    val anilistId: Int? = null,
    val malId: Int? = null,
    val title: String,
    val originalTitle: String? = null,
    val season: Int = 1,
    val episode: Int = 1,
    val year: Int? = null
) {
    val cacheKey: String
        get() = if (tmdbId > 0) {
            "$tmdbType:$tmdbId:$season:$episode"
        } else {
            // Keyless catalog must never let two unrelated titles share tv:0:1:1.
            val unique = imdbId?.takeIf { it.isNotBlank() }
                ?: anilistId?.let { "anilist:$it" }
                ?: malId?.let { "mal:$it" }
                ?: "${title.lowercase().trim()}:${year ?: 0}"
            "$tmdbType:$unique:$season:$episode"
        }
}
