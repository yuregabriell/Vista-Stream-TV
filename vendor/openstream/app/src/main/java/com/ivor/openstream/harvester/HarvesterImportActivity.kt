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
        val callback = intent.getParcelableExtra<android.os.ResultReceiver>("com.vistastream.tv.extra.CONFIRMATION_RECEIVER")
        val webOnly = !NativeHarvesterStore.safeDirect(url)
        if (webOnly && !NativeHarvesterStore.safeWebPage(pageUrl)) {
            Toast.makeText(this, "Sem página de origem válida para guardar a referência.", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED); finish(); return
        }
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
            text = "Importar para o Video Stream"; textSize = 21f; setTextColor(Color.WHITE)
            setTypeface(null,Typeface.BOLD)
        })
        panel.addView(TextView(this).apply {
            text = if (webOnly) "Esta fonte pode expirar. Guardaremos a página de origem em Downloads + Web; não o vídeo temporário." else
                "Confirma o título, episódio e idioma. Nada é guardado sem esta confirmação."
            setTextColor(0xffbac0d0.toInt()); setPadding(0,dp(8),0,dp(9))
        })
        val title=field("Título",intent.getStringExtra(HarvesterContract.EXTRA_TITLE).orEmpty())
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
                val item=NativeSavedStream(
                    url=if (webOnly) pageUrl else url,title=title.text.toString().trim().take(180),
                    pageUrl=pageUrl,
                    webOnly=webOnly,
                    mime=intent.getStringExtra(HarvesterContract.EXTRA_MIME_TYPE).orEmpty(),
                    kind=if(movie)"movie" else "tv",
                    season=if(movie)1 else (season.text.toString().toIntOrNull()?:0),
                    episode=if(movie)1 else (episode.text.toString().toIntOrNull()?:0),
                    year=year.text.toString().toIntOrNull()?:0,
                    audioMode=audio.text.toString().take(24),
                    language=language.text.toString().take(16),
                    quality=quality.text.toString().take(24)
                )
                if (!store.save(item)) {
                    Toast.makeText(this@HarvesterImportActivity,"Título, episódio ou URL inválidos",Toast.LENGTH_LONG).show()
                } else {
                    val response=Intent()
                        .putExtra(HarvesterContract.EXTRA_URL,url)
                        .putExtra(HarvesterContract.EXTRA_TITLE,item.title)
                        .putExtra("com.vistastream.tv.extra.SAVED_WEB_ONLY",webOnly)
                        .putExtra("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                    setResult(RESULT_OK,response)
                    // Optional explicit receipt for Android stacks which deliver a cancelled
                    // activity result after the external editor finishes. This is sent
                    // only after NativeHarvesterStore.save() returned success.
                    callback?.send(Activity.RESULT_OK,Bundle().apply {
                        putString(HarvesterContract.EXTRA_URL,url)
                        putString(HarvesterContract.EXTRA_TITLE,item.title)
                        putBoolean("com.vistastream.tv.extra.SAVED_WEB_ONLY",webOnly)
                        putString("com.vistastream.tv.extra.CONFIRMATION_TOKEN",confirmationToken)
                    })
                    Toast.makeText(this@HarvesterImportActivity,
                        if (webOnly) "Referência da página guardada no VistaStream. Reabra-a para encontrar a fonte válida." else
                            "Fonte guardada em Vídeos do Harvester no VistaStream. Pode reproduzir e transmitir à TV.",Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        },LinearLayout.LayoutParams(-1,dp(54)))
        panel.addView(Button(this).apply {
            text="Cancelar";setOnClickListener { setResult(RESULT_CANCELED);finish() }
        })
        setContentView(ScrollView(this).apply { addView(panel) })
    }
}
