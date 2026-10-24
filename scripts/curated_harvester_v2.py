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

# Editor: search the existing Vista Stream/TMDB catalog with a lightweight
# user-confirmed match. Never auto-attach to a guess or silently save.
editor=base/"HarvesterImportActivity.kt"
e=editor.read_text()
e=one(e,"import android.app.Activity",'''import android.app.Activity
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.ivor.openstream.domain.repository.AnimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import com.ivor.openstream.data.remote.model.AnimeDto''',"search imports")
e=one(e,"class HarvesterImportActivity : Activity() {",
'''@AndroidEntryPoint
class HarvesterImportActivity : ComponentActivity() {
    @Inject lateinit var catalog: AnimeRepository
    private val searchScope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    override fun onDestroy(){
        searchScope.cancel()
        super.onDestroy()
    }''',"Hilt searchable editor")
e=one(e,'''        val kinds=arrayOf("Vídeo","Filme","Série")''',
'''        val kinds=arrayOf("Vídeo","Filme","Série","Anime")''',"anime kind")
e=one(e,'''spinner.setSelection(when(original.kind){"movie"->1;"series"->2;else->0})''',
'''spinner.setSelection(when(original.kind){"movie"->1;"series"->2;"anime"->3;else->0})''',"anime selection")
e=one(e,'''        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)
        fun save(play:Boolean) {''',
'''        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)
        var matchedId=original.matchedTmdbId
        var matchedType=original.matchedTmdbType
        val matchLabel=TextView(this).apply{
            text=if(matchedId>0)"Ligado ao catálogo: $matchedType #$matchedId"
                else "Nenhum título associado (pode guardar sem correspondência)"
            setPadding(0,10,0,6)
        }
        layout.addView(matchLabel)
        val lookup=Button(this).apply{text="Procurar no catálogo (filme, série ou anime)"}
        layout.addView(lookup)
        val clear=Button(this).apply{
            text="Remover associação"
            setOnClickListener {
                matchedId=0
                matchedType=""
                matchLabel.text="Sem ligação ao catálogo"
            }
        }
        layout.addView(clear)
        lookup.setOnClickListener {
            lookup.isEnabled=false
            val query=(if(spinner.selectedItemPosition>=2 &&
                series.text.toString().trim().isNotEmpty())series.text.toString()
                else title.text.toString())
                .replace(Regex("(?i)^assistir\\\\s+"),"")
                .replace(Regex("(?i)\\\\s+(online|grátis|gratis|dublado|legendado).*"),"")
                .substringBefore(" - ").trim().take(100)
            if(query.length<2){
                lookup.isEnabled=true
                Toast.makeText(this,"Introduza o título do filme ou série.",Toast.LENGTH_SHORT).show()
            }else searchScope.launch {
                val candidates=runCatching {
                    withContext(Dispatchers.IO){
                        catalog.searchAnime(query,1,"all").getOrThrow()
                    }
                }.getOrNull().orEmpty().take(12)
                lookup.isEnabled=true
                if(isFinishing||isDestroyed)return@launch
                if(candidates.isEmpty()){
                    Toast.makeText(this@HarvesterImportActivity,
                        "Sem resultados no catálogo. Podes guardar sem correspondência.",
                        Toast.LENGTH_LONG).show()
                }else{
                    val labels=candidates.map { candidate ->
                        candidate.name + " ("+(candidate.date.take(4).ifBlank{"—"})+
                            ") · "+(if(candidate.isMovie)"Filme"
                                  else if(candidate.genreIds.orEmpty().contains(16)&&
                                    candidate.originalLanguage=="ja")"Anime"
                                  else "Série")
                    }.toTypedArray()
                    AlertDialog.Builder(this@HarvesterImportActivity)
                        .setTitle("Confirmar o título do catálogo")
                        .setItems(labels){_,index->
                            val candidate=candidates[index]
                            matchedId=candidate.id
                            matchedType=if(candidate.isMovie)"movie" else "tv"
                            title.setText(candidate.name)
                            if(!candidate.isMovie)series.setText(candidate.name)
                            val isAnime=!candidate.isMovie &&
                                candidate.genreIds.orEmpty().contains(16) &&
                                candidate.originalLanguage=="ja"
                            spinner.setSelection(if(candidate.isMovie)1 else if(isAnime)3 else 2)
                            candidate.date.take(4).toIntOrNull()?.let{year.setText(it.toString())}
                            candidate.overview?.takeIf{it.isNotBlank()}?.let{synopsis.setText(it)}
                            candidate.posterPath?.takeIf{it.isNotBlank()}?.let{
                                poster.setText("https://image.tmdb.org/t/p/w500"+it)
                            }
                            matchLabel.text="Associado a: "+candidate.name+
                                " (catálogo #"+candidate.id+")"
                        }.setNegativeButton("Cancelar",null).show()
                }
            }
        }
        fun save(play:Boolean) {''',"explicit catalog lookup")
e=one(e,'''val type=when(spinner.selectedItemPosition){1->"movie";2->"series";else->"video"}''',
'''val type=when(spinner.selectedItemPosition){
                1->"movie";2->"series";3->"anime";else->"video"}''',"category anime")
e=one(e,'''                inCollection=true)''',
'''                inCollection=true,
                matchedTmdbId=if(type=="video")0 else
                    matchedId.takeIf{matchedType == (if(type=="movie")"movie" else "tv")}?:0,
                matchedTmdbType=if(type=="video"||matchedId<=0)""
                    else matchedType.takeIf{it == (if(type=="movie")"movie" else "tv")}.orEmpty()
                )''',"persist confirmed catalog match")
e=one(e,'''            store.save(updated)
            Toast.makeText(this,"Adicionado à biblioteca do Vista Stream",Toast.LENGTH_SHORT).show()''',
'''            store.save(updated)
            setResult(Activity.RESULT_OK,Intent()
                .putExtra(HarvesterContract.EXTRA_URL,updated.url)
                .putExtra(HarvesterContract.EXTRA_TITLE,updated.title)
                .putExtra(HarvesterContract.EXTRA_KIND,updated.kind)
                .putExtra(HarvesterContract.EXTRA_MATCH_ID,updated.matchedTmdbId)
                .putExtra(HarvesterContract.EXTRA_MATCH_TYPE,updated.matchedTmdbType))
            Toast.makeText(this,"Fonte guardada apenas após confirmação",Toast.LENGTH_SHORT).show()''',"return explicit result to Harvester")
editor.write_text(e)
print("Vista metadata editor now searches catalog and confirms matching TMDB title and season/episode.")

print("Vista: ONLY explicit saves; matching film, series and anime sources integrated into episodes.")
