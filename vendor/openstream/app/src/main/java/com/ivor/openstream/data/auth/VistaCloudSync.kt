package com.ivor.openstream.data.auth

import android.content.Context
import com.ivor.openstream.data.local.AppDatabase
import com.ivor.openstream.data.local.entity.WatchLaterEntity
import com.ivor.openstream.data.local.entity.WatchProgressEntity
import com.ivor.openstream.data.settings.AppSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Explicit, bounded bidirectional merge for a selected LOCAL VS profile.
 * Progress conflict resolution: newest updatedAt; deletions are inferred from
 * the last successful snapshot on this device (never from an empty fresh install).
 * This never touches downloads, stream URLs, cache, playback or addon credentials.
 */
class VistaCloudSync(
    context: Context,
    private val db: AppDatabase,
    private val settings: AppSettingsStore,
    private val auth: VistaCloudAuth
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "vista_cloud_sync_checkpoints_v1", Context.MODE_PRIVATE
    )

    data class Result(val favorites: Int, val progress: Int)

    suspend fun sync(): Result = withContext(Dispatchers.IO) {
        val profileId = settings.activeProfileId.value
        val userId = auth.userId()
        val prefix = userId + ":" + profileId + ":"

        val localFavorites = db.watchLaterDao().getAllWatchLaterItems(profileId).first()
        val localProgress = db.watchProgressDao().observeAll(profileId).first()
        val currentFav = localFavorites.associate { favKey(it.id, it.mediaType) to favoriteJson(it) }
        val currentProgress = localProgress.associate { it.id to progressJson(it) }
        val beforeFav: Set<String>? = prefs.getString(prefix + "fav", null)?.let { value ->
            val array = JSONArray(value)
            (0 until array.length()).map { array.optString(it) }.toSet()
        }
        val beforeProgress: Map<String, Long>? = prefs.getString(prefix + "progress", null)?.let { value ->
            val obj = JSONObject(value)
            obj.keys().asSequence().associateWith { obj.optLong(it, 0L) }
        }

        // Remote row version is compared on every update. Repeat on conflict,
        // never overwrite another device's just-committed state blindly.
        var finalFavorites = linkedMapOf<String, JSONObject>()
        var finalProgress = linkedMapOf<String, JSONObject>()
        var success = false
        repeat(3) {
            if (success) return@repeat
            val remote = auth.cloudData()
            val version = remote.optLong("version", 1)
            val remoteFavs = remote.optJSONArray("favorites") ?: JSONArray()
            val remoteProgress = remote.optJSONObject("progress") ?: JSONObject()

            val favorites = linkedMapOf<String, JSONObject>()
            for (i in 0 until remoteFavs.length()) {
                val entry = remoteFavs.optJSONObject(i) ?: continue
                val id = entry.optInt("id")
                val kind = entry.optString("type")
                if (id > 0 && kind.length in 2..15) favorites[favKey(id, kind)] = entry
            }
            if (beforeFav == null) {
                // First pairing is a non-destructive import on both devices.
                favorites.putAll(currentFav)
            } else {
                // A deleted favorite is removed only if it was previously synced.
                for (old in beforeFav) if (old !in currentFav) favorites.remove(old)
                for ((key, value) in currentFav) if (key !in beforeFav) favorites[key] = value
            }

            val merged = linkedMapOf<String, JSONObject>()
            for (key in remoteProgress.keys()) {
                val value = remoteProgress.optJSONObject(key) ?: continue
                merged[key] = value
            }
            if (beforeProgress == null) {
                for ((key, value) in currentProgress) {
                    val old = merged[key]
                    if (old == null || old.optLong("u") < value.optLong("u")) merged[key] = value
                }
            } else {
                for (old in beforeProgress.keys) if (old !in currentProgress) merged.remove(old)
                for ((key, value) in currentProgress) {
                    val prev = beforeProgress[key]
                    if (prev == null || value.optLong("u") > prev) {
                        val remoteValue = merged[key]
                        if (remoteValue == null || value.optLong("u") >= remoteValue.optLong("u"))
                            merged[key] = value
                    }
                }
            }
            // Fixed caps, checked on encoded bytes, prevent user profiles from
            // growing into large JSONB rows or exceeding Supabase SQL constraints.
            // Never silently truncate user libraries. If the Free-tier compact
            // JSON budget is exhausted, leave BOTH local and cloud untouched.
            if (favorites.size > 160 || merged.size > 175) {
                throw IllegalStateException(
                    "A biblioteca excedeu o limite compacto de sincronização. " +
                    "Os dados locais foram preservados; é necessário aumentar o modelo cloud."
                )
            }
            val favArray = JSONArray().also { out ->
                favorites.values.sortedByDescending { it.optLong("date") }
                    .forEach { out.put(it) }
            }
            val progressObject = JSONObject().also { out ->
                merged.forEach { (id, value) -> out.put(id, value) }
            }
            if (favArray.toString().toByteArray(Charsets.UTF_8).size > 30_000 ||
                progressObject.toString().toByteArray(Charsets.UTF_8).size > 60_000) {
                throw IllegalStateException(
                    "Os dados excederam o limite da nuvem. Nenhum favorito ou progresso foi apagado."
                )
            }
            if (auth.patchCloudData(userId, version, favArray, progressObject)) {
                finalFavorites = linkedMapOf<String, JSONObject>().apply {
                    for (i in 0 until favArray.length()) {
                        val obj = favArray.getJSONObject(i)
                        put(favKey(obj.getInt("id"), obj.getString("type")), obj)
                    }
                }
                finalProgress = linkedMapOf<String, JSONObject>().apply {
                    for (entry in progressObject.keys()) {
                        progressObject.optJSONObject(entry)?.let { put(entry, it) }
                    }
                }
                success = true
            }
        }
        if (!success) throw IllegalStateException("A nuvem mudou durante a sincronização. Tenta novamente.")

        val favDao = db.watchLaterDao()
        for (local in localFavorites) {
            if (favKey(local.id, local.mediaType) !in finalFavorites)
                favDao.deleteWatchLaterItemById(profileId, local.id)
        }
        val currentFavKeys = currentFav.keys
        for ((key, obj) in finalFavorites) {
            if (key in currentFavKeys) continue
            favDao.insertWatchLaterItem(WatchLaterEntity(
                id = obj.optInt("id"),
                title = obj.optString("title").take(150),
                posterPath = obj.optString("poster").takeIf { it.isNotBlank() },
                mediaType = obj.optString("type"),
                voteAverage = obj.optDouble("vote", 0.0),
                dateAdded = obj.optLong("date", System.currentTimeMillis()),
                profileId = profileId
            ))
        }

        val progressDao = db.watchProgressDao()
        for (local in localProgress) {
            if (local.id !in finalProgress) progressDao.delete(profileId, local.id)
        }
        for ((key, obj) in finalProgress) {
            val id = obj.optInt("tmdb")
            val kind = obj.optString("type")
            val season = obj.optInt("s")
            val episode = obj.optInt("e")
            if (id <= 0 || kind.length !in 2..15 ||
                key != WatchProgressEntity.idFor(kind, id, season, episode)) continue
            val remoteTime = obj.optLong("u")
            if (localProgress.any { it.id == key && it.updatedAt >= remoteTime }) continue
            progressDao.upsert(WatchProgressEntity(
                id = key, tmdbId = id, mediaType = kind,
                season = season, episode = episode,
                title = obj.optString("title").take(150),
                episodeTitle = obj.optString("episodeTitle").takeIf { it.isNotBlank() },
                posterPath = obj.optString("poster").takeIf { it.isNotBlank() },
                backdropPath = null, stillPath = null,
                positionMs = obj.optLong("p").coerceAtLeast(0),
                durationMs = obj.optLong("d").coerceAtLeast(0),
                completed = obj.optBoolean("c"), updatedAt = remoteTime,
                profileId = profileId
            ))
        }

        val snapshotFav = JSONArray()
        finalFavorites.keys.forEach { snapshotFav.put(it) }
        val snapshotProgress = JSONObject()
        finalProgress.forEach { (id, value) -> snapshotProgress.put(id, value.optLong("u")) }
        prefs.edit()
            .putString(prefix + "fav", snapshotFav.toString())
            .putString(prefix + "progress", snapshotProgress.toString())
            .apply()
        Result(finalFavorites.size, finalProgress.size)
    }

    private fun favKey(id: Int, type: String) = type + ":" + id
    private fun favoriteJson(value: WatchLaterEntity) = JSONObject()
        .put("id", value.id)
        .put("type", value.mediaType.take(15))
        .put("title", value.title.take(150))
        .put("poster", value.posterPath?.take(200) ?: "")
        .put("date", value.dateAdded)
        .put("vote", value.voteAverage)

    private fun progressJson(value: WatchProgressEntity) = JSONObject()
        .put("tmdb", value.tmdbId)
        .put("type", value.mediaType.take(15))
        .put("s", value.season)
        .put("e", value.episode)
        .put("title", value.title.take(150))
        .put("episodeTitle", value.episodeTitle?.take(120) ?: "")
        .put("poster", value.posterPath?.take(200) ?: "")
        .put("p", value.positionMs)
        .put("d", value.durationMs)
        .put("c", value.completed)
        .put("u", value.updatedAt)
}
