package com.ivor.openstream.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * `bytesFor` has to hold the cache to a budget on a device, and to degrade to the wanted size
 * whenever it can't measure anything: the folder chain doesn't exist yet, or `StatFs` fails.
 * On the JVM `StatFs` is unimplemented, so this exercises the degradation, not the free/10 cap.
 */
class CacheBudgetTest {

    @Test
    fun `a folder that is not on disk hands back the wanted size`() {
        // Nothing exists anywhere up this chain, so there is no disk to measure and no space to guard.
        val missing = File("openstream-cache-budget-missing").resolve("cache")
        val wanted = 300L * 1024 * 1024
        assertEquals(wanted, CacheBudget.bytesFor(missing, wanted))
    }

    @Test
    fun `a folder that exists but cannot be measured hands back the wanted size`() {
        // On a device StatFs answers here; on the JVM it throws and runCatching falls through.
        val existing = File(System.getProperty("java.io.tmpdir"), "openstream-cache-budget")
        val wanted = 50L * 1024 * 1024
        assertEquals(wanted, CacheBudget.bytesFor(existing, wanted))
    }
    @Test
    fun tvArtworkBudgetIsBoundedRegardlessOfMobilePreset() {
        val wanted = 700L * 1024 * 1024
        assertEquals(CacheBudget.TV_ARTWORK_MAX_BYTES,
            CacheBudget.artworkBudget(wanted, tv = true))
        assertEquals(wanted, CacheBudget.artworkBudget(wanted, tv = false))
    }

    @Test
    fun tvMetadataBudgetIsBoundedRegardlessOfMobilePreset() {
        val wanted = 700L * 1024 * 1024
        assertEquals(CacheBudget.TV_METADATA_MAX_BYTES,
            CacheBudget.metadataBudget(wanted, tv = true))
        assertEquals(wanted, CacheBudget.metadataBudget(wanted, tv = false))
    }

    @Test
    fun nativeP2pCacheDoesNotNeedHundredsOfMbOnTv() {
        assertEquals(64L * 1024 * 1024, CacheBudget.TV_P2P_DISK_MAX_BYTES)
        assertEquals(16L * 1024 * 1024, CacheBudget.TV_P2P_MEMORY_MAX_BYTES)
    }

    @Test
    fun countDisposableCacheWithoutDeletingAnyFile() {
        val directory = Files.createTempDirectory("vista-tv-cache").toFile()
        try {
            val folder = File(directory, "p2p").also { it.mkdirs() }
            val piece = File(folder, "sample-piece")
            piece.writeBytes(ByteArray(128))
            assertEquals(128L, CacheBudget.usedBytes(directory))
            assertTrue(piece.exists())
            assertEquals(128L, piece.length())
        } finally {
            directory.deleteRecursively()
        }
    }

}
