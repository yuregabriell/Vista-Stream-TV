package com.ivor.openstream.data.streaming.providers

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

/**
 * Optional TorrServer transport for Stremio infoHash/fileIdx sources.
 *
 * TorrServer does the P2P piece buffering; the VS Media3 player only receives
 * an ordinary LAN HTTP stream. No torrent files are stored by the VS app.
 * A separately running TorrServer/TorrServe process is required.
 */
class TorrServerBridge(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vs_torrserver_v1", Context.MODE_PRIVATE)

    data class Config(val enabled: Boolean, val mode: String, val remote: String)

    fun config() = Config(
        enabled = prefs.getBoolean("enabled", false),
        mode = prefs.getString("mode", "auto").orEmpty(),
        remote = prefs.getString("remote", "").orEmpty()
    )

    fun configure(enabled: Boolean, mode: String, remote: String): Boolean {
        if (mode !in listOf("auto", "local", "remote")) return false
        val cleaned = remote.trim().trimEnd('/')
        if (cleaned.isNotBlank() && canonicalAddress(cleaned) == null) return false
        if (mode == "remote" && cleaned.isBlank()) return false
        prefs.edit().putBoolean("enabled", enabled).putString("mode", mode)
            .putString("remote", cleaned).apply()
        synchronized(this) { liveBase = null; lastProbeAt = 0 }
        return true
    }

    /**
     * Only loopback/RFC1918 or .local hosts: no arbitrary public server and
     * no plaintext streaming credentials sent to an internet endpoint.
     */
    fun canonicalAddress(raw: String): String? {
        val parsed = runCatching { URI(raw.trim().trimEnd('/')) }.getOrNull() ?: return null
        if (parsed.scheme !in listOf("http", "https") || parsed.userInfo != null ||
            parsed.rawQuery != null || parsed.rawFragment != null ||
            (parsed.path.isNotEmpty() && parsed.path != "/")) return null
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
        val ip = host.split('.')
        val privateIp = if (ip.size == 4 && ip.all { part -> part.toIntOrNull()?.let { n -> n in 0..255 } == true }) {
            val a = ip[0].toInt()
            val b = ip[1].toInt()
            a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) ||
                (a == 169 && b == 254)
        } else false
        if (!privateIp && host != "localhost" && host != "::1" && !host.endsWith(".local"))
            return null
        val port = parsed.port.takeIf { it in 1..65535 } ?: 8090
        return (parsed.scheme + "://" + (if (host.contains(":")) "[$host]" else host) + ":$port")
    }

    @Volatile private var liveBase: String? = null
    @Volatile private var lastProbeAt = 0L

    /** Call on a background thread. A health check never downloads torrent video data. */
    fun availableBase(): String? {
        val settings = config()
        if (!settings.enabled) return null
        val remote = settings.remote.takeIf { it.isNotBlank() }?.let(::canonicalAddress)
        val bases = when (settings.mode) {
            "remote" -> listOfNotNull(remote)
            "local" -> listOf("http://127.0.0.1:8090")
            else -> listOfNotNull(remote) + "http://127.0.0.1:8090"
        }.distinct()
        synchronized(this) {
            val cached = liveBase
            if (System.currentTimeMillis() - lastProbeAt < 25_000L &&
                cached != null && cached in bases) return cached
        }
        val working = bases.firstOrNull(::ping)
        synchronized(this) {
            liveBase = working
            lastProbeAt = System.currentTimeMillis()
        }
        return working
    }

    fun testConfiguredServer(): String {
        val settings = config()
        val bases = when (settings.mode) {
            "local" -> listOf("http://127.0.0.1:8090")
            "remote" -> listOfNotNull(canonicalAddress(settings.remote))
            else -> listOfNotNull(settings.remote.takeIf { it.isNotBlank() }?.let(::canonicalAddress)) +
                "http://127.0.0.1:8090"
        }
        val found = bases.firstOrNull(::ping)
        return if (found != null) "Servidor encontrado: $found" else
            "Nenhum TorrServer respondeu. Inicia TorrServe neste aparelho ou um TorrServer na rede."
    }

    private fun ping(base: String): Boolean = runCatching {
        val connection = URL("$base/echo").openConnection() as HttpURLConnection
        connection.connectTimeout = 1100
        connection.readTimeout = 1100
        connection.instanceFollowRedirects = false
        connection.requestMethod = "GET"
        try { connection.responseCode == 200 } finally { connection.disconnect() }
    }.getOrDefault(false)

    /**
     * Query uses only a validated info hash and an explicit Stremio file index.
     * The resulting localhost/LAN /stream route starts buffering on playback.
     * The optional filename is deliberately excluded from the URL.
     */
    fun playUrl(base: String, rawHash: String, fileIndex: Int): String? {
        val hash = rawHash.trim().lowercase(Locale.ROOT)
        if (!hash.matches(Regex("[a-f0-9]{40}")) || fileIndex !in 0..10_000 ||
            canonicalAddress(base) != base) return null
        val magnet = "magnet:?xt=urn:btih:$hash"
        return Uri.parse("$base/stream/video").buildUpon()
            .appendQueryParameter("link", magnet)
            .appendQueryParameter("index", fileIndex.toString())
            .appendQueryParameter("play", null)
            .build().toString()
    }
}
