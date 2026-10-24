#!/usr/bin/env python3
"""Explicit Harvester saves only; match TMDB titles and expose saved links as episode sources."""
from pathlib import Path
r=Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
base=r/"harvester"
contract=base/"HarvesterContract.kt"
s=contract.read_text()

def one(src,a,b,label):
    if src.count(a)!=1:raise RuntimeError(f"{label}: expected one, found {src.count(a)}")
    return src.replace(a,b,1)
s=one(s,'    const val ACTION_IMPORT = "com.vistastream.tv.action.IMPORT"',
'''    const val ACTION_IMPORT = "com.vistastream.tv.action.IMPORT"
    const val EXTRA_MATCH_ID = "com.vistastream.tv.extra.MATCH_TMDB_ID"
    const val EXTRA_MATCH_TYPE = "com.vistastream.tv.extra.MATCH_TMDB_TYPE"''',"contract constants")
s=one(s,'''            inCollection = action == ACTION_IMPORT''',
'''            // Merely invoking the metadata editor does not save anything.
            inCollection = false,
            matchedTmdbId = intent.getIntExtra(EXTRA_MATCH_ID, 0).coerceAtLeast(0),
            matchedTmdbType = intent.getStringExtra(EXTRA_MATCH_TYPE)
                ?.takeIf { it == "movie" || it == "tv" }.orEmpty()''',"do not imply collection before confirmation")
s=one(s,'''            .putExtra(EXTRA_SYNOPSIS, media.synopsis)''',
'''            .putExtra(EXTRA_SYNOPSIS, media.synopsis)
            .putExtra(EXTRA_MATCH_ID, media.matchedTmdbId)
            .putExtra(EXTRA_MATCH_TYPE, media.matchedTmdbType)''',"pass match extras")
s=one(s,'''    val inCollection: Boolean = false''',
'''    val inCollection: Boolean = false,
    val matchedTmdbId: Int = 0,
    val matchedTmdbType: String = ""''',"metadata model")
contract.write_text(s)

store=base/"HarvesterLibraryStore.kt"
t=store.read_text()
t=one(t,'''        entries().sortedByDescending { it.savedAt }.take(80)''',
'''        // Hide legacy auto-saved playback records without deleting them.
        entries().filter { it.inCollection }.sortedByDescending { it.savedAt }.take(80)''',
"hide nonconsensual playback data")
t=one(t,'''    fun save(media: HarvesterMedia) = synchronized(this) {
        // content://''',
'''    fun save(media: HarvesterMedia) = synchronized(this) {
        // Playback, previews, page scans and shared intents never add to the library.
        if (!media.inCollection) return@synchronized
        // content://''',"save must require explicit curator consent")
t=one(t,'''            synopsis = if (media.inCollection) media.synopsis else earlier?.synopsis ?: media.synopsis,''',
'''            synopsis = if (media.inCollection) media.synopsis else earlier?.synopsis ?: media.synopsis,
            matchedTmdbId = if(media.inCollection)media.matchedTmdbId else earlier?.matchedTmdbId ?: 0,
            matchedTmdbType = if(media.inCollection)media.matchedTmdbType else earlier?.matchedTmdbType.orEmpty(),''',
"persist matched ids")
# Only data explicitly imported and user-confirmed, attached to the correct title/episode.
needle='''    fun progress(url: String, positionMs: Long, durationMs: Long)'''
assert needle in t
t=t.replace(needle,'''    fun sourcesFor(identity: com.ivor.openstream.domain.model.MediaIdentity):
        List<com.ivor.openstream.domain.model.VideoServer> =
        all().filter { item ->
            item.matchedTmdbId > 0 &&
            item.matchedTmdbId == identity.tmdbId &&
            item.matchedTmdbType == identity.tmdbType &&
            (identity.tmdbType == "movie" ||
                (item.season == identity.season && item.episode == identity.episode))
        }.map { item ->
            val fmt = when {
                item.mimeType != null -> item.mimeType
                item.url.substringBefore('?').endsWith(".m3u8",true) -> "application/x-mpegURL"
                item.url.substringBefore('?').endsWith(".mpd",true) -> "application/dash+xml"
                else -> null
            }
            com.ivor.openstream.domain.model.VideoServer(
                id = "harvester:" + key(item.url),
                providerId = "harvester-import",
                providerName = "Adicionado pelo Harvester",
                name = item.title,
                url = item.url,
                quality = com.ivor.openstream.domain.model.StreamQuality.UNKNOWN,
                mimeType = fmt,
                headers = buildMap {
                    item.referer?.let { put("Referer",it) }
                    item.userAgent?.let { put("User-Agent",it) }
                },
                isDownloadable = false // These URLs can be temporary or session-bound.
            )
        }

'''+needle,1)
store.write_text(t)

player=base/"HarvesterPlaybackActivity.kt"
p=player.read_text()
p=one(p,'        current?.let(library::save)','''        // Playing is NOT a request to save in either application.''',"remove automatic save")
p=one(p,'            library.save(video)\n            current = video',
'''            // Do not auto-save on play or when returning from the player.
            current = video''',"no save on intent")
player.write_text(p)

# Attach saved sources to the existing title's ordinary Sources sheet.
stream=r/"data/streaming/StreamingRepositoryImpl.kt"
v=stream.read_text()
v=one(v,'import android.content.SharedPreferences',
'''import android.content.SharedPreferences
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ivor.openstream.harvester.HarvesterLibraryStore''',"source-store imports")
v=one(v,'''    private val streamCache: ResolvedStreamCache
)''',
'''    private val streamCache: ResolvedStreamCache,
    @ApplicationContext private val applicationContext: Context
)''',"store Context injection")
v=one(v,'''    private val consecutiveFailures = ConcurrentHashMap<String, Int>()''',
'''    private val savedHarvesterSources by lazy { HarvesterLibraryStore(applicationContext) }
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()''',"source store singleton")
anchor='''    ): Flow<ServerResolution> = channelFlow {
        val installedProviders'''
assert anchor in v
v=v.replace(anchor,'''    ): Flow<ServerResolution> = channelFlow {
        val curated = savedHarvesterSources.sourcesFor(identity)
        suspend fun emitWithSaved(progress: ServerResolution) {
            // Local explicit sources accompany every update, including a cached answer.
            this@channelFlow.send(progress.copy(
                servers = (curated + progress.servers).distinctBy { it.id }
            ))
        }
        val installedProviders''',1)
# Replace only bare sends inside this one method, leaving Channel.send untouched.
start=v.index('    override fun resolveServers(')
end=v.index('    /**\n     * One provider', start)
segment=v[start:end]
import re
segment=re.sub(r'(?<![.\w])send\(', 'emitWithSaved(', segment)
# Restore the ProducerScope send in helper, not recursively emitting.
segment=segment.replace('this@channelFlow.emitWithSaved(', 'this@channelFlow.send(')
v=v[:start]+segment+v[end:]
stream.write_text(v)
print("Vista: ONLY explicit saves; old accidental entries hidden; matching video sources integrated into catalog episodes.")
