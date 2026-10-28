package com.ivor.openstream.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Binder
import org.json.JSONObject
import java.util.Locale

/**
 * Read-only same-device VistaLauncher home integration, no server, login or media files.
 * The VS user's active local profile is respected. Only the VistaLauncher package
 * is permitted to request this feed. Users can disable it under VS settings.
 *
 * Content URI: content://com.vistastream.tv.homefeed/items
 */
class VistaLauncherFeedProvider : ContentProvider() {
    companion object {
        private const val AUTHORITY = "com.vistastream.tv.homefeed"
        private val COLUMNS = arrayOf("section", "kind", "title", "meta", "artwork", "url", "tmdbId")
        private val ALLOWED_CALLERS = setOf("io.freemytv")
    }

    override fun onCreate(): Boolean = true

    private fun approved(): Boolean {
        val ctx = context ?: return false
        if (Binder.getCallingUid() == android.os.Process.myUid()) return true
        return ctx.packageManager.getPackagesForUid(Binder.getCallingUid())
            ?.any { it in ALLOWED_CALLERS } == true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context ?: throw IllegalStateException("VistaStream unavailable")
        if (uri.authority != AUTHORITY || uri.path != "/items") throw IllegalArgumentException("Invalid home feed path")
        if (!approved()) throw SecurityException("VistaLauncher authorization required")
        val result = MatrixCursor(COLUMNS)
        if (!ctx.getSharedPreferences("vista_launcher_home_v1", 0)
                .getBoolean("share_enabled", true)) return result

        // Recent unfinished movie first, then series if no movie. Never invent viewing history.
        val dbFile = ctx.getDatabasePath("open_stream_db")
        if (dbFile.exists()) {
            try {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    val profile = ctx.getSharedPreferences("open_stream_prefs", 0)
                        .getLong("app_active_profile_id", 1L)
                    db.rawQuery(
                        "SELECT mediaType, tmdbId, title, backdropPath, posterPath, positionMs, durationMs " +
                        "FROM watch_progress WHERE profileId=? AND completed=0 AND positionMs>0 " +
                        "AND tmdbId>0 AND mediaType IN ('movie','tv') " +
                        "ORDER BY CASE WHEN mediaType='movie' THEN 0 ELSE 1 END, updatedAt DESC LIMIT 1",
                        arrayOf(profile.toString())
                    ).use { c ->
                        if (c.moveToFirst()) {
                            val kind = c.getString(0)
                            val id = c.getInt(1)
                            val progress = c.getLong(5)
                            val duration = c.getLong(6)
                            val pct = if (duration > 0) (100L * progress / duration).coerceIn(1, 99) else -1L
                            val title = c.getString(2) ?: ""
                            if (title.isNotBlank()) {
                                val artwork = c.getString(3) ?: c.getString(4) ?: ""
                                val meta = if (pct > 0) "Continuar a assistir · ${pct}%" else "Continuar a assistir"
                                result.addRow(arrayOf<Any?>("continue", kind, title, meta, art(artwork),
                                    "https://www.themoviedb.org/${kind}/${id}", id))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Damaged / locked database or a migration in progress must never crash the home.
            }
        }

        // The exact same cached public catalogue used by VS, including its own anime feed.
        val raw = ctx.getSharedPreferences("vista_keyless_catalog_v1", 0)
            .getString("home_public_catalog_v2", null) ?: return result
        try {
            val feeds = JSONObject(raw)
            for (type in arrayOf("movie", "series", "anime")) {
                val titles = feeds.optJSONArray(type) ?: continue
                var added = 0
                for (i in 0 until titles.length()) {
                    if (added >= 12) break
                    val item = titles.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val image = art(item.optString("background").ifBlank { item.optString("poster") })
                    if (title.isBlank()) continue
                    val kind = if (type == "series") "tv" else type
                    val tmdbId = item.optInt("tmdbId", 0)
                    val href = if (tmdbId > 0)
                        "https://www.themoviedb.org/${if (type == "movie") "movie" else "tv"}/$tmdbId"
                    else ""
                    val year = item.optInt("year", 0)
                    val meta = "VistaStream · ${when (type) { "anime" -> "Anime"; "series" -> "Série"; else -> "Filme" }}" +
                        (if (year in 1900..2100) " · $year" else "")
                    result.addRow(arrayOf<Any?>(type, kind, title, meta, image, href, tmdbId))
                    added++
                }
            }
        } catch (_: Exception) {
            // The cache is optional; VS may not have finished fetching its first catalogue.
        }
        return result
    }

    private fun art(raw: String?): String {
        val value = raw.orEmpty().trim()
        return when {
            value.startsWith("https://") -> value
            value.startsWith("/") && value.length < 128 && !value.contains("..") ->
                "https://image.tmdb.org/t/p/w1280$value"
            else -> ""
        }
    }
    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.vista.homeitem"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
