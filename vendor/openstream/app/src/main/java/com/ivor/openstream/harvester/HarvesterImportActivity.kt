package com.ivor.openstream.harvester

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** Companion to Harvester Browser ACTION_IMPORT; saves only after explicit confirmation. */
@AndroidEntryPoint
class HarvesterImportActivity : ComponentActivity() {
    @Inject lateinit var catalogClient: KeylessCatalogClient
    private val store by lazy { NativeHarvesterStore(this) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(HarvesterContract.EXTRA_URL).orEmpty()
        val pageUrl = intent.getStringExtra(HarvesterContract.EXTRA_PAGE_URL).orEmpty()
        val confirmationToken = intent.getStringExtra("com.vistastream.tv.extra.CONFIRMATION_TOKEN").orEmpty()
        @Suppress("DEPRECATION")
        val callback = runCatching {
            intent.getParcelableExtra<android.os.ResultReceiver>(
                "com.vistastream.tv.extra.CONFIRMATION_RECEIVER"
            )
        }.getOrNull()
        // Signed media playlists are usable transiently but may expire.
        val stable = NativeHarvesterStore.safeDirect(url)
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
        val hintMime = intent.getStringExtra(HarvesterContract.EXTRA_MIME_TYPE).orEmpty()
        val path = uri?.path.orEmpty().lowercase()
        val playableTemporary = !stable &&
            uri?.scheme in listOf("http", "https") && !uri?.host.isNullOrBlank() &&
            url.length <= 8192 && (
                listOf(".m3u8",".mpd",".mp4",".m4v",".webm",".mkv",".mov")
                    .any { path.endsWith(it) } ||
                hintMime.startsWith("video/") ||
                hintMime in listOf("application/vnd.apple.mpegurl","application/dash+xml")
            )
        val referenceOnly = !stable && !playableTemporary
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff151720.toInt())
            setPadding(dp(20), dp(24), dp(20), dp(30))
        }
        fun field(label: String, initial: String, numeric: Boolean = false): EditText {
            panel.addView(TextView(this).apply {
                text = label; textSize = 13f; setTextColor(0xffbac0d0.toInt())
                setPadding(0,dp(12),0,0)
            })
            return EditText(this).apply {
                setSingleLine(true)
                setText(initial); setTextColor(Color.WHITE)
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
                panel.addView(this, LinearLayout.LayoutParams(-1, dp(49)))
            }
        }
        panel.addView(TextView(this).apply {
            text = "Importar para o VistaStream"; textSize = 21f; setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = when {
                stable -> "Fonte compatível. Confirme os metadados antes de guardar."
                playableTemporary -> "Este vídeo pode expirar. Será guardado em Vídeos do Harvester como fonte temporária; a página original ajuda a recuperá-lo."
                else -> "Isto é uma referência de player, não um ficheiro. Indique a página original para guardar a referência Web."
            }
            setTextColor(0xffbac0d0.toInt()); setPadding(0,dp(8),0,dp(9))
        })
        val title=field("Título",intent.getStringExtra(HarvesterContract.EXTRA_TITLE).orEmpty())
        val origin=field("Página original (obrigatória para referência Web)",pageUrl)
        val types=RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        types.addView(RadioButton(this).apply { id=401; text="Filme"; setTextColor(Color.WHITE) })
        types.addView(RadioButton(this).apply { id=402; text="Série / anime"; setTextColor(Color.WHITE) })
        val seasonHint=intent.getIntExtra("com.vistastream.tv.extra.SEASON",0)
        val episodeHint=intent.getIntExtra("com.vistastream.tv.extra.EPISODE",0)
        types.check(if(seasonHint>0 && episodeHint>0) 402 else 401)
        panel.addView(types)
        val season=field("Temporada",seasonHint.coerceAtLeast(1).toString(),true)
        val episode=field("Episódio",episodeHint.coerceAtLeast(1).toString(),true)
        val year=field("Ano (opcional)",intent.getIntExtra("com.vistastream.tv.extra.YEAR",0).takeIf { it>0 }?.toString().orEmpty(),true)
        val audio=field("Áudio sugerido (dub / sub / multi)",
            intent.getStringExtra("com.vistastream.tv.extra.AUDIO_MODE").orEmpty())
        val language=field("Idioma sugerido (ex.: pt-BR)",
            intent.getStringExtra("com.vistastream.tv.extra.AUDIO_LANGUAGE").orEmpty())
        val quality=field("Qualidade sugerida",intent.getStringExtra("com.vistastream.tv.extra.QUALITY").orEmpty())

        // Restore the *real* catalog-linking flow using the same Cinemeta /
        // AniList client as VistaStream's own Browse/Search screen. A user
        // chooses the exact title, rather than silently guessing an ID.
        panel.addView(TextView(this).apply {
            text="ASSOCIAR AO CATÁLOGO DO VISTASTREAM"
            textSize=14f;setTypeface(null,Typeface.BOLD)
            setTextColor(0xffe7e6d1.toInt())
            setPadding(0,dp(25),0,dp(7))
        })
        panel.addView(TextView(this).apply {
            text="Procuramos pelo nome e metadados do vídeo. Escolha o título correto para que a fonte apareça na página oficial do filme, série ou anime e no episódio selecionado."
            textSize=13f;setTextColor(0xffbac0d0.toInt())
        })
        val matchState=TextView(this).apply {
            text="A consultar o catálogo…"
            textSize=13f;setTextColor(0xffbac0d0.toInt())
            setPadding(0,dp(12),0,dp(8))
        }
        panel.addView(matchState)
        val choices=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        panel.addView(choices)
        var chosenCatalog: KeylessTitle?=null

        fun simpleSearch(text: String): String {
            return text.trim()
                .replace(Regex("(?i)^\\s*(assistir|ver|watch|online)\\s+"),"")
                .replace(Regex("(?i)\\b(s\\s*\\d{1,2}e\\s*\\d{1,3}|\\d{1,2}x\\d{1,3})\\b")," ")
                .replace(Regex("(?i)\\b(dublado|legendado|dub|sub|1080p|720p|4k|hd|full hd|online|epis[oó]dio|ep\\.?)\\b")," ")
                .replace(Regex("\\s+")," ").trim().take(80)
        }
        var searchIndex=0
        fun searchCatalog() {
            val index=++searchIndex
            val query=simpleSearch(title.text.toString())
            chosenCatalog=null
            choices.removeAllViews()
            if(query.length<3){
                matchState.text="Indique pelo menos 3 letras do título para procurar."
                return
            }
            matchState.text="A procurar «$query» no catálogo…"
            val wanted=if(types.checkedRadioButtonId==401)
                listOf("movie") else listOf("series","anime")
            lifecycleScope.launch {
                val results=runCatching {
                    wanted.map { type ->
                        async { runCatching { catalogClient.titles(type,query) }.getOrDefault(emptyList()) }
                    }.awaitAll().flatten().distinctBy { it.sourceId }.take(16)
                }.getOrDefault(emptyList())
                if(index!=searchIndex || isFinishing || isDestroyed)return@launch
                choices.removeAllViews()
                matchState.text=if(results.isEmpty())
                    "Nenhuma correspondência encontrada. Pode ajustar o nome e pesquisar novamente, ou guardar como vídeo separado."
                    else "Escolha o resultado certo (não associamos automaticamente títulos ambíguos):"
                for(candidate in results){
                    val row=TextView(this@HarvesterImportActivity).apply {
                        text="○   ${candidate.title}  ·  ${candidate.year ?: "—"}  ·  ${if(candidate.type=="movie")"Filme" else if(candidate.type=="anime")"Anime" else "Série"}"
                        textSize=14f;setTextColor(Color.WHITE)
                        setPadding(dp(12),dp(16),dp(9),dp(16))
                    }
                    row.setOnClickListener {
                        chosenCatalog=candidate
                        for(i in 0 until choices.childCount){
                            val child=choices.getChildAt(i) as? TextView ?:continue
                            val original=child.tag as? String ?:continue
                            child.text=(if(child===row)"✓   " else "○   ")+original
                            child.setBackgroundColor(
                                if(child===row)0xff343b32.toInt() else 0xff1e2229.toInt())
                        }
                        types.check(if(candidate.type=="movie")401 else 402)
                        if(year.text.isNullOrBlank() && candidate.year!=null)
                            year.setText(candidate.year.toString())
                        matchState.text="Associado a: ${candidate.title} · fonte do VistaBrowser"
                    }
                    row.tag=row.text.toString().removePrefix("○   ")
                    row.setBackgroundColor(0xff1e2229.toInt())
                    choices.addView(row,LinearLayout.LayoutParams(-1,-2).apply{
                        topMargin=dp(5)
                    })
                }
            }
        }
        panel.addView(Button(this).apply {
            text="⌕  Procurar título no catálogo"
            setOnClickListener { searchCatalog() }
        },LinearLayout.LayoutParams(-1,dp(50)).apply { topMargin=dp(12) })

        panel.addView(Button(this).apply {
            text="Associar fonte ao título e guardar"
            setOnClickListener {
                val movie=types.checkedRadioButtonId==401
                val originalPage=origin.text.toString().trim()
                if (referenceOnly && !NativeHarvesterStore.safeWebPage(originalPage)) {
                    origin.error="Insira a página Web original (http ou https)"
                    origin.requestFocus()
                    Toast.makeText(this@HarvesterImportActivity,
                        "Não foi possível guardar uma referência sem a página de origem.",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (originalPage.isNotBlank() && !NativeHarvesterStore.safeWebPage(originalPage)) {
                    origin.error="Endereço de origem inválido"
                    origin.requestFocus()
                    return@setOnClickListener
                }
                val approvedTitle=title.text.toString().trim().take(180)
                if (approvedTitle.isBlank()) {
                    title.error="Indique o nome do vídeo"
                    title.requestFocus()
                    return@setOnClickListener
                }
                var succeeded=false
                val match=chosenCatalog
                if(match==null) {
                    Toast.makeText(this@HarvesterImportActivity,
                        "Escolha um título do catálogo, ou utilize Guardar separadamente.",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if(referenceOnly){
                    Toast.makeText(this@HarvesterImportActivity,
                        "Só uma página Web foi encontrada. É preciso descobrir a fonte de vídeo antes de a associar ao episódio.",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if(stable||playableTemporary){
                    val exact=NativeSavedStream(
                        url=url,
                        title=match.title,
                        pageUrl=originalPage,
                        webOnly=false,
                        temporary=playableTemporary,
                        catalogSourceId=match.sourceId,
                        poster=match.poster,
                        background=match.background,
                        imdbId=match.imdbId,
                        tmdbId=match.tmdbId,
                        anilistId=match.anilistId,
                        mime=hintMime,
                        kind=if(match.type=="movie")"movie" else "tv",
                        season=if(match.type=="movie")1 else (season.text.toString().toIntOrNull()?:0),
                        episode=if(match.type=="movie")1 else (episode.text.toString().toIntOrNull()?:0),
                        year=match.year ?: (year.text.toString().toIntOrNull()?:0),
                        audioMode=audio.text.toString().take(24),
                        language=language.text.toString().take(16),
                        quality=quality.text.toString().take(24)
                    )
                    succeeded=runCatching {store.save(exact)}.getOrDefault(false)
                }
                if(!succeeded) {
                    Toast.makeText(this@HarvesterImportActivity,
                        "Fonte inválida ou incompleta. Reveja o endereço e o episódio.",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val response=Intent()
                    .putExtra(HarvesterContract.EXTRA_URL,url)
                    .putExtra(HarvesterContract.EXTRA_TITLE,match.title)
                    .putExtra("com.vistastream.tv.extra.SAVED_WEB_ONLY",false)
                    .putExtra("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                    .putExtra("com.vistastream.tv.extra.CATALOG_LINKED",true)
                    .putExtra("com.vistastream.tv.extra.CATALOG_SOURCE_ID",match.sourceId)
                    .putExtra("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                setResult(RESULT_OK,response)
                runCatching {
                    callback?.send(Activity.RESULT_OK,Bundle().apply {
                        putString(HarvesterContract.EXTRA_URL,url)
                        putString(HarvesterContract.EXTRA_TITLE,match.title)
                        putBoolean("com.vistastream.tv.extra.SAVED_WEB_ONLY",false)
                        putBoolean("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                        putBoolean("com.vistastream.tv.extra.CATALOG_LINKED",true)
                        putString("com.vistastream.tv.extra.CATALOG_SOURCE_ID",match.sourceId)
                        putString("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                    })
                }
                Toast.makeText(this@HarvesterImportActivity,
                    if(playableTemporary)
                        "Associado a ${match.title}. Fonte temporária: poderá expirar."
                    else "Fonte VistaBrowser associada a ${match.title}.",
                    Toast.LENGTH_LONG).show()
                finish()
                return@setOnClickListener
            }
        },LinearLayout.LayoutParams(-1,dp(54)).apply {topMargin=dp(16)})

        // The old separate library stays available by explicit opt-in, so
        // source-only URLs or uncertain title matches never get mislabeled.
        panel.addView(Button(this).apply {
            text="Guardar apenas em Vídeos do Harvester"
            setOnClickListener {
                val chosenCatalog=null
                val originalPage=origin.text.toString().trim()
                val approvedTitle=title.text.toString().trim().take(180)
                if(approvedTitle.isBlank()){ title.error="Título obrigatório";return@setOnClickListener }
                if(referenceOnly && !NativeHarvesterStore.safeWebPage(originalPage)) {
                    origin.error="Página original obrigatória";return@setOnClickListener
                }
                var saved=false
                if(referenceOnly) {
                    val single=NativeSavedStream(
                        url=originalPage,title=approvedTitle,pageUrl=originalPage,
                        webOnly=true,kind=if(types.checkedRadioButtonId==401)"movie" else "tv",
                        season=season.text.toString().toIntOrNull()?:1,
                        episode=episode.text.toString().toIntOrNull()?:1)
                    saved=runCatching {store.save(single)}.getOrDefault(false)
                } else if(stable || playableTemporary) {
                    saved=runCatching {
                        HarvesterLibraryStore(this@HarvesterImportActivity).save(
                            HarvesterMedia(url=url,title=approvedTitle,mimeType=hintMime,
                                pageUrl=originalPage.takeIf {it.isNotBlank()},
                                referer=originalPage.takeIf {it.isNotBlank()})
                        )
                        true
                    }.getOrDefault(false)
                }
                if(!saved) {
                    Toast.makeText(this@HarvesterImportActivity,"Não foi possível guardar a referência.",Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val response=Intent()
                    .putExtra(HarvesterContract.EXTRA_URL,url)
                    .putExtra(HarvesterContract.EXTRA_TITLE,approvedTitle)
                    .putExtra("com.vistastream.tv.extra.SAVED_WEB_ONLY",referenceOnly)
                    .putExtra("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                setResult(RESULT_OK,response)
                runCatching{
                    callback?.send(Activity.RESULT_OK,Bundle().apply{
                        putString(HarvesterContract.EXTRA_URL,url)
                        putString(HarvesterContract.EXTRA_TITLE,approvedTitle)
                        putBoolean("com.vistastream.tv.extra.SAVED_WEB_ONLY",referenceOnly)
                        putBoolean("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                    })
                }
                Toast.makeText(this@HarvesterImportActivity,
                    "Guardado separadamente; não foi associado a um título do catálogo.",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        },LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(8)})
        panel.addView(Button(this).apply {
            text="Cancelar";setOnClickListener { setResult(RESULT_CANCELED);finish() }
        })
        setContentView(ScrollView(this).apply { addView(panel) })
        searchCatalog()
    }
}
