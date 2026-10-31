package com.ivor.openstream.presentation.settings

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ivor.openstream.harvester.NativeHarvesterStore
import com.ivor.openstream.harvester.VistaSourceCodec
import java.io.IOException

/**
 * Import/export a personal Vista Source manifest using Android's Storage Access
 * Framework. The file never contains account credentials or media bytes.
 */
class VistaSourceActivity : ComponentActivity() {
    private val store by lazy { NativeHarvesterStore(this) }
    private lateinit var status: TextView

    private val exportPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val output = contentResolver.openOutputStream(uri)
                    ?: throw IOException("Não foi possível criar o ficheiro")
                output.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(VistaSourceCodec.export(store.all()))
                }
            }
            showResult(if (result.isSuccess) "Manifesto exportado com sucesso."
                else "Falha ao exportar: " + (result.exceptionOrNull()?.message ?: "erro"))
        }
    }

    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val input = contentResolver.openInputStream(uri)
                    ?: throw IOException("Não foi possível abrir o ficheiro")
                val bytes = input.use { stream ->
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val amount = stream.read(chunk)
                        if (amount < 0) break
                        if (output.size() + amount > VistaSourceCodec.MAX_BYTES)
                            throw IOException("Manifesto demasiado grande")
                        output.write(chunk, 0, amount)
                    }
                    output.toByteArray()
                }
                importRaw(bytes.toString(Charsets.UTF_8))
            }
            showResult(if (result.isSuccess)
                "Fontes importadas: " + result.getOrThrow() + ". Atualiza o catálogo."
                else "Falha ao importar: " + (result.exceptionOrNull()?.message ?: "erro"))
        }
    }

    private fun importRaw(raw: String): Int {
        val entries = VistaSourceCodec.parse(raw)
        return entries.count { store.save(it) }
    }

    private fun showResult(message: String) {
        status.text = message + "\nFontes locais: " + store.all().size
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff11141e.toInt())
            val p = (22 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        fun label(text: String, size: Float): TextView = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 18)
            container.addView(this)
        }
        label("Vista Source", 27f)
        label(
            "Importa ou exporta o manifest.json da tua biblioteca pessoal. " +
                "Os títulos e episódios ficam disponíveis no VistaStream como fontes locais. " +
                "A exportação inclui apenas URLs diretos sem tokens conhecidos; " +
                "não inclui cookies, autenticação ou ficheiros de vídeo.", 15f
        )
        status = label("Fontes locais: " + store.all().size, 15f)
        container.addView(Button(this).apply {
            text = "Exportar manifest.json"
            setOnClickListener { exportPicker.launch("manifest.json") }
        })
        container.addView(Button(this).apply {
            text = "Importar Vista Source"
            setOnClickListener { importPicker.launch(arrayOf("application/json", "text/plain")) }
        })
        container.addView(Button(this).apply {
            text = "Voltar"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(container) })
        // Explicit cross-app import request. Untrusted input is never saved
        // without a visible confirmation from the user.
        intent?.getStringExtra("com.vistastream.tv.extra.SOURCE_JSON")?.let { raw ->
            val count = runCatching {
                if (raw.toByteArray(Charsets.UTF_8).size > 500_000)
                    error("Fonte demasiado grande para partilha direta")
                VistaSourceCodec.parse(raw).size
            }
            if (count.isFailure) {
                showResult("Fonte recebida inválida: " + (count.exceptionOrNull()?.message ?: "erro"))
            } else {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Importar Vista Source")
                    .setMessage("VistaBrowser quer adicionar " + count.getOrThrow() +
                        " fontes à biblioteca local. Confirmas a importação?")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Importar") { _, _ ->
                        val result = runCatching { importRaw(raw) }
                        showResult(if (result.isSuccess)
                            "Fontes importadas: " + result.getOrThrow()
                            else "Não foi possível importar: " +
                                (result.exceptionOrNull()?.message ?: "erro"))
                    }.show()
            }
        }

    }
}
