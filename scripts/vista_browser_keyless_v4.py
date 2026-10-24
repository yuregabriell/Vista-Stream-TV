from pathlib import Path
root=Path("vendor/openstream/app/src/main/java/com/ivor/openstream")
base=root/"harvester"

def one(s,a,b,label):
    count=s.count(a)
    if count!=1:raise RuntimeError(f"{label}: {count} matches")
    return s.replace(a,b,1)

p=base/"HarvesterContract.kt";s=p.read_text()
s=one(s,'    const val EXTRA_MATCH_TYPE = "com.vistastream.tv.extra.MATCH_TMDB_TYPE"',
'''    const val EXTRA_MATCH_TYPE = "com.vistastream.tv.extra.MATCH_TMDB_TYPE"
    const val EXTRA_MATCH_SOURCE_ID = "com.vistastream.tv.extra.MATCH_CATALOG_ID"''',"contract id")
s=one(s,'''            matchedTmdbType = intent.getStringExtra(EXTRA_MATCH_TYPE)
                ?.takeIf { it == "movie" || it == "tv" }.orEmpty()''',
'''            matchedTmdbType = intent.getStringExtra(EXTRA_MATCH_TYPE)
                ?.takeIf { it == "movie" || it == "tv" }.orEmpty(),
            matchedSourceId = intent.getStringExtra(EXTRA_MATCH_SOURCE_ID)?.take(150).orEmpty()''',"parse catalog id")
s=one(s,'''            .putExtra(EXTRA_MATCH_TYPE, media.matchedTmdbType)''',
'''            .putExtra(EXTRA_MATCH_TYPE, media.matchedTmdbType)
            .putExtra(EXTRA_MATCH_SOURCE_ID, media.matchedSourceId)''',"send id")
s=one(s,'''    val matchedTmdbType: String = ""''',
'''    val matchedTmdbType: String = "",
    val matchedSourceId: String = ""''',"data field")
p.write_text(s)

p=base/"HarvesterLibraryStore.kt";s=p.read_text()
s=one(s,'''            matchedTmdbType = if(media.inCollection)media.matchedTmdbType else earlier?.matchedTmdbType.orEmpty(),''',
'''            matchedTmdbType = if(media.inCollection)media.matchedTmdbType else earlier?.matchedTmdbType.orEmpty(),
            matchedSourceId = if(media.inCollection)media.matchedSourceId else earlier?.matchedSourceId.orEmpty(),''',"persist keyless id")
a=s.index("        all().filter { item ->",s.index("    fun sourcesFor("))
b=s.index("        }.map { item ->",a)
s=s[:a]+'''        all().filter { item ->
            val sameTmdb=item.matchedTmdbId>0 &&
                item.matchedTmdbId==identity.tmdbId &&
                item.matchedTmdbType==identity.tmdbType
            val sameImdb=identity.imdbId?.isNotBlank()==true &&
                item.matchedSourceId=="cinemeta:"+identity.imdbId &&
                item.matchedTmdbType==identity.tmdbType
            val sameAniList=identity.anilistId!=null &&
                item.matchedSourceId=="anilist:"+identity.anilistId &&
                item.matchedTmdbType=="tv"
            val episodeMatches=identity.tmdbType=="movie" ||
                (item.season==identity.season && item.episode==identity.episode)
            (sameTmdb || sameImdb || sameAniList) && episodeMatches
'''+s[b:]
s=one(s,'providerName = "Adicionado pelo Harvester"','providerName = "Browser"',"Browser provider name")
s=one(s,'name = item.title,','name = "Browser · " + item.title,',"Browser source display")
p.write_text(s)

p=base/"HarvesterImportActivity.kt";s=p.read_text()
s=one(s,'import com.ivor.openstream.domain.repository.AnimeRepository',
'''import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle''',"client type")
s=one(s,'@Inject lateinit var catalog: AnimeRepository',
        '@Inject lateinit var catalog: KeylessCatalogClient',"inject keyless")
s=one(s,'        var matchedType=original.matchedTmdbType',
'''        var matchedType=original.matchedTmdbType
        var matchedCatalogId=original.matchedSourceId
        var matchedTmdbId=original.matchedTmdbId''',"stored candidate")
