package com.ivor.openstream.data.catalog

/**
 * Display only episode metadata that has actually been returned by a catalog.
 * AniList reports a total episode count but often has no per-episode listing;
 * an unknown count is NOT a single episode.
 */
object KeylessEpisodeList {
    private const val MAX_REPORTED_EPISODES = 5_000

    fun forTitle(title: KeylessTitle, remote: List<KeylessEpisode>): List<KeylessEpisode> {
        if (title.type == "movie" || title.format.equals("MOVIE", ignoreCase = true)) {
            return emptyList()
        }
        if (remote.isNotEmpty()) {
            return remote.filter { it.season > 0 && it.number > 0 }
                .distinctBy { it.season to it.number }
                .sortedWith(compareBy({ it.season }, { it.number }))
        }
        // No guess: never manufacture episode 1 for a title with unknown count.
        val count = title.episodes?.coerceIn(0, MAX_REPORTED_EPISODES) ?: return emptyList()
        return (1..count).map { KeylessEpisode(1, it, "Episódio $it") }
    }
}
