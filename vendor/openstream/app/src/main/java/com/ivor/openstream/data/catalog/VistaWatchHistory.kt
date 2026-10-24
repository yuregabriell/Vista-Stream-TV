package com.ivor.openstream.data.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class VistaWatchProgress(val positionMs: Long = 0L, val durationMs: Long = 0L) {
    val canResume: Boolean
        get() = positionMs > 15_000L && (durationMs <= 0L || positionMs < durationMs - 45_000L)
}

@Singleton
class VistaWatchHistory @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("vista_stream_online_progress_v1", Context.MODE_PRIVATE)
    private fun key(id: String, season: Int, episode: Int) = "$id:s$season:e$episode"

    fun read(id: String, season: Int, episode: Int): VistaWatchProgress {
        val key = key(id, season, episode)
        return VistaWatchProgress(
            prefs.getLong("$key:position", 0L),
            prefs.getLong("$key:duration", 0L)
        )
    }

    fun lastPlayedAt(id: String): Long = prefs.getLong("$id:lastPlayedAt", 0L)

    fun isInProgress(id: String): Boolean {
        val (season, episode) = lastEpisode(id) ?: return false
        return read(id, season, episode).canResume
    }

    fun lastEpisode(id: String): Pair<Int, Int>? {
        val season = prefs.getInt("$id:lastSeason", 0)
        val episode = prefs.getInt("$id:lastEpisode", 0)
        return if (season > 0 && episode > 0) season to episode else null
    }

    fun write(id: String, season: Int, episode: Int, positionMs: Long, durationMs: Long) {
        if (positionMs < 10_000L || durationMs <= 0L) return
        val position = if (positionMs >= durationMs * 95 / 100) 0L else positionMs
        val key = key(id, season, episode)
        prefs.edit()
            .putLong("$key:position", position)
            .putLong("$key:duration", durationMs)
            .putInt("$id:lastSeason", season)
            .putInt("$id:lastEpisode", episode)
            .putLong("$id:lastPlayedAt", System.currentTimeMillis())
            .apply()
    }
}
