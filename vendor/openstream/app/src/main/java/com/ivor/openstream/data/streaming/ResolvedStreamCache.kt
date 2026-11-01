package com.ivor.openstream.data.streaming

import android.os.SystemClock
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ServerResolution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The streams the player's last automatic searches found, so reopening an episode (or going back
 * to the previous one) starts without asking every source again.
 *
 * Kept in memory only and briefly ([TTL_MS]): stream links carry tokens that expire, and some are
 * tied to a session or an IP. No source documents how long its links last, which is why the entry
 * is also dropped as soon as one of its links fails to play ([forget]). An entry only answers for
 * the same set of sources, in the same order and search mode, that produced it.
 */
@Singleton
class ResolvedStreamCache internal constructor(private val clock: () -> Long) {

    @Inject
    constructor() : this({ SystemClock.elapsedRealtime() })

    private data class Entry(val sources: String, val resolution: ServerResolution, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    /** A complete resolution for [identity] found by [sources] less than [TTL_MS] ago, or null. */
    @Synchronized
    fun get(identity: MediaIdentity, sources: String): ServerResolution? {
        val key = identity.cacheKey
        val entry = entries[key] ?: return null
        if (entry.sources != sources || clock() - entry.storedAt !in 0L until TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.resolution
    }

    /** Only complete resolutions that found something are worth keeping. */
    @Synchronized
    fun put(identity: MediaIdentity, sources: String, resolution: ServerResolution) {
        if (!resolution.isComplete || resolution.servers.isEmpty()) return
        entries[identity.cacheKey] = Entry(sources, resolution, clock())
    }

    /**
     * A failed URL should not erase other known working candidates for the same episode.
     * Keep the original insertion timestamp: removing one stream must not renew stale links.
     */
    @Synchronized
    fun forgetServer(identity: MediaIdentity, serverId: String) {
        val key = identity.cacheKey
        val entry = entries[key] ?: return
        val remaining = entry.resolution.servers.filterNot { it.id == serverId }
        if (remaining.isEmpty()) {
            entries.remove(key)
        } else {
            entries[key] = entry.copy(resolution = entry.resolution.copy(servers = remaining))
        }
    }

    @Synchronized
    fun forget(identity: MediaIdentity) {
        entries.remove(identity.cacheKey)
    }

    @Synchronized
    fun clear() = entries.clear()

    internal companion object {
        const val TTL_MS = 10 * 60_000L
        const val MAX_ENTRIES = 24
    }
}
