package com.ivor.openstream.presentation.settings

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.widget.*
import com.ivor.openstream.data.settings.TmdbKeyStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Personal TMDB metadata key. Never share it with any addon or include it in releases. */
class TmdbKeyActivity : Activity() {
    private val pool = Executors.newSingleThreadExecutor()
    private val store by lazy { TmdbKeyStore(this) }
    private val screenBg = 0xff10131c.toInt()
    private fun dp(x: Int) = (x * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = screenBg
        window.navigationBarColor = screenBg
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(26), dp(22), dp(20))
            setBackgroundColor(screenBg)
        }
        fun label(value: String, size: Float, color: Int = Color.WHITE) =
            TextView(this).apply { text = value; textSize = size; setTextColor(color) }
        content.addView(label("Catálogo TMDB", 25f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(label(
            "A chave API v3 do TMDB permite consultar os metadados e detalhes de filmes, séries e temporadas. " +
                "Não é a chave do AIOStreams nem fornece vídeos por si só.",
            15f, 0xffb8bfd2.toInt()
        ).apply { setPadding(0, dp(14), 0, dp(22)) })
        content.addView(label(if(store.current().isNotBlank())
            "Chave configurada neste dispositivo. Podes substituí-la." else
            "Ainda não existe uma chave TMDB configurada no VS.", 14f, 0xffc6cbe2.toInt()))
        val key = EditText(this).apply {
            hint = "Chave TMDB API v3 (32 caracteres)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xff9faac2.toInt())
        }
        content.addView(key)
        val result = label("A chave fica guardada apenas nas definições locais do VS.", 13f,
            0xffb8bfd2.toInt())
        result.setPadding(0, dp(10), 0, dp(12))
        content.addView(result)
        val save = Button(this).apply { text = "Testar e guardar chave" }
        save.setOnClickListener {
            val value = key.text.toString().trim()
            if(!Regex("[a-fA-F0-9]{32}").matches(value)) {
                result.text = "Usa a chave API v3 de 32 caracteres, não o token de leitura v4."
                return@setOnClickListener
            }
            save.isEnabled = false
            result.text = "A validar diretamente com o TMDB..."
            pool.execute {
                val code = runCatching {
                    val url = URL("https://api.themoviedb.org/3/configuration?api_key=" +
                        URLEncoder.encode(value, "UTF-8"))
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    connection.instanceFollowRedirects = false
                    try { connection.responseCode } finally { connection.disconnect() }
                }.getOrDefault(-1)
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    save.isEnabled = true
                    if (code == 200 && store.save(value)) {
                        key.text.clear()
                        setResult(RESULT_OK)
                        Toast.makeText(this, "TMDB configurado no VS.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        result.text = when (code) {
                            401 -> "O TMDB recusou esta chave (HTTP 401). Verifica se é a chave API v3 correta."
                            -1 -> "Sem resposta do TMDB. Verifica a ligação e tenta novamente."
                            else -> "O TMDB devolveu HTTP $code; a chave não foi guardada."
                        }
                    }
                }
            }
        }
        content.addView(save)
        content.addView(Button(this).apply {
            text = "Voltar"; setOnClickListener { finish() }
        })
        content.addView(label(
            "Para obter a chave: themoviedb.org/settings/api. " +
                "O TVDB utiliza outro sistema de autenticação e não é necessário para corrigir este erro.",
            13f, 0xffb8bfd2.toInt()).apply { setPadding(0, dp(14), 0, 0) })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onDestroy() {
        pool.shutdownNow()
        super.onDestroy()
    }
}
