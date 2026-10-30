package com.ivor.openstream.harvester

import android.content.Context
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Local-only list of the video URLs handed to Vista by Harvester/Android.
 * Do not persist temporary content:// permissions or auth headers/cookies.
 */
class HarvesterLibraryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "vista_harvester_library_v1", Context.MODE_PRIVATE
    )
    private val json = Json { ignoreUnknownKeys = true }

    private fun key(url: String): String = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .take(16).joinToString("") { "%02x".format(it) }

    fun all(): List<HarvesterMedia> = synchronized(this) {
        entries().sortedByDescending { it.savedAt }.take(80)
    }

    private fun entries(): List<HarvesterMedia> = prefs.getString("library", null)
        ?.let { runCatching { json.decodeFromString<List<HarvesterMedia>>(it) }.getOrNull() }
        .orEmpty()

    fun save(media: HarvesterMedia) = synchronized(this) {
        // content:// grants from shares are transient; never make a broken bookmark.
        val uri = runCatching { Uri.parse(media.url) }.getOrNull()
        if (uri?.scheme !in listOf("https", "http")) return@synchronized
        val previous = entries()
        val earlier = previous.firstOrNull { key(it.url) == key(media.url) }
        val item = media.copy(
            savedAt = System.currentTimeMillis(),
            positionMs = earlier?.positionMs ?: media.positionMs,
            durationMs = earlier?.durationMs ?: media.durationMs,
            // A referral header is kept as media metadata but never store cookies,
            // bearer tokens or other arbitrary request headers.
        )
        val next = (listOf(item) + previous.filterNot { key(it.url) == key(item.url) }).take(80)
        prefs.edit().putString("library", json.encodeToString(next)).apply()
    }

    /**
     * Import direct browser playback history from an explicitly paired VistaStream.
     * Only HTTP media URLs without auth tokens are transferable; never copy headers.
     * Keep local playback position unless peer progress was saved more recently.
     */
    fun mergeFromPeer(peer: List<HarvesterMedia>) = synchronized(this) {
        val local = entries().associateBy { key(it.url) }.toMutableMap()
        for (remote in peer.take(80)) {
            if (!NativeHarvesterStore.safeDirect(remote.url)) continue
            val identity = key(remote.url)
            val existing = local[identity]
            val cleaned = remote.copy(referer = null, userAgent = null, subtitleUrl = null)
            if (existing == null || cleaned.savedAt > existing.savedAt) {
                local[identity] = cleaned
            } else if (existing.positionMs == 0L && cleaned.positionMs > 0L) {
                local[identity] = existing.copy(
                    positionMs = cleaned.positionMs,
                    durationMs = cleaned.durationMs
                )
            }
        }
        val merged = local.values.sortedByDescending { it.savedAt }.take(80)
        prefs.edit().putString("library", json.encodeToString(merged)).apply()
    }

    fun progress(url: String, positionMs: Long, durationMs: Long) = synchronized(this) {
        if (durationMs <= 0 || positionMs < 5_000) return@synchronized
        val data = entries().toMutableList()
        val index = data.indexOfFirst { key(it.url) == key(url) }
        if (index < 0) return@synchronized
        val pos = if (positionMs >= durationMs * 95 / 100) 0L else positionMs
        data[index] = data[index].copy(positionMs = pos, durationMs = durationMs)
        prefs.edit().putString("library", json.encodeToString(data)).apply()
    }

    fun delete(url: String) = synchronized(this) {
        prefs.edit().putString(
            "library", json.encodeToString(entries().filterNot { key(it.url) == key(url) })
        ).apply()
    }
}
