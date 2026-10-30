package com.ivor.openstream.harvester

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ivor.openstream.data.catalog.KeylessCatalogClient
import com.ivor.openstream.data.catalog.KeylessTitle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicitly confirm a web source's title and episode before attaching it to the
 * existing catalog. The metadata match is a best-effort free catalog lookup, not
 * a claim that scraped page titles are reliable or that a playable file exists.
 */
@AndroidEntryPoint
class HarvesterCatalogMatchActivity : ComponentActivity() {
    @Inject lateinit var catalog: KeylessCatalogClient
    private val store by lazy { NativeHarvesterStore(this) }
    private lateinit var matches: LinearLayout
    private lateinit var feedback: TextView
    private lateinit var search: EditText
    private lateinit var season: EditText
    private lateinit var episode: EditText
    private lateinit var original: NativeSavedStream

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        original = store.all().firstOrNull { it.url == url } ?: run {
            Toast.makeText(this, "Fonte não encontrada; importa-a primeiro no VistaStream.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(32))
            setBackgroundColor(0xff11141e.toInt())
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        layout.addView(label("Adicionar ao catálogo", 25f).apply { setTypeface(null, Typeface.BOLD) })
        layout.addView(label("O VistaStream procura o título correto no catálogo. Confirma o resultado e o episódio antes de guardar.", 14f))
        layout.addView(label("Fonte: " + original.title.take(110), 13f))
        if (original.webOnly) {
            layout.addView(label("Atenção: esta entrada é uma referência de página. Não será reproduzível sem voltar a identificar um vídeo direto.", 13f))
        }
        search = EditText(this).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            hint = "Nome da série, filme ou anime"
            setText(cleanTitle(original.title))
        }
        layout.addView(search)
        val options = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        season = EditText(this).apply {
            setText(original.season.toString())
            hint = "Temporada"
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        episode = EditText(this).apply {
            setText(original.episode.toString())
            hint = "Episódio"
            setTextColor(Color.WHITE)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        options.addView(season, LinearLayout.LayoutParams(0, dp(52), 1f))
        options.addView(episode, LinearLayout.LayoutParams(0, dp(52), 1f))
        layout.addView(options)
        feedback = label("A pesquisar metadados…", 14f)
        layout.addView(feedback)
        layout.addView(Button(this).apply {
            text = "Pesquisar novamente"
            setOnClickListener { findCandidates() }
        })
        matches = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(matches)
        layout.addView(Button(this).apply {
            text = "Cancelar"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(layout) })
        findCandidates()
    }

    private fun findCandidates() {
        val name = search.text.toString().trim()
        if (name.length < 2) {
            feedback.text = "Escreve pelo menos 2 caracteres do título."
            return
        }
        feedback.text = "A procurar títulos no catálogo…"
        matches.removeAllViews()
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                coroutineScope {
                    val kinds = if (original.kind == "movie") listOf("movie", "series", "anime")
                                else listOf("anime", "series", "movie")
                    kinds.map { kind ->
                        async { runCatching { catalog.titles(kind, name) }.getOrDefault(emptyList()) }
                    }.flatMap { it.await() }.distinctBy { it.sourceId }
                }
            }
            if (isFinishing || isDestroyed) return@launch
            val needle = NativeHarvesterStore.fold(name)
            val ranked = found.sortedByDescending { candidate ->
                val a = NativeHarvesterStore.fold(candidate.title)
                val b = candidate.alternateTitle?.let(NativeHarvesterStore::fold).orEmpty()
                when {
                    a == needle || b == needle -> 4
                    a.startsWith(needle) || b.startsWith(needle) -> 3
                    a.contains(needle) || b.contains(needle) -> 2
                    else -> 1
                }
            }.take(12)
            feedback.text = if (ranked.isEmpty())
                "Nenhum título encontrado. Tenta escrever o nome original ou outra grafia."
                else "Escolhe o título certo (a fonte só é associada após confirmação):"
            ranked.forEach { candidate ->
                val detail = listOfNotNull(candidate.title,
                    candidate.year?.toString(), candidate.type).joinToString(" · ")
                matches.addView(Button(this@HarvesterCatalogMatchActivity).apply {
                    text = detail
                    isAllCaps = false
                    setOnClickListener { confirm(candidate) }
                })
            }
        }
    }

    private fun confirm(title: KeylessTitle) {
        val isMovie = title.type == "movie"
        val s = if (isMovie) 1 else season.text.toString().toIntOrNull() ?: 0
        val e = if (isMovie) 1 else episode.text.toString().toIntOrNull() ?: 0
        if (s !in 1..999 || e !in 1..9999) {
            feedback.text = "Indica uma temporada e episódio válidos."
            return
        }
        val matched = original.copy(title = title.title, kind = if (isMovie) "movie" else "tv",
            season = s, episode = e, year = title.year ?: original.year,
            tmdbId = title.tmdbId, anilistId = title.anilistId,
            catalogSourceId = title.sourceId)
        if (store.save(matched)) {
            // A renamed source must not remain duplicated under an old title.
            if (original.url != matched.url) store.remove(original.url)
            Toast.makeText(this, "Fonte associada a " + title.title + ". Abre o episódio no catálogo.", Toast.LENGTH_LONG).show()
            finish()
        } else feedback.text = "Esta referência não é uma fonte direta válida. Volta a capturar o vídeo."
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace(Regex("(?i)^assistir\\s+"), "")
            .replace(Regex("(?i)\\b\\d{1,2}x\\d{1,3}\\b.*$"), "")
            .replace(Regex("(?i)\\b(?:dublado|legendado|online|hd)\\b.*$"), "")
            .replace(Regex("(?i)\\s+[-|]\\s+[^-|]{2,40}$"), "")
            .trim().ifBlank { raw.take(80) }
    }
}
