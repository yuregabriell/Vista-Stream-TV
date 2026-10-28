package com.ivor.openstream.data.cast

/**
 * A single HTTP byte range. Cast receivers use suffix requests (bytes=-N) to find the
 * MP4 index at the end of a large file. All offsets use Long, including movies > 4 GiB.
 */
internal sealed interface CastRangeRequest {
    data class From(val start: Long, val endInclusive: Long?) : CastRangeRequest
    data class Suffix(val lastBytes: Long) : CastRangeRequest
}

internal data class CastResolvedRange(val start: Long, val endInclusive: Long?) {
    val length: Long?
        get() = endInclusive?.let { it - start + 1L }

    fun contentRange(total: Long?, actualEnd: Long?): String? {
        val end = actualEnd ?: endInclusive ?: total?.minus(1L)
        if (end == null || end < start) return null
        return "bytes $start-$end/${total ?: "*"}"
    }
}

/** Strict single-range parser. Never silently serve a multi-range request as a full file. */
internal object CastByteRanges {
    private val pattern = Regex("bytes=(\\d*)-(\\d*)", RegexOption.IGNORE_CASE)

    fun parse(header: String): CastRangeRequest? {
        val groups = pattern.matchEntire(header.trim())?.groupValues ?: return null
        val first = groups[1]
        val last = groups[2]
        if (first.isEmpty()) {
            val count = last.toLongOrNull() ?: return null
            return count.takeIf { it > 0 }?.let { CastRangeRequest.Suffix(it) }
        }
        val start = first.toLongOrNull() ?: return null
        val end = last.takeIf { it.isNotEmpty() }?.toLongOrNull()
        if (last.isNotEmpty() && end == null) return null
        if (end != null && end < start) return null
        // A range length must fit in a signed 64-bit value.
        if (end != null && end - start == Long.MAX_VALUE) return null
        return CastRangeRequest.From(start, end)
    }

    fun resolve(request: CastRangeRequest, knownTotal: Long?): CastResolvedRange? {
        val total = knownTotal?.takeIf { it > 0 }
        return when (request) {
            is CastRangeRequest.From -> {
                if (total != null && request.start >= total) return null
                val end = request.endInclusive?.let { end ->
                    if (total != null) minOf(end, total - 1L) else end
                }
                CastResolvedRange(request.start, end)
            }
            is CastRangeRequest.Suffix -> {
                if (total == null) return null
                CastResolvedRange((total - request.lastBytes).coerceAtLeast(0L), total - 1L)
            }
        }
    }
}
