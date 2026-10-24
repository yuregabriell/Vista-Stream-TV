from pathlib import Path
p=Path("vendor/openstream/app/src/main/java/com/ivor/openstream/harvester/HarvesterImportActivity.kt")
s=p.read_text()
a=s.index("        lookup.setOnClickListener {")
b=s.index("        fun save(play:Boolean) {",a)
assert b>a
replacement=r'''        val queryField=field("Procurar outro nome (editável)",
            HarvesterTitleMatching.clean(
                if(series.text.toString().isNotBlank())series.text.toString()
                else title.text.toString()))
        val searchMessage=TextView(this).apply{
            text="A pesquisa sugerirá títulos semelhantes."
            setPadding(0,8,0,8)
        }
        layout.addView(searchMessage)
        lookup.setOnClickListener {
            val query=queryField.text.toString().trim()
            if(query.length<2){
                queryField.error="Escreve pelo menos 2 letras"
            } else if(lookup.isEnabled){
                lookup.isEnabled=false
                searchMessage.text="Procurando no catálogo…"
                searchScope.launch {
                    val variants=HarvesterTitleMatching.variants(query)
                    val guesses=linkedMapOf<String,com.ivor.openstream.data.remote.model.AnimeDto>()
                    var lastError:String?=null
                    for(phrase in variants){
                        val result=withContext(Dispatchers.IO){
                            catalog.searchAnime(phrase,1,"all")
                        }
                        result.onSuccess { items->
                            items.forEach { item->
                                if(item.id>0 && item.name.isNotBlank())
                                    guesses.putIfAbsent(
                                        (if(item.isMovie)"movie:" else "tv:")+item.id,item)
                            }
                        }.onFailure{error->lastError=error.message?.take(160)}
                        if(guesses.size>=15)break
                    }
                    if(isFinishing||isDestroyed)return@launch
                    lookup.isEnabled=true
                    val preferred=when(spinner.selectedItemPosition){
                        1->"movie";2->"tv";3->"anime";else->"all"
                    }
                    val yearGuess=year.text.toString().toIntOrNull()
                        ?:HarvesterTitleMatching.yearIn(title.text.toString())
                    val suggestions=guesses.values.sortedWith(
                        compareByDescending<com.ivor.openstream.data.remote.model.AnimeDto>{
                            HarvesterTitleMatching.score(query,it.name,
                                it.date.take(4).toIntOrNull(),yearGuess,
                                if(it.isMovie)"movie" else
                                if(it.originalLanguage=="ja" &&
                                   16 in it.genreIds.orEmpty())"anime" else "tv",
                                preferred)
                        }.thenByDescending{it.popularity?:0.0}
                    ).take(18)
                    if(suggestions.isEmpty()){
                        searchMessage.text=if(lastError!=null)
                            "Falha na pesquisa: "+lastError+"; verifica o acesso ao TMDB."
                            else "Sem resultados. Experimenta um termo mais curto, como «homem aranha»."
                    }else{
                        searchMessage.text=suggestions.size.toString()+
                            " sugestões — escolhe o filme/episódio correto."
                        val labels=suggestions.map{item->
                            item.name+" ("+item.date.take(4).ifBlank{"—"}+")"+
                              " · "+(if(item.isMovie)"Filme" else
                                   if(item.originalLanguage=="ja" &&
                                      16 in item.genreIds.orEmpty())"Anime" else "Série")
                        }.toTypedArray()
                        AlertDialog.Builder(this@HarvesterImportActivity)
                            .setTitle("Escolher título do catálogo")
                            .setItems(labels){_,index->
                                val item=suggestions[index]
                                matchedId=item.id
                                matchedType=if(item.isMovie)"movie" else "tv"
                                title.setText(item.name)
                                if(!item.isMovie)series.setText(item.name)
                                val isAnime=16 in item.genreIds.orEmpty() &&
                                    item.originalLanguage=="ja"
                                spinner.setSelection(if(isAnime)3 else if(item.isMovie)1 else 2)
                                item.date.take(4).toIntOrNull()?.let{year.setText(it.toString())}
                                item.overview?.takeIf{it.isNotBlank()}?.let{synopsis.setText(it)}
                                item.posterPath?.takeIf{it.isNotBlank()}?.let{
                                    poster.setText("https://image.tmdb.org/t/p/w500"+it)
                                }
                                matchLabel.text="✓ Associado a "+item.name+
                                    " (TMDB #"+item.id+")"
                            }.setNegativeButton("Cancelar",null).show()
                    }
                }
            }
        }
'''
s=s[:a]+replacement+s[b:]
old='''val type=when(spinner.selectedItemPosition){1->"movie";2->"series";3->"anime";else->"video"}'''
assert old in s
s=s.replace(old,old+'''
            val matchValid=matchedId>0 &&
                (type=="anime" || (type=="movie" && matchedType=="movie") ||
                 (type=="series" && matchedType=="tv"))
            if(matchValid && matchedType=="tv"){
                if((season.text.toString().toIntOrNull()?:0)<1){
                    season.error="Indica a temporada associada";return
                }
                if((episode.text.toString().toIntOrNull()?:0)<1){
                    episode.error="Indica o episódio associado";return
                }
            }''',1)
