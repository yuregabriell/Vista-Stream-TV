package com.ivor.openstream.presentation.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Optional local Vista ID connection. Retains Room profiles, history, library,
 * download metadata and cached playback separately. No cloud token yet.
 */
class VistaIdAccountActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("vista_id_link", Context.MODE_PRIVATE) }
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getStringExtra("com.vista.id.profile_id")
        val name = result.data?.getStringExtra("com.vista.id.profile_name")
        val mode = result.data?.getStringExtra("com.vista.id.mode")
        if (result.resultCode == Activity.RESULT_OK && id != null &&
            Regex("V[A-Z2-9]{12}").matches(id) && mode == "local") {
            prefs.edit().putString("id", id).putString("name", name?.take(48) ?: "").apply()
            draw()
        } else if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Resposta do Vista ID inválida", Toast.LENGTH_LONG).show()
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun label(message: String, size: Float): TextView = TextView(this).apply {
        text = message
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(9), 0, dp(9))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draw()
    }
    private fun draw() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 17, 29))
            setPadding(dp(26), dp(32), dp(26), dp(22))
        }
        content.addView(label("Vista ID · opcional", 25f))
        val id = prefs.getString("id", null)
        if (id == null) {
            content.addView(label("Usar sem conta continua disponível. Liga o teu perfil Vista ID local se quiseres.", 16f))
        } else {
            content.addView(label("Perfil ligado: ${prefs.getString("name", "")} · $id", 17f))
        }
        content.addView(label("A biblioteca, favoritos, histórico, progresso e dados de reprodução continuam guardados neste dispositivo. Não existe ainda sincronização cloud.", 13f))
        val sharePrefs = getSharedPreferences("vista_launcher_home_v1", Context.MODE_PRIVATE)
        val homeShare = android.widget.CheckBox(this).apply {
            text = "Mostrar sugestões e Continuar a assistir no VistaLauncher"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = sharePrefs.getBoolean("share_enabled", true)
            setOnCheckedChangeListener { _, checked ->
                sharePrefs.edit().putBoolean("share_enabled", checked).apply()
            }
        }
        content.addView(homeShare)
        content.addView(label("Partilha apenas metadados e progresso da conta local selecionada com o VistaLauncher nesta mesma TV; nunca envia vídeos ou palavras-passe.", 12f))
        val action = Button(this).apply {
            text = if (id == null) "Ligar Vista ID local" else "Alterar Vista ID"
            setOnClickListener {
                try {
                    launcher.launch(Intent("com.vista.id.action.LINK_LOCAL_PROFILE").setPackage("com.vista.id"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@VistaIdAccountActivity, "Instala Vista ID v0.2 ou superior.", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(this@VistaIdAccountActivity, "Não foi possível abrir Vista ID: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        content.addView(action)
        if (id != null) {
            content.addView(Button(this).apply {
                text = "Desligar (não apaga os dados)"
                setOnClickListener {
                    prefs.edit().remove("id").remove("name").apply()
                    draw()
                }
            })
        }
        content.addView(Button(this).apply { text = "Voltar"; setOnClickListener { finish() } })
        val scroll = android.widget.ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
    }
}
