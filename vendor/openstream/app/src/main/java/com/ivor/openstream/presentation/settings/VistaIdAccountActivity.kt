package com.ivor.openstream.presentation.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.ivor.openstream.data.auth.VistaCloudAuth
import com.ivor.openstream.data.auth.VistaCloudSync
import com.ivor.openstream.data.local.AppDatabase
import com.ivor.openstream.data.settings.AppSettingsStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * VistaStream account screen for Android phone + TV D-pad.
 * No password is sent to the Vista ID app, and no cloud account is required to play.
 */
@AndroidEntryPoint
class VistaIdAccountActivity : ComponentActivity() {
    @Inject lateinit var database: AppDatabase
    @Inject lateinit var settingsStore: AppSettingsStore
    private val cloud by lazy { VistaCloudAuth(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val bg = Color.rgb(9, 14, 28)
    private val panel = Color.rgb(23, 30, 49)
    private val accent = Color.rgb(136, 149, 255)
    private val muted = Color.rgb(172, 181, 202)
    private var pairing: VistaCloudAuth.Pairing? = null
    private var polling = false
    private var working = false
    private var active = true
    private var qrStatus: TextView? = null
    private var qrImage: ImageView? = null
    private var lastUsername: String? = null

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }
    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
    private fun card(root: LinearLayout): LinearLayout = col().apply {
        setPadding(dp(21), dp(20), dp(21), dp(20))
        background = shape(panel, 18)
        root.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun button(parent: LinearLayout, caption: String, primary: Boolean, action: () -> Unit) =
        Button(this).apply {
            text = caption; isAllCaps = false
            textSize = 15f; minHeight = dp(52)
            setTextColor(if (primary) bg else Color.WHITE)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = shape(if (primary) accent else Color.rgb(43, 53, 79), 12)
            setOnClickListener { if (!working) action() }
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(55)))
        }
    private fun field(parent: LinearLayout, hint: String, secret: Boolean): EditText =
        EditText(this).apply {
            setSingleLine(true); setTextColor(Color.WHITE)
            setHintTextColor(muted); setHint(hint); textSize = 15f
            inputType = if (secret)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(14), 0, dp(14), 0)
            background = shape(Color.rgb(40, 49, 72), 11)
            parent.addView(this, LinearLayout.LayoutParams(-1, dp(52)))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        draw()
    }
    private fun draw() {
        pairing = null; polling = false; qrStatus = null; qrImage = null
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(bg) }
        val outside = col().apply {
            setPadding(dp(22), dp(26), dp(22), dp(35))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val content = col().apply {
            layoutParams = LinearLayout.LayoutParams(
                if (resources.configuration.screenWidthDp > 800) dp(680)
                    else LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        outside.addView(content)
        scroll.addView(outside); setContentView(scroll)
        content.addView(text("VISTA ID", 12f, accent, true)); gap(content, 5)
        content.addView(text("A tua conta Vista", 29f, Color.WHITE, true)); gap(content, 8)
        content.addView(text("Sincronização de conta, sem transferir filmes ou cache para a nuvem.", 14f, muted))
        gap(content, 23)
        if (cloud.hasSession()) account(content) else signIn(content)
        gap(content, 18)
        button(content, "Voltar ao VistaStream", false) { finish() }
    }
    private fun signIn(content: LinearLayout) {
        val password = card(content)
        password.addView(text("Entrar com palavra-passe", 20f, Color.WHITE, true))
        gap(password, 6)
        password.addView(text("Usa e-mail ou @utilizador e palavra-passe do Vista ID.", 13f, muted))
        gap(password, 14)
        val identifier = field(password, "E-mail ou nome de utilizador", false)
        gap(password, 10)
        val credential = field(password, "Palavra-passe", true)
        gap(password, 13)
        button(password, "Entrar", true) {
            runAsync({ cloud.signInPassword(identifier.text.toString(), credential.text.toString()); runCatching { cloud.markVistaStreamConnected() }; cloud.profile() }) {
                lastUsername = it.username; draw()
            }
        }
        gap(content, 14)
        val code = card(content)
        code.addView(text("Entrar com código temporário", 19f, Color.WHITE, true))
        gap(code, 6)
        code.addView(text("Abre o Vista ID no telemóvel e vê o código que muda a cada 30 segundos.", 13f, muted))
        gap(code, 14)
        val username = field(code, "Nome de utilizador Vista ID", false)
        gap(code, 10)
        val temporaryCode = field(code, "Código de 8 caracteres", false)
        gap(code, 12)
        button(code, "Entrar com código", true) {
            runAsync({ cloud.signInCode(username.text.toString(), temporaryCode.text.toString()); runCatching { cloud.markVistaStreamConnected() }; cloud.profile() }) {
                lastUsername = it.username; draw()
            }
        }
        gap(content, 14)
        val qr = card(content)
        qr.addView(text("Entrar com QR Code", 19f, Color.WHITE, true))
        gap(qr, 6)
        qr.addView(text("No Vista ID: Ler QR Code → confirmar o código que aparece na TV.", 13f, muted))
        gap(qr, 14)
        qrImage = ImageView(this).apply { contentDescription = "QR Code temporário do Vista ID"; visibility = View.GONE }
        qr.addView(qrImage, LinearLayout.LayoutParams(dp(255), dp(255)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        gap(qr, 10)
        qrStatus = text("O código QR expira em 2 minutos.", 14f, muted)
        qr.addView(qrStatus)
        gap(qr, 11)
        button(qr, "Gerar QR Code para ligar o VS", true) { startPairing() }
        gap(qr, 9)
        button(qr, "Cancelar QR", false) {
            pairing = null; polling = false
            qrImage?.visibility = View.GONE
            qrStatus?.text = "Ligação cancelada neste dispositivo."
        }
        gap(content, 14)
        val offline = card(content)
        offline.addView(text("Sem conta? Podes continuar a usar o VS normalmente.", 14f, muted))
    }
    private fun account(content: LinearLayout) {
        val card = card(content)
        card.addView(text("SESSÃO VISTA CLOUD", 12f, accent, true))
        gap(card, 11)
        card.addView(text(lastUsername?.let { "@" + it } ?: "Conta Vista ligada", 22f, Color.WHITE, true))
        gap(card, 8)
        val info = text("A validar perfil...", 13f, muted)
        card.addView(info)
        runAsync({
            val identity = cloud.profile()
            val data = cloud.cloudData()
            Pair(identity, data)
        }) { (identity, data) ->
            lastUsername = identity.username
            info.text = "@" + identity.username + " · " +
                data.optJSONArray("favorites").let { it?.length() ?: 0 } + " favoritos · " +
                data.optJSONObject("progress").let { it?.length() ?: 0 } + " títulos na nuvem"
        }
        gap(card, 14)
        button(card, "Sincronizar favoritos e progresso agora", true) {
            AlertDialog.Builder(this)
                .setTitle("Sincronizar este perfil?")
                .setMessage("Combinar favoritos e episódios do perfil local selecionado com os do Vista Cloud. Mantém os dados no aparelho e transfere apenas metadados pequenos. Se houver conflito de progresso, prevalece o mais recente.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Sincronizar", { _, _ ->
                    runAsync({
                        runBlocking {
                            VistaCloudSync(applicationContext, database, settingsStore, cloud).sync()
                        }
                    }) { result ->
                        Toast.makeText(this, "Sincronizados " + result.favorites +
                            " favoritos e " + result.progress + " episódios", Toast.LENGTH_LONG).show()
                        draw()
                    }
                }).show()
        }
        gap(card, 10)
        button(card, "Consultar dados sincronizados", false) {
            runAsync({ cloud.cloudData() }) { data ->
                AlertDialog.Builder(this).setTitle("Vista Cloud · VS")
                    .setMessage("Favoritos: " + (data.optJSONArray("favorites")?.length() ?: 0) +
                        "\nProgresso: " + (data.optJSONObject("progress")?.length() ?: 0) +
                        "\nVersão: " + data.optLong("version", 1))
                    .setPositiveButton("OK", null).show()
            }
        }
        gap(card, 10)
        button(card, "Terminar sessão neste dispositivo", false) {
            runAsync({ cloud.logout(); true }) { lastUsername = null; draw() }
        }
    }

    private fun startPairing() {
        pairing = null; polling = false
        qrStatus?.text = "A gerar QR Code seguro..."
        runAsync({ cloud.createPairing() }) { result ->
            pairing = result
            val bitmap = qrBitmap(result.qrUri)
            qrImage?.apply { setImageBitmap(bitmap); visibility = View.VISIBLE }
            qrStatus?.text = "Confirma na TV: " + result.verificationCode +
                "\nO QR é válido durante 2 minutos."
            polling = true
            schedulePoll(result)
        }
    }
    private fun qrBitmap(value: String): Bitmap {
        val bits: BitMatrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
        val bitmap = Bitmap.createBitmap(360, 360, Bitmap.Config.RGB_565)
        for (y in 0 until 360) for (x in 0 until 360)
            bitmap.setPixel(x, y, if (bits[x, y]) Color.BLACK else Color.WHITE)
        return bitmap
    }
    private fun schedulePoll(expected: VistaCloudAuth.Pairing) {
        if (!active || isFinishing || !polling || pairing !== expected) return
        handler.postDelayed({
            if (!active || isFinishing || !polling || pairing !== expected) return@postDelayed
            Thread {
                try {
                    val result = cloud.pollPairing(expected)
                    runOnUiThread {
                        if (!active || isFinishing || !polling || pairing !== expected) return@runOnUiThread
                        if (result.pending) schedulePoll(expected)
                        else {
                            polling = false; pairing = null
                            lastUsername = result.username
                            draw()
                            Toast.makeText(this, "VistaStream ligado ao Vista ID", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (ex: Exception) {
                    runOnUiThread {
                        if (active && pairing === expected) {
                            polling = false
                            qrStatus?.text = "Não foi possível ligar: " + (ex.message ?: "erro")
                        }
                    }
                }
            }.start()
        }, 4_500)
    }
    private fun <T> runAsync(job: () -> T, success: (T) -> Unit) {
        if (working || isFinishing) return
        working = true
        Thread {
            try {
                val result = job()
                runOnUiThread {
                    working = false
                    if (active && !isFinishing) success(result)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    working = false
                    if (active && !isFinishing) {
                        AlertDialog.Builder(this).setTitle("Vista ID")
                            .setMessage(e.message ?: "Falha na ligação. Tenta novamente.")
                            .setPositiveButton("OK", null).show()
                    }
                }
            }
        }.start()
    }
    override fun onDestroy() {
        polling = false; pairing = null; active = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
