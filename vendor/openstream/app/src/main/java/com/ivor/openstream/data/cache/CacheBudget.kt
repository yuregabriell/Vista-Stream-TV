package com.ivor.openstream.data.cache

import android.os.StatFs
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Where each cache lives, and how much of the disk it may take. */
object CacheBudget {
    /** Coil's default folder, kept so artwork cached before this setting existed survives. */
    const val IMAGE_DIR = "coil3_disk_cache"
    const val METADATA_DIR = "tmdb_http"

    // The phone's LARGE option must never allocate hundreds of MB on Android TV.
    // User downloads are saved separately and intentionally excluded.
    const val TV_ARTWORK_MAX_BYTES = 24L * 1024 * 1024
    const val TV_METADATA_MAX_BYTES = 16L * 1024 * 1024
    const val TV_P2P_DISK_MAX_BYTES = 128L * 1024 * 1024
    const val TV_P2P_MEMORY_MAX_BYTES = 16L * 1024 * 1024

    fun artworkBudget(wanted: Long, tv: Boolean): Long =
        if (tv) wanted.coerceAtMost(TV_ARTWORK_MAX_BYTES) else wanted

    fun metadataBudget(wanted: Long, tv: Boolean): Long =
        if (tv) wanted.coerceAtMost(TV_METADATA_MAX_BYTES) else wanted

    /**
     * Counts all disposable files under the app's cache folder. Android's Settings
     * cache figure includes more than Coil/OkHttp (e.g. native P2P payloads), so
     * this is deliberately more complete than those two library sizes alone.
     * No symlinks followed, no file contents read, no mutations.
     */
    fun usedBytes(directory: File): Long = runCatching {
        if (!directory.exists()) return@runCatching 0L
        var used = 0L
        Files.walkFileTree(directory.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) used += attrs.size()
                return FileVisitResult.CONTINUE
            }
        })
        used
    }.getOrDefault(0L)

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
