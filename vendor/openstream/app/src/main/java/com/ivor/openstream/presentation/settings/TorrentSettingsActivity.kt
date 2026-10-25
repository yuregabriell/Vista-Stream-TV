package com.ivor.openstream.presentation.settings

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import com.ivor.openstream.data.streaming.providers.TorrServerBridge
import java.util.concurrent.Executors

/**
 * Opt-in TorrServer integration. The engine runs separately (TorrServe app,
 * a PC or NAS on the LAN) and is NOT bundled in the VS APK.
 */
class TorrentSettingsActivity : Activity() {
    private val bridge by lazy { TorrServerBridge(this) }
    private val bg = 0xff10131c.toInt()
    private val muted = 0xffb2b9cc.toInt()
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun text(value: String, size: Float = 15f, color: Int = Color.WHITE): TextView =
        TextView(this).apply { text=value; textSize=size;setTextColor(color) }
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=bg
        window.navigationBarColor=bg
        val current=bridge.config()
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20),dp(24),dp(20),dp(24))
        }
        root.addView(text("Streaming P2P",24f).apply { setTypeface(null,Typeface.BOLD) })
        root.addView(text(
            "Reproduz torrents de add-ons Stremio por um TorrServer local ou na rede. " +
            "Não descarrega o filme completo para o VS. Usa apenas torrents que podes partilhar legalmente.",
            14f,muted).apply {setPadding(0,dp(8),0,dp(18))})
        val enabled=Switch(this).apply {
            text="Ativar reprodução P2P"
            textSize=17f
            setTextColor(Color.WHITE)
            isChecked=current.enabled
        }
        root.addView(enabled)
        root.addView(text("Escolher motor TorrServer",18f).apply {setPadding(0,dp(18),0,dp(7))})
        val auto=RadioButton(this).apply{id=5101;text="Automático — rede primeiro, depois este aparelho";setTextColor(Color.WHITE)}
        val local=RadioButton(this).apply{id=5102;text="Neste aparelho — TorrServe em execução";setTextColor(Color.WHITE)}
        val remote=RadioButton(this).apply{id=5103;text="Servidor na rede — recomendado para TV";setTextColor(Color.WHITE)}
        val modes=RadioGroup(this).apply {
            orientation=RadioGroup.VERTICAL
            addView(auto);addView(local);addView(remote)
            check(when(current.mode){"local"->5102;"remote"->5103;else->5101})
        }
        root.addView(modes)
        root.addView(text("Endereço TorrServer na rede (opcional no Automático)",14f,muted)
            .apply {setPadding(0,dp(18),0,dp(4))})
        val host=EditText(this).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
            hint="http://192.168.1.100:8090"
            setText(current.remote)
            setSelectAllOnFocus(true)
        }
        root.addView(host)
        root.addView(text("Só são aceites endereços locais/privados. Para ligação remota, o PC ou NAS precisa estar ligado à mesma rede. Não exponhas a porta 8090 à internet.",
            12f,muted).apply {setPadding(0,0,0,dp(15))})

        val feedback=text("Estado: ainda não testado",13f,muted)
        root.addView(feedback)
        fun save(): Boolean {
            val mode=when(modes.checkedRadioButtonId){5102->"local";5103->"remote";else->"auto"}
            val accepted=bridge.configure(enabled.isChecked,mode,host.text.toString())
            if(!accepted) {
                feedback.text="Endereço inválido. Usa apenas localhost ou um endereço privado, ex.: http://192.168.1.50:8090."
                feedback.setTextColor(0xffffb6b6.toInt())
            }
            return accepted
        }
        val saveButton=Button(this).apply {
            text="Guardar configuração"
            setOnClickListener {
                if(save()) {
                    feedback.text="Configuração guardada. O VS consultará o servidor apenas quando P2P estiver ativado."
                    feedback.setTextColor(0xffb9e7b8.toInt())
                }
            }
        }
        root.addView(saveButton)
        val test=Button(this).apply {
            text="Testar ligação ao TorrServer"
            setOnClickListener {
                if(!save())return@setOnClickListener
                feedback.text="A procurar TorrServer..."
                isEnabled=false
                executor.execute {
                    val result=bridge.testConfiguredServer()
                    runOnUiThread {
                        if(!isFinishing) { feedback.text=result;isEnabled=true }
                    }
                }
            }
        }
        root.addView(test)
        root.addView(text("Para executar no telemóvel, instala e inicia TorrServe. Na TV, usa de preferência um TorrServer no PC/NAS; a TV só recebe o vídeo via Wi-Fi.",
            13f,muted).apply {setPadding(0,dp(15),0,dp(8))})
        root.addView(Button(this).apply {
            text="Abrir página do TorrServe (opcional)"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://f-droid.org/packages/ru.yourok.torrserve/")))
            }
        })
        root.addView(Button(this).apply {
            text="Voltar";setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
