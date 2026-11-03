package com.ivor.openstream.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionBatchTransferTest {
    @Test
    fun backupRoundTripKeepsRepositoryUrlsEnabledStatesAndPriority() {
        val input = ExtensionBackup(
            exportedAt = 123L,
            repositories = listOf("https://example.com/extensions/index.json"),
            installed = listOf(
                ExtensionBackupEntry("repo/one", enabled = false),
                ExtensionBackupEntry("repo/two", enabled = true)
            ),
            sourceOrder = listOf("repo/two", "repo/one")
        )
        val restored = ExtensionBatchTransfer.decode(ExtensionBatchTransfer.encode(input))
        assertEquals(input, restored)
        assertFalse(restored.installed.first().enabled)
        assertEquals("repo/two", restored.sourceOrder.first())
    }

    @Test
    fun repeatedEntriesAreDeduplicated() {
        val input = ExtensionBackup(
            repositories = listOf("https://example.com/x.json", "https://example.com/x.json"),
            installed = listOf(ExtensionBackupEntry("r/a", false), ExtensionBackupEntry("r/a", true)),
            sourceOrder = listOf("r/a", "r/a")
        )
        val restored = ExtensionBatchTransfer.decode(ExtensionBatchTransfer.encode(input))
        assertEquals(1, restored.repositories.size)
        assertEquals(1, restored.installed.size)
        assertFalse(restored.installed[0].enabled)
        assertEquals(listOf("r/a"), restored.sourceOrder)
    }

    @Test
    fun invalidFormatFailsBeforeApplyingChanges() {
        var rejected = false
        try {
            ExtensionBatchTransfer.decode("""{"format":"not-vista","version":1}""")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun oversizedBackupIsRejected() {
        var rejected = false
        try {
            ExtensionBatchTransfer.decode(" ".repeat(ExtensionBatchTransfer.MAX_BYTES + 1))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
