package com.ivor.openstream.data.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastByteRangesTest {
    @Test fun suffixFindsIndexAtEndOfFiveGigabyteMovie() {
        val size = 5L * 1024 * 1024 * 1024
        val request = CastByteRanges.parse("bytes=-65536")!!
        val range = CastByteRanges.resolve(request, size)!!
        assertEquals(size - 65_536L, range.start)
        assertEquals(size - 1L, range.endInclusive)
        assertEquals(65_536L, range.length)
        assertEquals("bytes ${size - 65_536L}-${size - 1L}/$size",
            range.contentRange(size, size - 1L))
    }

    @Test fun suffixLargerThanFileReturnsWholeFile() {
        val range = CastByteRanges.resolve(CastByteRanges.parse("bytes=-500")!!, 100L)!!
        assertEquals(0L, range.start)
        assertEquals(99L, range.endInclusive)
        assertEquals(100L, range.length)
    }

    @Test fun boundedProbeDoesNotInventFileSize() {
        val range = CastByteRanges.resolve(CastByteRanges.parse("bytes=0-1")!!, null)!!
        assertEquals(2L, range.length)
        assertEquals("bytes 0-1/*", range.contentRange(null, 1L))
    }

    @Test fun seekingBeyondFourGigabytesWorks() {
        val start = 4_900_000_000L
        val range = CastByteRanges.resolve(CastByteRanges.parse("bytes=$start-")!!, 5_000_000_000L)!!
        assertEquals(start, range.start)
        assertEquals("bytes 4900000000-4999999999/5000000000",
            range.contentRange(5_000_000_000L, 4_999_999_999L))
    }

    @Test fun clampsEndToActualSize() {
        val range = CastByteRanges.resolve(CastByteRanges.parse("bytes=99-999")!!, 101L)!!
        assertEquals(100L, range.endInclusive)
        assertEquals(2L, range.length)
    }

    @Test fun rejectsInvalidOrUnsupportedRanges() {
        assertNull(CastByteRanges.parse("bytes=0-1,10-11"))
        assertNull(CastByteRanges.parse("bytes=-0"))
        assertNull(CastByteRanges.parse("bytes=10-2"))
        assertNull(CastByteRanges.parse("bytes=9223372036854775808-"))
        assertNull(CastByteRanges.resolve(CastByteRanges.parse("bytes=101-")!!, 101L))
        assertNull(CastByteRanges.resolve(CastByteRanges.parse("bytes=-10")!!, null))
    }
}
