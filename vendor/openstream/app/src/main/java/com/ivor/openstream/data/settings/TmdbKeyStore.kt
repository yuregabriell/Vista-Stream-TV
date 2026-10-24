package com.ivor.openstream.data.settings

import android.content.Context
import com.ivor.openstream.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Private user-entered TMDB V3 API key for catalog metadata (not playback sources). */
@Singleton
class TmdbKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("vista_stream_catalog", Context.MODE_PRIVATE)

    fun current(): String {
        val saved = prefs.getString("tmdb_v3_api_key", null).orEmpty().trim()
        return saved.ifBlank { BuildConfig.TMDB_API_KEY.takeUnless { it == "DEMO_KEY" }.orEmpty() }
    }

    fun save(key: String): Boolean {
        val value = key.trim()
        if (!Regex("^[a-fA-F0-9]{32}$").matches(value)) return false
        prefs.edit().putString("tmdb_v3_api_key", value).apply()
        return true
    }
}
