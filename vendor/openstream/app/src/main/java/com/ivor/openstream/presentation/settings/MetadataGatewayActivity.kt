package com.ivor.openstream.presentation.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.widget.*
import com.ivor.openstream.data.settings.MetadataGatewayStore
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Securely configure an individually provisioned backend session.
 * No global TMDB or TVDB key should ever be pasted into this screen.
 */
class MetadataGatewayActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val store by lazy { MetadataGatewayStore(this) }
    private fun dp(x: Int) = (resources.displayMetrics.density * x + .5f).toInt()
    private val bg = 0xff11141e.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(23), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        fun label(value: String, size: Float = 15f): TextView =
            TextView(this).apply { text = value; textSize = size; setTextColor(Color.WHITE) }
        content.addView(label("Servidor de metadados", 23f).apply { setTypeface(null, Typeface.BOLD) })
        content.addView(label(
            "Opcional. Para o catálogo partilhado, instala primeiro o backend VS num domínio HTTPS. " +
                "O administrador do servidor fornece um token individual. NÃO coloques aqui a chave TMDB ou TVDB.",
            14f
        ).apply {setPadding(0,dp(8),0,dp(18))})
        content.addView(label("Endereço HTTPS do servidor", 14f))
        val address = EditText(this).apply {
            hint = "https://catalogo.exemplo.com"
            setText(store.configuredUrl())
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffb0b0bc.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        content.addView(address)
        content.addView(label("Token de acesso ao servidor (não é chave TMDB)",14f)
            .apply{setPadding(0,dp(18),0,0)})
        val token = EditText(this).apply {
            hint = if (store.active()) "Token guardado; deixa vazio se não quiseres alterar" else
                "Token individual fornecido pelo servidor"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffb0b0bc.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(token)
        val information = label(
            if (store.active()) "Servidor configurado neste dispositivo."
            else "Nenhum servidor configurado. O catálogo atual continua disponível.", 13f
        ).apply {setPadding(0,dp(15),0,dp(8))}
        content.addView(information)
        val test = Button(this).apply { text = "Testar e guardar servidor" }
        test.setOnClickListener {
            val url = MetadataGatewayStore.validateUrl(address.text.toString())
            val previous = store.current()
            val presentedToken = token.text.toString().trim().ifBlank {
                previous?.takeIf { it.baseUrl == url }?.accessToken.orEmpty()
            }
            if (url == null || presentedToken.length !in 20..250) {
                information.text = "Verifica o domínio HTTPS e o token individual (mínimo 20 caracteres)."
                return@setOnClickListener
            }
            test.isEnabled = false
            information.text = "A contactar o servidor..."
            worker.execute {
                val response = runCatching {
                    val conn = URL(url + "/api/status").openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("X-VS-Access-Token", presentedToken)
                    try {
                        val code = conn.responseCode
                        val body = if (code == 200) conn.inputStream.bufferedReader().use {
                            it.readText().take(5000)
                        } else ""
                        code to body
                    } finally { conn.disconnect() }
                }.getOrDefault(-1 to "")
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    test.isEnabled = true
                    when {
                        response.first == 200 && response.second.contains("\"tmdb\":true") &&
                            store.save(url, presentedToken) -> {
                            token.text.clear()
                            setResult(RESULT_OK, Intent())
                            Toast.makeText(this,"Servidor de metadados ativo.",Toast.LENGTH_LONG).show()
                            finish()
                        }
                        response.first == 401 -> information.text =
                            "Token rejeitado pelo servidor (HTTP 401)."
                        response.first == 200 -> information.text =
                            "Servidor respondeu, mas o TMDB ainda não está configurado nele."
                        else -> information.text =
                            "Não foi possível validar o servidor (HTTP " + response.first + ")."
                    }
                }
            }
        }
        content.addView(test)
        content.addView(Button(this).apply {
            text = "Desativar servidor e usar catálogo anterior"
            setOnClickListener {
                store.clear()
                setResult(RESULT_OK)
                finish()
            }
        })
        content.addView(Button(this).apply { text="Voltar";setOnClickListener{finish()} })
        content.addView(label(
            "As chaves reais ficam exclusivamente nas variáveis de ambiente do backend. " +
                "O token deste dispositivo é cifrado com Android Keystore. " +
                "TVDB só será ativado após confirmar a licença do projeto.", 12f
        ).apply {setPadding(0,dp(16),0,0)})
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