a=s.index("        lookup.setOnClickListener {")
b=s.index("        fun save(play:Boolean) {",a)
s=s[:a]+r'''        lookup.setOnClickListener {
            val text=queryField.text.toString().trim()
            if(text.length<2){
                queryField.error="Escreve um título para procurar."
            }else if(lookup.isEnabled){
                lookup.isEnabled=false
                searchMessage.text="A identificar filme, série ou anime…"
                searchScope.launch {
                    val guesses=linkedMapOf<String,KeylessTitle>()
                    val preferred=when(spinner.selectedItemPosition){
                        1->"movie";2->"series";3->"anime";else->"all"
                    }
                    val categories=when(preferred){
                        "movie"->listOf("movie","series")
                        "anime"->listOf("anime","series")
                        "series"->listOf("series","anime")
                        else->listOf("movie","series","anime")
                    }
                    var lastError:String?=null
                    for(phrase in HarvesterTitleMatching.variants(text).take(3)){
                        // Use the SAME Cinemeta/AniList client as the Vista catalog.
                        // No TMDB key, no extra scraper, max 9 queries.
                        val results=withContext(Dispatchers.IO){
                            categories.map{category->
                                runCatching{catalog.titles(category,query=phrase)}
                            }
                        }
                        results.forEach{result->
                            result.onSuccess{items->
                                items.forEach{item->
                                    guesses.putIfAbsent(item.sourceId,item)
                                }
                            }.onFailure{error->lastError=error.message?.take(140)}
                        }
                        if(guesses.size>=18)break
                    }
                    if(isFinishing||isDestroyed)return@launch
                    lookup.isEnabled=true
                    val guessYear=year.text.toString().toIntOrNull()
                        ?: HarvesterTitleMatching.yearIn(title.text.toString())
                    val candidates=guesses.values.sortedWith(
                        compareByDescending<KeylessTitle>{
                            HarvesterTitleMatching.score(text,it.title,it.year,
                                guessYear,it.type,preferred)
                        }.thenByDescending{it.year?:0}
                    ).take(16)
                    if(candidates.isEmpty()){
                        searchMessage.text=if(lastError!=null)
                            "Pesquisa indisponível: $lastError. Podes editar manualmente."
                            else "Não encontrei correspondência. Tenta um nome mais curto."
                    }else{
                        searchMessage.text=candidates.size.toString()+
                            " opções encontradas. Seleciona a correta."
                        val labels=candidates.map{item->
                            item.title+" ("+(item.year?.toString()?:"—")+") · "+
                            when(item.type){"movie"->"Filme";"anime"->"Anime";else->"Série"}
                        }.toTypedArray()
                        AlertDialog.Builder(this@HarvesterImportActivity)
                            .setTitle("Associar ao catálogo Vista Stream")
                            .setItems(labels){_,index->
                                val item=candidates[index]
                                matchedCatalogId=item.sourceId
                                matchedId=item.tmdbId?:0
                                matchedTmdbId=item.tmdbId?:0
                                matchedType=if(item.type=="movie")"movie" else "tv"
                                title.setText(item.title)
                                if(item.type!="movie")series.setText(item.title)
                                spinner.setSelection(when(item.type){
                                    "movie"->1;"anime"->3;else->2
                                })
                                item.year?.let{year.setText(it.toString())}
                                item.description.takeIf{it.isNotBlank()}?.let{
                                    synopsis.setText(it.take(2500))
                                }
                                item.poster?.takeIf{it.startsWith("https://")}?.let{
                                    poster.setText(it)
                                }
                                matchLabel.text="✓ Associado a "+item.title+
                                    " (catálogo "+item.sourceId+")"
                            }
                            .setNegativeButton("Editar manualmente",null)
                            .show()
                    }
                }
            }
        }
'''+s[b:]
s=one(s,'''                matchedId=0
                matchedType=""''',
'''                matchedId=0
                matchedTmdbId=0
                matchedCatalogId=""
                matchedType=""''',"clear current match")
s=one(s,'''            val matchValid=matchedId>0 &&
                (type=="anime" || (type=="movie" && matchedType=="movie") ||
                 (type=="series" && matchedType=="tv"))''',
'''            val matchValid=matchedCatalogId.isNotBlank() &&
                (type=="anime" || (type=="movie" && matchedType=="movie") ||
                 (type=="series" && matchedType=="tv"))''',"allow no-key association")
s=one(s,'''                matchedTmdbId=if(matchValid)matchedId else 0,
                matchedTmdbType=if(matchValid)matchedType else ""''',
'''                matchedTmdbId=if(matchValid)matchedTmdbId else 0,
                matchedTmdbType=if(matchValid)matchedType else "",
                matchedSourceId=if(matchValid)matchedCatalogId else ""''',"save catalog id")
s=one(s,'''                if(matchedId==0 && queryField.text.toString().length>=3)lookup.performClick()''',
'''                if(matchedCatalogId.isBlank() && queryField.text.toString().length>=3)
                    lookup.performClick()''',"automatic search")
s=one(s,'''                .putExtra(HarvesterContract.EXTRA_MATCH_TYPE,updated.matchedTmdbType))''',
'''                .putExtra(HarvesterContract.EXTRA_MATCH_TYPE,updated.matchedTmdbType)
                .putExtra(HarvesterContract.EXTRA_MATCH_SOURCE_ID,updated.matchedSourceId))''',
"pass back catalog match")
p.write_text(s)

# Dedicated browser shelf inside the Saved/Library section (not merely Home).
p=root/"presentation/watch_later/WatchLaterScreen.kt";s=p.read_text()
s=one(s,'import com.ivor.openstream.ui.theme.onScrim',
'''import com.ivor.openstream.ui.theme.onScrim
import com.ivor.openstream.harvester.HarvesterShelf''',"import shelf")
s=one(s,'''            // The user's own lists sit above Watch Later;''',
'''            item(key = "browser-videos", span = fullWidth) {
                HarvesterShelf()
            }

            // The user's own lists sit above Watch Later;''',"library section")
p.write_text(s)
print("Vista v4: 401 removed, automatic keyless search, correct IMDb/AniList source association, Browser shelf.")
