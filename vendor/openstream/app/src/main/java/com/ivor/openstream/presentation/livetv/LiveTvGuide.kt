package com.ivor.openstream.presentation.livetv

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** Real XMLTV metadata. Empty means no programme data, never fabricated programme names. */
internal data class LiveProgramme(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

internal object LiveTvGuide {
    private const val PREFS = "vistastream_live_tv"
    private var cacheKey = ""
    private var cacheTime = 0L
    private var cache: Map<String, List<LiveProgramme>> = emptyMap()

    fun guideUrl(context: Context, country: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("xmltv_" + country, "").orEmpty()

    fun saveGuideUrl(context: Context, country: String, raw: String): Boolean {
        val url = raw.trim()
        if (url.isNotEmpty() && !isSafeUrl(url)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("xmltv_" + country, url).apply()
        cacheKey = ""
        return true
    }

    private fun isSafeUrl(value: String): Boolean = runCatching {
        if (value.length !in 1..1800) return@runCatching false
        val u = URL(value)
        val h = u.host.lowercase(Locale.ROOT)
        u.protocol == "https" && u.userInfo == null && u.port in listOf(-1, 443) &&
            h.isNotBlank() && h != "localhost" && !h.endsWith(".local") &&
            !h.startsWith("127.") && !h.startsWith("10.") &&
            !h.startsWith("192.168.") && !h.startsWith("169.254.") &&
            !Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(h)
    }.getOrDefault(false)

    suspend fun programmes(context: Context, country: String): Map<String, List<LiveProgramme>> =
        withContext(Dispatchers.IO) {
            val raw = guideUrl(context, country)
            if (raw.isBlank() || !isSafeUrl(raw)) return@withContext emptyMap()
            val key = country + "|" + raw
            val now = System.currentTimeMillis()
            if (cacheKey == key && now - cacheTime < 30 * 60_000L) return@withContext cache
            val result = runCatching { parse(raw, now) }.getOrDefault(emptyMap())
            if (result.isNotEmpty()) {
                cacheKey = key
                cacheTime = now
                cache = result
            }
            result
        }

    private fun parse(raw: String, now: Long): Map<String, List<LiveProgramme>> {
        val con = (URL(raw).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 18_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/xml, text/xml")
            setRequestProperty("User-Agent", "VistaStream/LiveTV")
        }
        try {
            if (con.responseCode !in 200..299) return emptyMap()
            val source = con.inputStream.buffered()
            val data = if (raw.endsWith(".gz", true) || con.contentEncoding?.contains("gzip", true) == true)
                GZIPInputStream(source) else source
            val counted = object : java.io.FilterInputStream(data) {
                var bytesRead = 0L
                override fun read(): Int {
                    val x = super.read()
                    if (x >= 0 && ++bytesRead > 18_000_000L) error("XMLTV too large")
                    return x
                }
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    // Call underlying stream directly; super.read(byte[]) may call overridden read().
                    val n = this.`in`.read(b, off, len)
                    if (n > 0) bytesRead += n
                    if (bytesRead > 18_000_000L) error("XMLTV too large")
                    return n
                }
            }
            counted.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, "UTF-8")
                val out = linkedMapOf<String, MutableList<LiveProgramme>>()
                var channel = ""
                var start = 0L
                var end = 0L
                var title = ""
                var desc = ""
                var element: String? = null
                var accepted = 0
                while (parser.eventType != XmlPullParser.END_DOCUMENT && accepted < 35_000) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.name) {
                            "programme" -> {
                                channel = parser.getAttributeValue(null, "channel").orEmpty()
                                start = parseXmlTime(parser.getAttributeValue(null, "start").orEmpty())
                                end = parseXmlTime(parser.getAttributeValue(null, "stop").orEmpty())
                                title = ""; desc = ""
                            }
                            "title", "desc" -> element = parser.name
                        }
                        XmlPullParser.TEXT -> {
                            when (element) {
                                "title" -> title += parser.text.take(200)
                                "desc" -> desc += parser.text.take(450)
                            }
                        }
                        XmlPullParser.END_TAG -> when (parser.name) {
                            "title", "desc" -> element = null
                            "programme" -> {
                                if (channel.isNotBlank() && title.isNotBlank() &&
                                    start < now + 6 * 3600_000L && end > now - 2 * 3600_000L &&
                                    end > start) {
                                    out.getOrPut(channel.lowercase(Locale.ROOT)) { mutableListOf() }
                                        .add(LiveProgramme(channel, title.take(160), desc.take(400), start, end))
                                    accepted++
                                }
                                element = null
                            }
                        }
                    }
                    parser.next()
                }
                return out.mapValues { (_, v) -> v.sortedBy { it.startMs } }
            }
        } finally {
            con.disconnect()
        }
    }

    /** XMLTV start/stop: yyyyMMddHHmmss +0000, in provider timezone if offset absent. */
    private fun parseXmlTime(raw: String): Long {
        val compact = raw.trim().replace(Regex("\\s+"), " ")
        for (pattern in listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmss", "yyyyMMddHHmm")) {
            val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getDefault()
            }
            val parsed = runCatching { sdf.parse(compact) }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return 0L
    }

    fun forChannel(channel: Channel, programmes: Map<String, List<LiveProgramme>>): List<LiveProgramme> {
        val id = channel.tvgId.trim().lowercase(Locale.ROOT)
        val name = channel.name.trim().lowercase(Locale.ROOT)
        return programmes[id] ?: programmes[name] ?: emptyList()
    }
}
