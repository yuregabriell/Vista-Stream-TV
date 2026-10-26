package com.ivor.openstream.harvester

import com.ivor.openstream.data.catalog.KeylessTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSmartMatcherTest {
    @Test fun `extracts title and year from movie URL without quality or hash`() {
        val hints = ImportSmartMatcher.inspect("codigo vinganca ea99a493a",
            "https://example.org/filmes/codigo-vinganca-dublado-2026-1080p")
        assertEquals("codigo vinganca", hints.title)
        assertEquals(2026, hints.year)
        assertEquals(listOf("codigo vinganca"), hints.queries)
    }

    @Test fun `Portuguese alias beats unrelated vengeance film`() {
        val hints = ImportSmartMatcher.inspect("codigo vinganca ea99a493a",
            "https://example.org/codigo-vinganca-dublado-2026-1080p")
        val right = KeylessTitle(sourceId="tmdb:125", title="Mutiny",
            alternateTitle="Código: Vingança", type="movie", year=2026,tmdbId=125)
        val wrong = KeylessTitle(sourceId="cinemeta:tt000", title="Vingança",
            type="movie", year=2008)
        val ranked = ImportSmartMatcher.rank(listOf(wrong,right),hints,setOf("movie"))
        assertEquals(right, ranked.first().item)
        assertTrue(ranked.first().score > 90)
        assertTrue(ranked.last().score < ranked.first().score)
    }

    @Test fun `untrusted query values are never used as names`() {
        val hints = ImportSmartMatcher.inspect("A Viagem (2012)",
            "https://example.org/player.php?token=abc&title=outro-filme-2020")
        assertEquals("A Viagem",hints.title)
        assertEquals(2012,hints.year)
    }

    @Test fun `filters wrong content type and weak coincidence`() {
        val hints=ImportSmartMatcher.inspect("codigo vinganca",
            "https://example.org/codigo-vinganca-2026")
        val series=KeylessTitle(sourceId="anilist:5",title="Código Vingança",type="anime",year=2026)
        val other=KeylessTitle(sourceId="tmdb:5",title="Aventura Espacial",type="movie",year=2026)
        assertTrue(ImportSmartMatcher.rank(listOf(series,other),hints,setOf("movie")).isEmpty())
    }
}
