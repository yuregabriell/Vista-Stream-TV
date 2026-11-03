package com.ivor.openstream.data.cache

import android.os.StatFs
import java.io.File

/** Where each cache lives, and how much of the disk it may take. */
object CacheBudget {
    /** Coil's default folder, kept so artwork cached before this setting existed survives. */
    const val IMAGE_DIR = "coil3_disk_cache"
    const val METADATA_DIR = "tmdb_http"

    // The phone's LARGE option must never allocate hundreds of MB on Android TV.
    // User downloads are saved separately and intentionally excluded.
    const val TV_ARTWORK_MAX_BYTES = 24L * 1024 * 1024
    const val TV_METADATA_MAX_BYTES = 16L * 1024 * 1024
    const val TV_P2P_DISK_MAX_BYTES = 64L * 1024 * 1024
    const val TV_P2P_MEMORY_MAX_BYTES = 16L * 1024 * 1024

    fun artworkBudget(wanted: Long, tv: Boolean): Long =
        if (tv) wanted.coerceAtMost(TV_ARTWORK_MAX_BYTES) else wanted

    fun metadataBudget(wanted: Long, tv: Boolean): Long =
        if (tv) wanted.coerceAtMost(TV_METADATA_MAX_BYTES) else wanted

    private const val MIN_BYTES = 10L * 1024 * 1024

    /**
     * [wanted] bytes, but never more than a tenth of the free space where [dir] lives: on a nearly
     * full phone the cache must not be what fills it. Android may also clear the cache folder
     * itself when space runs low; both caches cope with files disappearing.
     */
    fun bytesFor(dir: File, wanted: Long): Long {
        val free = runCatching {
            val existing = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() } ?: return wanted
            StatFs(existing.path).availableBytes
        }.getOrNull() ?: return wanted
        return wanted.coerceAtMost(free / 10).coerceAtLeast(MIN_BYTES)
    }
}
