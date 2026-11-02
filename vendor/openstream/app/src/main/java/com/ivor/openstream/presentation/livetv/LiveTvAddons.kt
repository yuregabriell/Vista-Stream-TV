package com.ivor.openstream.presentation.livetv

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Live-TV Stremio catalogs have type "tv"; movie add-ons are not used here. */
internal object LiveTvAddons {
    const val RICOS = "https://zoreu.github.io/ricosstremio/manifest.json"
    const val NOVA_CONFIG = "https://nova.livebx.net/"
    const val IPTV_BR_REFERENCE = "https://github.com/WillAcris/IPTV-BR"
    const val AIOLIVE_CONFIG = "https://mrcanelas.github.io/aiolivetv/"
    private const val PREFS = "vistastream_live_tv"
    private const val MAX_BYTES = 1_500_000

    fun ricosEnabled(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("ricos_enabled", true)

    fun setRicosEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("ricos_enabled", enabled).apply()
    }

    fun configured(context: Context, provider: String): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("manifest_" + provider, "").orEmpty()

    /** Blank removes the source; tokens, when required, stay local in SharedPreferences. */
    fun configure(context: Context, provider: String, raw: String): Boolean {
        if (provider !in listOf("nova", "custom")) return false
        val url = raw.trim().replaceFirst(Regex("^stremio://", RegexOption.IGNORE_CASE), "https://")
        if (url.isNotEmpty() && safeManifest(url) == null) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("manifest_" + provider, url).apply()
        return true
    }

    private fun safeManifest(value: String): URL? {
        if (value.length !in 1..2048) return null
        return runCatching {
            val url = URL(value)
            val host = url.host.lowercase(Locale.ROOT)
            if (url.protocol != "https" || url.userInfo != null ||
                url.port !in listOf(-1, 443) ||
                !url.path.endsWith("/manifest.json", ignoreCase = true) ||
                host == "localhost" || host.endsWith(".local") ||
                host.startsWith("127.") || host.startsWith("10.") ||
                host.startsWith("192.168.") || host.startsWith("169.254.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
            ) null else url
        }.getOrNull()
    }

    private fun endpoint(manifest: String, resource: String, type: String, id: String): URL {
        val url = safeManifest(manifest) ?: error("Invalid source")
        return URL(url.toString().removeSuffix("manifest.json") +
            resource + "/" + Uri.encode(type) + "/" + Uri.encode(id) + ".json")
    }

    private fun fetchJson(url: URL): JSONObject {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "VistaStream/LiveTV")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP " + conn.responseCode)
            val bytes = conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (output.size() + n > MAX_BYTES) error("Large addon response")
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            return JSONObject(String(bytes, Charsets.UTF_8))
        } finally { conn.disconnect() }
    }

    /** Keep IPTV-org plus enabled add-ons. A single offline server must not blank all channels. */
    suspend fun channels(context: Context, country: String): List<Channel> = withContext(Dispatchers.IO) {
        val addons = buildList {
            if (country == "BR" && ricosEnabled(context)) add("Canal Ricos" to RICOS)
            configured(context, "nova").takeIf { it.isNotBlank() }?.let { add("Nova Channels" to it) }
            configured(context, "custom").takeIf { it.isNotBlank() }?.let { add("Stremio TV" to it) }
        }
        val output = mutableListOf<Channel>()
        for ((label, manifest) in addons.distinctBy { it.second }) {
            runCatching { output += catalog(manifest, label, country) }
                .onFailure { Log.w("VistaLiveAddons", label + ": " + it.message) }
        }
        output.distinctBy { it.id }.take(1000)
    }

    private fun catalog(manifest: String, label: String, country: String): List<Channel> {
        val source = safeManifest(manifest) ?: return emptyList()
        val descriptor = fetchJson(source)
        val resources = descriptor.optJSONArray("resources") ?: return emptyList()
        if ((0 until resources.length()).none {
            resources.optString(it) == "catalog" || resources.optJSONObject(it)?.optString("name") == "catalog"
        }) return emptyList()
        val catalogs = descriptor.optJSONArray("catalogs") ?: return emptyList()
        val out = mutableListOf<Channel>()
        val aliases = when (country) {
            "BR" -> listOf("br", "brazil", "brasil")
            "PT" -> listOf("pt", "portugal")
            "US" -> listOf("us", "usa", "united states")
            else -> listOf(country.lowercase(Locale.ROOT))
        }
        for (i in 0 until minOf(80, catalogs.length())) {
            val item = catalogs.optJSONObject(i) ?: continue
            val type = item.optString("type")
            if (type != "tv" && type != "channel") continue
            val id = item.optString("id")
            val name = item.optString("name", id)
            if (id.isBlank()) continue
            val countryMatch = aliases.any {
                alias -> id.equals(alias, true) || name.equals(alias + " TV", true) ||
                    name.equals(alias, true)
            }
            if (!(manifest == RICOS && country == "BR") && !countryMatch) continue
            val metas = fetchJson(endpoint(manifest, "catalog", type, id)).optJSONArray("metas") ?: continue
            for (k in 0 until minOf(metas.length(), 800)) {
                val meta = metas.optJSONObject(k) ?: continue
                val ref = meta.optString("id")
                val title = meta.optString("name")
                if (ref.isBlank() || title.isBlank()) continue
                val logo = meta.optString("logo").ifBlank { meta.optString("poster") }
                    .takeIf { it.startsWith("https://") }.orEmpty()
                val group = meta.optJSONArray("genres")?.optString(0).orEmpty()
                    .ifBlank { if (manifest == RICOS) "Variedades" else "TV Brasileira" }
                out += Channel(
                    id = country + "|stremio|" + Uri.encode(manifest) + "|" + ref,
                    name = title.take(120), group = group.take(50),
                    logo = logo, url = "", sourceLabel = label,
                    addonManifest = manifest, addonRef = ref, addonType = type
                )
            }
        }
        return out
    }

    /** Resolve fresh at each channel selection: live-stream URLs may expire. */
    suspend fun resolve(channel: Channel): String? = withContext(Dispatchers.IO) {
        val manifest = channel.addonManifest ?: return@withContext channel.url.takeIf { it.isNotBlank() }
        val ref = channel.addonRef ?: return@withContext null
        val data = runCatching { fetchJson(endpoint(manifest, "stream", channel.addonType, ref)) }
            .onFailure { Log.w("VistaLiveAddons", "Stream failed: " + it.message) }
            .getOrNull() ?: return@withContext null
        val streams = data.optJSONArray("streams") ?: return@withContext null
        for (i in 0 until minOf(35, streams.length())) {
            val url = streams.optJSONObject(i)?.optString("url").orEmpty()
            val parsed = runCatching { Uri.parse(url) }.getOrNull()
            val host = parsed?.host?.lowercase(Locale.ROOT).orEmpty()
            if (url.length < 4096 && parsed?.scheme == "https" && host.isNotBlank() &&
                host != "localhost" && !host.startsWith("127.") &&
                !host.startsWith("10.") && !host.startsWith("192.168.")) {
                return@withContext url
            }
        }
        null
    }
}
