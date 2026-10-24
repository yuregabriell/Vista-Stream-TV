#!/usr/bin/env python3
"""Vista Stream stand-alone APK: accept external Harvester playback and editable imports."""
from pathlib import Path

root=Path("vendor/openstream/app/src/main")
base=root/"java/com/ivor/openstream/harvester"
contract=base/"HarvesterContract.kt"
s=contract.read_text()
assert 'ACTION_IMPORT' not in s
s=s.replace('    const val ACTION_PLAY = "com.vistastream.tv.action.PLAY"',
'''    const val ACTION_PLAY = "com.vistastream.tv.action.PLAY"
    const val ACTION_IMPORT = "com.vistastream.tv.action.IMPORT"
    const val EXTRA_KIND = "com.vistastream.tv.extra.KIND"
    const val EXTRA_SERIES = "com.vistastream.tv.extra.SERIES"
    const val EXTRA_SEASON = "com.vistastream.tv.extra.SEASON"
    const val EXTRA_EPISODE = "com.vistastream.tv.extra.EPISODE"
    const val EXTRA_YEAR = "com.vistastream.tv.extra.YEAR"
    const val EXTRA_POSTER = "com.vistastream.tv.extra.POSTER"
    const val EXTRA_SYNOPSIS = "com.vistastream.tv.extra.SYNOPSIS"''',1)
s=s.replace('setOf(ACTION_PLAY, Intent.ACTION_VIEW, Intent.ACTION_SEND)',
            'setOf(ACTION_PLAY, ACTION_IMPORT, Intent.ACTION_VIEW, Intent.ACTION_SEND)',1)
s=s.replace('            ACTION_PLAY -> intent.getStringExtra(EXTRA_URL) ?: intent.dataString',
            '            ACTION_PLAY, ACTION_IMPORT -> intent.getStringExtra(EXTRA_URL) ?: intent.dataString',1)
s=s.replace('action != ACTION_PLAY && !definiteVideo',
            'action != ACTION_PLAY && action != ACTION_IMPORT && !definiteVideo',1)
s=s.replace('''            subtitleLanguage = intent.getStringExtra(EXTRA_SUBTITLE_LANGUAGE)
                ?.take(12)?.takeIf { it.matches(Regex("[a-zA-Z-]{2,12}")) } ?: "pt-BR"
''',
'''            subtitleLanguage = intent.getStringExtra(EXTRA_SUBTITLE_LANGUAGE)
                ?.take(12)?.takeIf { it.matches(Regex("[a-zA-Z-]{2,12}")) } ?: "pt-BR",
            kind = intent.getStringExtra(EXTRA_KIND)?.lowercase()
                ?.takeIf { it in setOf("video", "movie", "series") } ?: "video",
            seriesTitle = intent.getStringExtra(EXTRA_SERIES)?.trim()?.take(180).orEmpty(),
            season = intent.getIntExtra(EXTRA_SEASON, 0).coerceIn(0,999),
            episode = intent.getIntExtra(EXTRA_EPISODE, 0).coerceIn(0,9999),
            year = intent.getIntExtra(EXTRA_YEAR, 0).coerceIn(0,2100),
            poster = httpExtra(EXTRA_POSTER).orEmpty(),
            synopsis = intent.getStringExtra(EXTRA_SYNOPSIS)?.trim()?.take(2500).orEmpty(),
            inCollection = action == ACTION_IMPORT
''',1)
s=s.replace('''            .putExtra(EXTRA_SUBTITLE_LANGUAGE, media.subtitleLanguage)
}''',
'''            .putExtra(EXTRA_SUBTITLE_LANGUAGE, media.subtitleLanguage)
            .putExtra(EXTRA_KIND, media.kind)
            .putExtra(EXTRA_SERIES, media.seriesTitle)
            .putExtra(EXTRA_SEASON, media.season)
            .putExtra(EXTRA_EPISODE, media.episode)
            .putExtra(EXTRA_YEAR, media.year)
            .putExtra(EXTRA_POSTER, media.poster)
            .putExtra(EXTRA_SYNOPSIS, media.synopsis)

    fun directImport(media: HarvesterMedia, targetPackage: String): Intent =
        directPlay(media, targetPackage).setAction(ACTION_IMPORT)
            .setClassName(targetPackage, "com.ivor.openstream.harvester.HarvesterImportActivity")
}''',1)
s=s.replace('''    val durationMs: Long = 0
)''',
'''    val durationMs: Long = 0,
    val kind: String = "video",
    val seriesTitle: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    val year: Int = 0,
    val poster: String = "",
    val synopsis: String = "",
    val inCollection: Boolean = false
)''',1)
contract.write_text(s)

