package com.ivor.openstream.harvester

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface

/** Companion to Harvester Browser ACTION_IMPORT; saves only after explicit confirmation. */
class HarvesterImportActivity : Activity() {
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
            uri?.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
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
        panel.addView(Button(this).apply {
            text="Confirmar e guardar"
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
                if (stable || referenceOnly || (playableTemporary && originalPage.isNotEmpty())) {
                    val original=NativeSavedStream(
                        url=if (stable) url else originalPage,
                        title=approvedTitle, pageUrl=originalPage, webOnly=!stable,
                        mime=hintMime, kind=if(movie)"movie" else "tv",
                        season=if(movie)1 else (season.text.toString().toIntOrNull()?:0),
                        episode=if(movie)1 else (episode.text.toString().toIntOrNull()?:0),
                        year=year.text.toString().toIntOrNull()?:0,
                        audioMode=audio.text.toString().take(24),
                        language=language.text.toString().take(16),
                        quality=quality.text.toString().take(24)
                    )
                    succeeded=runCatching { store.save(original) }.getOrDefault(false)
                }
                if (playableTemporary) {
                    val temp=HarvesterMedia(
                        url=url,title=approvedTitle,
                        mimeType=hintMime.takeIf { it.isNotBlank() },
                        pageUrl=originalPage.takeIf { it.isNotBlank() },
                        referer=originalPage.takeIf { it.isNotBlank() }
                    )
                    succeeded=runCatching {
                        HarvesterLibraryStore(this@HarvesterImportActivity).save(temp)
                        true
                    }.getOrDefault(false) || succeeded
                }
                if (!succeeded) {
                    Toast.makeText(this@HarvesterImportActivity,
                        "Não foi possível guardar. Reveja o endereço, título ou episódio.",
                        Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val response=Intent()
                    .putExtra(HarvesterContract.EXTRA_URL,url)
                    .putExtra(HarvesterContract.EXTRA_TITLE,approvedTitle)
                    .putExtra("com.vistastream.tv.extra.SAVED_WEB_ONLY",referenceOnly)
                    .putExtra("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                    .putExtra("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                setResult(RESULT_OK,response)
                // Never let an unavailable cross-process callback crash the editor.
                runCatching {
                    callback?.send(Activity.RESULT_OK,Bundle().apply {
                        putString(HarvesterContract.EXTRA_URL,url)
                        putString(HarvesterContract.EXTRA_TITLE,approvedTitle)
                        putBoolean("com.vistastream.tv.extra.SAVED_WEB_ONLY",referenceOnly)
                        putBoolean("com.vistastream.tv.extra.SAVED_TEMPORARY",playableTemporary)
                        putString("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                    })
                }
                Toast.makeText(this@HarvesterImportActivity,
                    when {
                        referenceOnly -> "Referência Web guardada no VistaStream."
                        playableTemporary -> "Fonte guardada em Vídeos do Harvester. O link poderá expirar."
                        else -> "Fonte guardada na biblioteca do VistaStream."
                    },Toast.LENGTH_LONG).show()
                finish()
            }
        },LinearLayout.LayoutParams(-1,dp(54)))
        panel.addView(Button(this).apply {
            text="Cancelar";setOnClickListener { setResult(RESULT_CANCELED);finish() }
        })
        setContentView(ScrollView(this).apply { addView(panel) })
    }
}
