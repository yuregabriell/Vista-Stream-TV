package com.ivor.openstream.presentation.livetv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** EPG is optional. Empty means the guide is unavailable, never fabricated programming. */
internal data class TvProgramme(
    val channelId: String, val title: String, val description: String,
    val startMs: Long, val endMs: Long
) {
    fun progress(now: Long = System.currentTimeMillis()) =
        ((now - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    fun startLabel() = clock(startMs)
    fun endLabel() = clock(endMs)
}

internal class LiveTvGuide private constructor(
    private val entries: Map<String, List<TvProgramme>>,
    val created: Long
) {
    private val keys = entries.keys.associateBy { normalize(it) }
    fun schedule(channel: Channel): List<TvProgramme> =
        entries[channel.tvgId] ?: entries[keys[normalize(channel.tvgId)]] ?:
        entries[keys[normalize(channel.name)]] ?: emptyList()

    fun now(channel: Channel, time: Long = System.currentTimeMillis()) =
        schedule(channel).firstOrNull { it.startMs <= time && it.endMs > time }

    fun next(channel: Channel, time: Long = System.currentTimeMillis()) =
        schedule(channel).firstOrNull { it.startMs > time }

    companion object {
        fun empty() = LiveTvGuide(emptyMap(), System.currentTimeMillis())
        fun of(data: List<TvProgramme>, names: Map<String, String>): LiveTvGuide {
            val map = data.groupBy { it.channelId }.mapValues { (_, list) -> list.sortedBy { it.startMs } }
                .toMutableMap()
            for ((id, name) in names) map[id]?.let { map.putIfAbsent(name, it) }
            return LiveTvGuide(map, System.currentTimeMillis())
        }
        private fun normalize(name: String): String =
            Normalizer.normalize(name.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), "")
                .removeSuffix("br").removeSuffix("hd")
    }
}

internal object LiveTvGuideProvider {
    private const val ROOT = "https://iptv-epg.org/files/epg-"
    private val available = setOf("br", "pt", "us", "gb", "es", "fr", "de", "it", "jp", "ca", "mx", "ar")
    private var cachedCode = ""
    private var cachedGuide: LiveTvGuide? = null
    private const val FRESH_MS = 45L * 60 * 1000

    suspend fun load(country: String): LiveTvGuide = withContext(Dispatchers.IO) {
        val code = country.lowercase(Locale.ROOT)
        if (code !in available) return@withContext LiveTvGuide.empty()
        synchronized(this@LiveTvGuideProvider) {
            cachedGuide?.takeIf { cachedCode == code && System.currentTimeMillis() - it.created < FRESH_MS }
                ?.let { return@withContext it }
        }
        val guide = runCatching { download(code) }
            .onFailure { Log.w("VistaEPG", "Guide unavailable: " + it.javaClass.simpleName) }
            .getOrElse { LiveTvGuide.empty() }
        synchronized(this@LiveTvGuideProvider) {
            cachedCode = code
            cachedGuide = guide
        }
        guide
    }

    private fun download(code: String): LiveTvGuide {
        val connection = URL(ROOT + code + ".xml.gz").openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "VistaStream-EPG")
        try {
            if (connection.responseCode !in 200..299) error("EPG HTTP " + connection.responseCode)
            val pull = XmlPullParserFactory.newInstance().newPullParser()
            val programmes = ArrayList<TvProgramme>()
            val channelNames = HashMap<String, String>()
            val now = System.currentTimeMillis()
            val from = now - 4 * 3600_000L
            val until = now + 36 * 3600_000L
            val input = LimitedInput(connection.inputStream, 12_000_000)
            GZIPInputStream(input).use { stream ->
                pull.setInput(stream, "UTF-8")
                var channelId = ""
                var programmeId = ""
                var start = 0L
                var stop = 0L
                var title = ""
                var description = ""
                var field = ""
                var count = 0
                while (pull.eventType != XmlPullParser.END_DOCUMENT && count < 130_000) {
                    when (pull.eventType) {
                        XmlPullParser.START_TAG -> when (pull.name) {
                            "channel" -> channelId = pull.getAttributeValue(null, "id").orEmpty()
                            "programme" -> {
                                programmeId = pull.getAttributeValue(null, "channel").orEmpty()
                                start = parseDate(pull.getAttributeValue(null, "start"))
                                stop = parseDate(pull.getAttributeValue(null, "stop"))
                                title = ""; description = ""; count++
                            }
                            else -> field = pull.name.orEmpty()
                        }
                        XmlPullParser.TEXT -> {
                            val value = pull.text.orEmpty().trim()
                            if (value.isNotEmpty() && programmeId.isEmpty() && channelId.isNotEmpty() &&
                                field == "display-name") channelNames.putIfAbsent(channelId, value.take(120))
                            if (value.isNotEmpty() && programmeId.isNotEmpty()) when (field) {
                                "title" -> title = (title + value).take(200)
                                "desc" -> description = (description + value).take(800)
                            }
                        }
                        XmlPullParser.END_TAG -> when (pull.name) {
                            "channel" -> { channelId = ""; field = "" }
                            "programme" -> {
                                if (programmeId.isNotBlank() && title.isNotBlank() &&
                                    stop > from && start < until && stop > start) {
                                    programmes.add(TvProgramme(programmeId, title, description, start, stop))
                                }
                                programmeId = ""; field = ""
                            }
                            else -> field = ""
                        }
                    }
                    pull.next()
                }
            }
            return LiveTvGuide.of(programmes, channelNames)
        } finally { connection.disconnect() }
    }

    private fun parseDate(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.length < 14) return 0
        val fmt = SimpleDateFormat(
            if (raw.length >= 19) "yyyyMMddHHmmss Z" else "yyyyMMddHHmmss", Locale.US
        )
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        fmt.isLenient = false
        return runCatching { fmt.parse(raw)?.time ?: 0L }.getOrDefault(0L)
    }

    private class LimitedInput(input: java.io.InputStream, val limit: Int) :
        java.io.FilterInputStream(input) {
        private var total = 0
        override fun read(): Int {
            val n = super.read()
            if (n >= 0 && ++total > limit) error("EPG size exceeded")
            return n
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) { total += n; if (total > limit) error("EPG size exceeded") }
            return n
        }
    }
}

internal fun clock(timeMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
