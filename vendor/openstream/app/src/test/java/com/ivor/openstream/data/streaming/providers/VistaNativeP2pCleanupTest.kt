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
