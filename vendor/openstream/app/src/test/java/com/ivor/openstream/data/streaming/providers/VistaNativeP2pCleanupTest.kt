package com.ivor.openstream.data.streaming.providers

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaNativeP2pCleanupTest {
    private val videoA = "0123456789abcdef0123456789abcdef01234567"
    private val videoB = "abcdef0123456789abcdef0123456789abcdef01"

    @Test fun removesOnlyTheSelectedVideoCache() {
        val temp = Files.createTempDirectory("vista-p2p-test-").toFile()
        try {
            val engineCache = temp.resolve("vista-p2p-pieces")
            val a = engineCache.resolve("payload/$videoA").apply { mkdirs() }
            val b = engineCache.resolve("payload/$videoB").apply { mkdirs() }
            a.resolve("segment.bin").writeText("video A")
            b.resolve("segment.bin").writeText("video B")
            val artwork = temp.resolve("poster-cache.jpg").apply { writeText("keep artwork") }

            assertTrue(purgeIsolatedTorrentPayload(engineCache, videoA))
            assertFalse(a.exists())
            assertTrue(b.resolve("segment.bin").exists())
            assertEquals("video B", b.resolve("segment.bin").readText())
            assertEquals("keep artwork", artwork.readText())
        } finally {
            temp.deleteRecursively()
        }
    }

    @Test fun clearsNativeTemporaryDataButPreservesSharedCachesAndDownloads() {
        val root = Files.createTempDirectory("vista-p2p-startup-").toFile()
        try {
            val pieces = root.resolve("vista-p2p-pieces/payload/$videoA").apply { mkdirs() }
            val engine = root.resolve("vista-p2p-engine/config").apply { mkdirs() }
            pieces.resolve("segment.bin").writeText("torrent data")
            engine.resolve("metadata.bin").writeText("torrent metadata")
            root.resolve("shared-player-cache.bin").writeText("keep shared video cache")
            root.resolve("poster.jpg").writeText("keep artwork")
            root.resolve("downloads").apply { mkdirs() }.resolve("offline.mp4").writeText("keep downloads")

            assertTrue(purgeNativeP2pTemporaryData(root))
            assertFalse(root.resolve("vista-p2p-pieces").exists())
            assertFalse(root.resolve("vista-p2p-engine").exists())
            assertEquals("keep shared video cache", root.resolve("shared-player-cache.bin").readText())
            assertEquals("keep artwork", root.resolve("poster.jpg").readText())
            assertEquals("keep downloads", root.resolve("downloads/offline.mp4").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun cleanupCannotFollowDirectorySymlinksOutsideCache() {
        val root = Files.createTempDirectory("vista-p2p-symlink-").toFile()
        val outside = Files.createTempDirectory("outside-vista-p2p-").toFile()
        try {
            outside.resolve("important.txt").writeText("untouched")
            Files.createSymbolicLink(root.resolve("vista-p2p-engine").toPath(), outside.toPath())
            assertFalse(purgeNativeP2pTemporaryData(root))
            assertEquals("untouched", outside.resolve("important.txt").readText())
        } finally {
            root.resolve("vista-p2p-engine").delete()
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun doesNotAcceptPathTraversalOrUnknownTorrentIds() {
        val root = Files.createTempDirectory("vista-p2p-test-").toFile()
        try {
            assertNull(isolatedTorrentPayloadDirectory(root, "../../elsewhere"))
            assertNull(isolatedTorrentPayloadDirectory(root, "not-a-torrent"))
            assertFalse(purgeIsolatedTorrentPayload(root, "../secret"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun normalizedHashCannotEscapeTheP2pFolder() {
        val root = Files.createTempDirectory("vista-p2p-test-").toFile()
        try {
            val a = isolatedTorrentPayloadDirectory(root, videoA.uppercase())
            assertEquals(root.resolve("payload/$videoA").canonicalPath, a?.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