a=s.index('                matchedTmdbId=if(type=="video")0 else')
b=s.index('\n                )',a)
s=s[:a]+'''                matchedTmdbId=if(matchValid)matchedId else 0,
                matchedTmdbType=if(matchValid)matchedType else ""'''+s[b:]
old='''                dialog.setOnCancelListener{finish()}
                dialog.show()'''
assert old in s
s=s.replace(old,'''                dialog.setOnCancelListener{finish()}
                dialog.show()
                if(matchedId==0 && queryField.text.toString().length>=3)lookup.performClick()''',1)
p.write_text(s)
helper=p.parent/"HarvesterTitleMatching.kt"
helper.write_text(r'''package com.ivor.openstream.harvester

import java.text.Normalizer
import java.util.Locale

/** Fast on-device matching and bounded query expansion; no LLM required. */
internal object HarvesterTitleMatching {
    private fun compact(raw:String):String =
        Normalizer.normalize(raw.lowercase(Locale.ROOT),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"")
            .replace(Regex("[^a-z0-9]+")," ").trim().replace(Regex("\\s+")," ")
            .replace(Regex("\\bhomem aranha\\b"),"spider man")
            .replace(Regex("\\bspiderman\\b"),"spider man")
    fun clean(raw:String):String =
        raw.substringBefore(" | ")
            .replace(Regex("(?i)^\\s*(assistir|ver|watch)\\s+"),"")
            .replace(Regex("(?i)\\s+-\\s+(19\\d{2}|20\\d{2}).*"),"")
            .replace(Regex("(?i)\\s+(online|grátis|gratis|dublado|legendado).*"),"")
            .replace(Regex("\\s+")," ")
            .trim(' ','-','.',':').take(100)
    fun yearIn(value:String):Int? =
        Regex("\\b(?:19|20)\\d{2}\\b").find(value)?.value?.toIntOrNull()
    fun variants(value:String):List<String> {
        val full=clean(value)
        val base=full.substringBefore(':').trim()
        val punct=full.replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
        val guesses=mutableListOf(full,base)
        val tokens=punct.split(" ").filter{it.isNotBlank()}
        if(tokens.size>=2)guesses.add(tokens.take(2).joinToString(" "))
        if("spider man" in compact(full))guesses.add("Spider Man")
        guesses.add(punct)
        // Preserve URL search punctuation differences: the catalog indexes
        // "homem aranha" more reliably than "homem-aranha".
        return guesses.filter{it.length>=2}.distinctBy{it.lowercase(Locale.ROOT)}.take(4)
    }
    fun score(raw:String, candidate:String, year:Int?, expectedYear:Int?,
              type:String, preferredType:String):Int {
        val a=compact(clean(raw)).split(" ").filter{it.isNotBlank()}.toSet()
        val b=compact(candidate).split(" ").filter{it.isNotBlank()}.toSet()
        val common=a.intersect(b).size
        var score=if(a.isEmpty()||b.isEmpty())0 else
            common*55/a.size + common*35/b.size
        if(compact(clean(raw))==compact(candidate))score+=60
        if(year!=null && expectedYear!=null)
            score+=if(year==expectedYear)35 else
                -minOf(25,kotlin.math.abs(year-expectedYear)*4)
        if(type==preferredType)score+=20
        return score
    }
}
''')
assert "HarvesterTitleMatching.variants(query)" in s
print("Title matcher fixed with localized alternatives, editable query, scoring and confirmed matches.")
