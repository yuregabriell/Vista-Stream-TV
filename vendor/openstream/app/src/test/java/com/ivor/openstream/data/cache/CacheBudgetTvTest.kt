package com.ivor.openstream.data.cache

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class CacheBudgetTvTest {
    private val mb = 1024L * 1024L

    @Test fun tvHasSmallIndependentImageAndMetadataBudgets() {
        assertEquals(24L * mb, CacheBudget.artworkBudget(250L * mb, tv = true))
        assertEquals(16L * mb, CacheBudget.metadataBudget(50L * mb, tv = true))
        assertEquals(128L * mb, CacheBudget.TV_P2P_DISK_MAX_BYTES)
        assertEquals(16L * mb, CacheBudget.TV_P2P_MEMORY_MAX_BYTES)
    }

    @Test fun mobileSettingsStayUnchanged() {
        assertEquals(250L * mb, CacheBudget.artworkBudget(250L * mb, tv = false))
        assertEquals(50L * mb, CacheBudget.metadataBudget(50L * mb, tv = false))
    }

    @Test fun tvSettingsCountNativeTemporaryFilesAsCache() {
        val directory = Files.createTempDirectory("vista-cache-budget")
        try {
            val images = Files.createDirectory(directory.resolve("coil3_disk_cache"))
            val torrent = Files.createDirectory(directory.resolve("vista-p2p-pieces"))
            Files.write(images.resolve("artwork.bin"), ByteArray(128))
            Files.write(torrent.resolve("piece.bin"), ByteArray(256))
            assertEquals(384L, CacheBudget.usedBytes(directory.toFile()))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