store=base/"HarvesterLibraryStore.kt"
t=store.read_text()
assert 'inCollection' not in t
t=t.replace('''            durationMs = earlier?.durationMs ?: media.durationMs,
''',
'''            durationMs = earlier?.durationMs ?: media.durationMs,
            inCollection = media.inCollection || earlier?.inCollection == true,
            title = if (media.inCollection) media.title else earlier?.takeIf { it.inCollection }?.title ?: media.title,
            kind = if (media.inCollection) media.kind else earlier?.kind ?: media.kind,
            seriesTitle = if (media.inCollection) media.seriesTitle else earlier?.seriesTitle ?: media.seriesTitle,
            season = if (media.inCollection) media.season else earlier?.season ?: media.season,
            episode = if (media.inCollection) media.episode else earlier?.episode ?: media.episode,
            year = if (media.inCollection) media.year else earlier?.year ?: media.year,
            poster = if (media.inCollection) media.poster else earlier?.poster ?: media.poster,
            synopsis = if (media.inCollection) media.synopsis else earlier?.synopsis ?: media.synopsis,
''',1)
store.write_text(t)

importActivity=r'''package com.ivor.openstream.harvester

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.content.Intent
import android.widget.*
import android.text.InputType
import android.graphics.Color

/**
 * Vista-owned metadata editor. Invoked by the independent Harvester Browser.
 * Only explicit Save adds films / series to the curated local library.
 */
class HarvesterImportActivity : Activity() {
    private lateinit var store: HarvesterLibraryStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = HarvesterLibraryStore(this)
        val original = HarvesterContract.parse(intent)
        if(original == null){
            Toast.makeText(this,"Fonte inválida.",Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val shell=ScrollView(this)
        val layout=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(30,10,30,10)
        }
        shell.addView(layout)
        fun field(label:String, content:String, multi:Boolean=false, number:Boolean=false):EditText {
            val view=EditText(this).apply{
                hint=label
                setText(content)
                if(number)inputType=InputType.TYPE_CLASS_NUMBER
                else if(multi){inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=3}
                else inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            layout.addView(view)
            return view
        }
        val title=field("Título",original.title)
        val kinds=arrayOf("Vídeo","Filme","Série")
        val spinner=Spinner(this)
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,kinds)
        spinner.setSelection(when(original.kind){"movie"->1;"series"->2;else->0})
        layout.addView(TextView(this).apply{ text="Tipo de conteúdo";textSize=15f;setPadding(0,20,0,4) })
        layout.addView(spinner)
        val series=field("Nome da série (quando aplicável)",original.seriesTitle)
        val season=field("Temporada (opcional)",if(original.season>0)original.season.toString() else "",number=true)
        val episode=field("Episódio (opcional)",if(original.episode>0)original.episode.toString() else "",number=true)
        val year=field("Ano (opcional)",if(original.year>0)original.year.toString() else "",number=true)
        val poster=field("URL da capa HTTPS (opcional)",original.poster)
        val synopsis=field("Sinopse / descrição (opcional)",original.synopsis,multi=true)
        fun save(play:Boolean) {
            val name=title.text.toString().trim().take(180)
            if(name.isEmpty()){title.error="Informe um título";return}
            val cover=poster.text.toString().trim().take(2048)
            if(cover.isNotEmpty()&&!cover.startsWith("https://")){
                poster.error="Utilize um endereço HTTPS válido";return
            }
            val type=when(spinner.selectedItemPosition){1->"movie";2->"series";else->"video"}
            val updated=original.copy(
                title=name,kind=type,seriesTitle=series.text.toString().trim().take(180),
                season=season.text.toString().toIntOrNull()?.coerceIn(0,999)?:0,
                episode=episode.text.toString().toIntOrNull()?.coerceIn(0,9999)?:0,
                year=year.text.toString().toIntOrNull()?.coerceIn(0,2100)?:0,
                poster=cover,synopsis=synopsis.text.toString().trim().take(2500),
                inCollection=true)
            store.save(updated)
            Toast.makeText(this,"Adicionado à biblioteca do Vista Stream",Toast.LENGTH_SHORT).show()
            if(play){
                try{ startActivity(HarvesterContract.directPlay(updated,packageName)) }
                catch(_ :RuntimeException){}
            }
            finish()
        }
        AlertDialog.Builder(this)
            .setTitle("Adicionar ao Vista Stream")
            .setView(shell)
            .setPositiveButton("Guardar",null)
            .setNeutralButton("Guardar e reproduzir",null)
            .setNegativeButton("Cancelar"){_,_->finish()}
            .create().also {dialog->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(false) }
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { save(true) }
                }
                dialog.setOnCancelListener{finish()}
                dialog.show()
            }
    }
}
'''
# onShowListener must be registered before show()—dialog shows after listeners are bound.
importActivity=importActivity.replace('''                dialog.show()
            }''','''                dialog.show()
            }''')
