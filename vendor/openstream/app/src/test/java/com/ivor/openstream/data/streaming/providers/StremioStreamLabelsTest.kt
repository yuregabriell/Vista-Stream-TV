package com.ivor.openstream.data.streaming.providers

import org.junit.Assert.assertTrue
import org.junit.Test

class StremioStreamLabelsTest {
    @Test fun prefersFileResolutionToMisleadingDownloadLabel() {
        val parts = StremioStreamLabels.Parts(
            nameLines = listOf("1080p | 4.17 GB | Dual Áudio"),
            detailLines = emptyList(),
            filename = "Show.S01E01.2160p.WEB-DL.mkv"
        )
        val label = StremioStreamLabels.label(parts, "Show", "Vista")
        assertTrue(label.startsWith("4K"))
        assertTrue(label.contains("4.17 GB"))
    }

    @Test fun readsSizesProvidedOnlyInAddonName() {
        val parts = StremioStreamLabels.Parts(
            nameLines = listOf("1080p · 1.48 GB"),
            detailLines = emptyList(),
            filename = null
        )
        assertTrue(StremioStreamLabels.label(parts, "Movie", "Vista").contains("1.48 GB"))
    }
}