(base/"HarvesterImportActivity.kt").write_text(importActivity)

xmlPath=root/"AndroidManifest.xml"
xml=xmlPath.read_text()
needle='''        <!-- Harvester Browser / Android share official Vista player.'''
assert needle in xml
xml=xml.replace(needle,'''        <!-- External Harvester Browser metadata import; no embedded web browser. -->
        <activity
            android:name=".harvester.HarvesterImportActivity"
            android:exported="true"
            android:theme="@style/Theme.OpenStream">
            <intent-filter>
                <action android:name="com.vistastream.tv.action.IMPORT" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

'''+needle,1)
xmlPath.write_text(xml)

# Existing HarvesterShelf shows saved URLs. Add type / episode hints and the
# ability to edit details in Vista again, without returning to the Browser.
shelf=base/"HarvesterShelf.kt"
c=shelf.read_text()
c=c.replace('''import androidx.compose.material.icons.filled.PlayArrow''',
'''import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Edit''',1)
c=c.replace('''        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),''',
'''        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),''',1)
c=c.replace('''            Text(
                runCatching { Uri.parse(video.url).host }.getOrNull()
                    ?: "Vídeo online",''',
'''            Text(
                when(video.kind){
                    "movie"->"Filme" + (if(video.year>0)" · ${video.year}" else "")
                    "series"->"Série" + (if(video.season>0)" · T${video.season}" else "") +
                        (if(video.episode>0)" E${video.episode}" else "")
                    else->"Vídeo"
                } + " · " + (runCatching { Uri.parse(video.url).host }.getOrNull() ?: "Online"),''',1)
# optional cover display: coil is already dependency
c=c.replace('''import androidx.lifecycle.DefaultLifecycleObserver''',
'''import coil3.compose.AsyncImage
import androidx.lifecycle.DefaultLifecycleObserver''',1)
c=c.replace('''                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Reproduzir vídeo do navegador",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )''',
'''                        if(video.poster.startsWith("https://")) {
                            AsyncImage(
                                model = video.poster,
                                contentDescription = "Capa de " + video.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Reproduzir vídeo do navegador",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }''',1)
c=c.replace('''                        IconButton(onClick = {
                            store.delete(video.url)''',
'''                        IconButton(onClick = {
                            context.startActivity(HarvesterContract.directImport(video,context.packageName))
                        }) {
                            Icon(Icons.Default.Edit,contentDescription = "Editar metadados")
                        }
                        IconButton(onClick = {
                            store.delete(video.url)''',1)
shelf.write_text(c)

# Show Harvester shelf on the regular home when the TMDB catalog is enabled.
home=root/"java/com/ivor/openstream/presentation/home/HomeScreen.kt"
h=home.read_text()
if "import com.ivor.openstream.harvester.HarvesterShelf" not in h:
    h=h.replace("import com.ivor.openstream.ui.theme.onScrim",
                "import com.ivor.openstream.ui.theme.onScrim\nimport com.ivor.openstream.harvester.HarvesterShelf",1)
h=h.replace('''                    item(key = "hero") {''',
'''                    item(key = "harvester-imports") { HarvesterShelf() }

                    item(key = "hero") {''',1)
home.write_text(h)

# Metadata lives in Vista; its file tree has no embedded browser.
assert not (root/"java/com/harvester/browser/MainActivity.java").exists()
assert 'HarvesterImportActivity' in xml
print("Vista external playback/import protocol and movie/series metadata editor configured.")
