package com.ivor.openstream.presentation.settings

import android.os.Bundle
import android.util.Base64
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ivor.openstream.data.backup.LibraryBackup
import com.ivor.openstream.harvester.NativeHarvesterStore
import com.ivor.openstream.harvester.NativeSavedStream
import com.ivor.openstream.harvester.HarvesterLibraryStore
import com.ivor.openstream.harvester.HarvesterMedia
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.security.SecureRandom
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * One-time, opt-in Wi-Fi sync between two VistaStream installs (phone and Android TV).
 *
 * A new random 72-bit pairing code is displayed on the receiver; it is NEVER sent
 * over the network or persisted. The payload is AES-256-GCM encrypted, using PBKDF2
 * with a random public salt. The service is local-only, rate-limited and stops after
 * one successful exchange or five minutes. It cannot sync when the devices are apart.
 *
 * Sources are metadata only: no video is uploaded or downloaded and expiring links
 * may need to be captured again in the originating browser.
 */
@AndroidEntryPoint
class VistaLanSyncActivity : ComponentActivity() {
    @Inject lateinit var backup: LibraryBackup

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()
    private var server: ServerSocket? = null
    private var receiverJob: Job? = null
    private lateinit var status: TextView
    private lateinit var discovery: VistaLanDiscovery

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discovery = VistaLanDiscovery(this)
        val isTelevision = (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager)
            .currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(30), dp(24), dp(36))
            setBackgroundColor(0xff11141e.toInt())
        }
        fun label(value: String, size: Float = 15f) = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(8))
        }
        layout.addView(label("Sincronizar VistaStream", 26f).apply {
            setTypeface(null, Typeface.BOLD)
        })
        layout.addView(label(
            "Na mesma rede Wi-Fi, junta o Continuar a assistir, listas, títulos guardados " +
                "e fontes locais compatíveis do VistaStream na TV e no telemóvel. " +
                "O histórico online também é incluído. Nada é enviado para a cloud."
        ))
        layout.addView(label(
            "No dispositivo que vai RECEBER, abre esta página e seleciona Receber. " +
                "No outro, escreve o endereço e o código exibidos e toca Sincronizar. " +
                "Os dois dispositivos ficam com os dados combinados."
        , 13f))

        val receive = Button(this).apply { text = "Gerar código para ligar telemóvel" }
        layout.addView(receive)
        if (!isTelevision) receive.visibility = android.view.View.GONE
        status = label("Aguarda uma ação.", 15f)
        layout.addView(status)

        layout.addView(label("Endereço mostrado no outro dispositivo", 14f))
        val address = EditText(this).apply {
            hint = "192.168.1.25:45678"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffadb3c3.toInt())
        }
        layout.addView(address)
        if (isTelevision) address.visibility = android.view.View.GONE
        layout.addView(label("A procurar TVs VistaStream na mesma rede Wi-Fi…", 13f))
        discovery.discover { name, endpoint ->
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    address.setText(endpoint)
                    status.text = "Dispositivo encontrado: $name. Introduz o código apresentado na TV."
                }
            }
        }
        layout.addView(label("Código temporário de emparelhamento", 14f))
        val code = EditText(this).apply {
            hint = "12 caracteres"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(0xffadb3c3.toInt())
        }
        layout.addView(code)
        if (isTelevision) code.visibility = android.view.View.GONE
        val send = Button(this).apply { text = "Ligar e sincronizar com a TV" }
        layout.addView(send)
        if (isTelevision) send.visibility = android.view.View.GONE

        layout.addView(label(
            "Privacidade: o código dura no máximo 5 minutos; o envio é cifrado. " +
                "Não emparelhes numa rede Wi-Fi pública. Fontes temporárias, " +
                "cookies, sessões e ficheiros de vídeo não são transferidos.",
            13f
        ))
        layout.addView(Button(this).apply {
            text = "Voltar"
            setOnClickListener { finish() }
        })
        setContentView(ScrollView(this).apply { addView(layout) })

        receive.setOnClickListener {
            receive.isEnabled = false
            startReceiver {
                receive.isEnabled = true
            }
        }
        send.setOnClickListener {
            val destination = address.text.toString().trim()
            val pairingCode = code.text.toString().trim()
            if (!validPeer(destination) || !Regex("[A-Za-z0-9_-]{12}").matches(pairingCode)) {
                status.text = "Verifica o endereço IP privado, a porta e o código de 12 caracteres."
                return@setOnClickListener
            }
            send.isEnabled = false
            status.text = "A trocar a biblioteca cifrada com o outro dispositivo…"
            lifecycleScope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val packet = snapshot()
                    val remote = exchange(destination, pairingCode, packet)
                    merge(remote)
                }
                withContext(Dispatchers.Main) {
                    send.isEnabled = true
                    status.text = if (result.isSuccess)
                        "Sincronização concluída. Abre novamente a Home para atualizar as listas."
                    else "Não foi possível sincronizar: ${result.exceptionOrNull()?.message ?: "erro de rede"}"
                }
            }
        }
        if (isTelevision) receive.performClick()
    }

    private fun startReceiver(onStop: () -> Unit) {
        receiverJob?.cancel()
        server?.close()
        receiverJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val localServer = ServerSocket(0)
                server = localServer
                withContext(Dispatchers.Main) { discovery.advertise(localServer.localPort) }
                val pairingCode = ByteArray(9).also(random::nextBytes)
                    .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
                val salt = ByteArray(16).also(random::nextBytes)
                val addresses = localAddresses()
                withContext(Dispatchers.Main) {
                    status.text = if (addresses.isEmpty()) {
                        "Não foi encontrado um IP privado. Liga a TV e o telemóvel ao mesmo Wi-Fi."
                    } else {
                        "Endereço: ${addresses.first()}:${localServer.localPort}\n" +
                            "Código: $pairingCode\n" +
                            "Mantém esta página aberta (máximo 5 minutos)."
                    }
                }
                try {
                    serve(localServer, pairingCode, salt)
                } finally {
                    localServer.close()
                    server = null
                    withContext(Dispatchers.Main) { discovery.stopAdvertising() }
                }
            }
            withContext(Dispatchers.Main) {
                onStop()
                status.text = if (result.isSuccess) {
                    "Dados recebidos e combinados. Podes voltar à Home."
                } else {
                    "Receção terminada: ${result.exceptionOrNull()?.message ?: "sem ligação"}"
                }
            }
        }
    }

    private suspend fun snapshot(): SyncPacket {
        val output = ByteArrayOutputStream()
        backup.export(output, includeSettings = false)
        val history = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val values = mutableMapOf<String, String>()
        val sets = mutableMapOf<String, List<String>>()
        history.all.forEach { (key, value) ->
            if (key.length > 512) return@forEach
            when (value) {
                is String -> if (value.length <= 20_000) values[key] = "s:$value"
                is Long -> values[key] = "l:$value"
                is Int -> values[key] = "i:$value"
                is Boolean -> values[key] = "b:$value"
                is Set<*> -> sets[key] = value.filterIsInstance<String>().take(1000)
            }
        }
        val sources = NativeHarvesterStore(this).all().filter { item ->
            item.url.length <= 4096 &&
                (if (item.webOnly) NativeHarvesterStore.safeWebPage(item.pageUrl)
                 else NativeHarvesterStore.safeDirect(item.url))
        }
        val browserHistory = HarvesterLibraryStore(this).all()
            .filter { NativeHarvesterStore.safeDirect(it.url) }
            .map { it.copy(userAgent = null, referer = null, subtitleUrl = null) }
        return SyncPacket(library = output.toString(Charsets.UTF_8.name()),
            onlineHistory = values, onlineHistorySets = sets, sources = sources,
            browserHistory = browserHistory)
    }

    private suspend fun merge(packet: SyncPacket) {
        require(packet.version == 1) { "Versão de sincronização não suportada." }
        require(packet.library.length < MAX_BYTES) { "Biblioteca demasiado grande." }
        backup.restore(ByteArrayInputStream(packet.library.toByteArray(Charsets.UTF_8)))
        mergeOnlineHistory(packet)
        val store = NativeHarvesterStore(this)
        packet.sources.take(250).forEach { source ->
            // NativeHarvesterStore validates URLs again before accepting them.
            store.save(source)
        }
        HarvesterLibraryStore(this).mergeFromPeer(packet.browserHistory.take(80)
            .filter { NativeHarvesterStore.safeDirect(it.url) })
    }

    private fun mergeOnlineHistory(packet: SyncPacket) {
        val prefs = getSharedPreferences(HISTORY_PREFS, MODE_PRIVATE)
        val current = prefs.all
        val edit = prefs.edit()
        packet.onlineHistory.entries.take(5000).forEach { (key, encoded) ->
            if (key.length > 512 || encoded.length > 20_002) return@forEach
            val id = when {
                key.startsWith("title:") -> key.removePrefix("title:")
                key.contains(":s") && key.contains(":e") -> key.substringBeforeLast(":s")
                else -> key.substringBeforeLast(':', "")
            }
            val remoteAt = packet.onlineHistory["$id:lastPlayedAt"]
                ?.removePrefix("l:")?.toLongOrNull() ?: 0L
            val localAt = current["$id:lastPlayedAt"] as? Long ?: 0L
            if (current.containsKey(key) && remoteAt < localAt) return@forEach
            when {
                encoded.startsWith("s:") -> edit.putString(key, encoded.drop(2))
                encoded.startsWith("l:") -> encoded.drop(2).toLongOrNull()
                    ?.let { edit.putLong(key, it) }
                encoded.startsWith("i:") -> encoded.drop(2).toIntOrNull()
                    ?.let { edit.putInt(key, it) }
                encoded.startsWith("b:") -> edit.putBoolean(key, encoded.drop(2) == "true")
            }
        }
        packet.onlineHistorySets.forEach { (key, entries) ->
            if (key != "saved_title_ids") return@forEach
            val existing = (current[key] as? Set<*>)?.filterIsInstance<String>().orEmpty()
            edit.putStringSet(key, (existing + entries.take(1000)).toSet())
        }
        edit.apply()
    }

    private fun serve(receiver: ServerSocket, code: String, salt: ByteArray) {
        receiver.soTimeout = 15_000
        val expires = System.currentTimeMillis() + 300_000L
        var errors = 0
        while (System.currentTimeMillis() < expires && errors < 4) {
            val peer = try {
                receiver.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            peer.soTimeout = 20_000
            try {
                if (!peer.inetAddress.isSiteLocalAddress) {
                    respond(peer, 403, "Ligação não local".toByteArray())
                    errors++
                    continue
                }
                val input = peer.getInputStream()
                val first = readLine(input) ?: throw IllegalArgumentException("Pedido inválido")
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).lowercase()] =
                        line.substring(idx + 1).trim()
                }
                when (first) {
                    "GET /challenge HTTP/1.1" -> {
                        val challenge = JSONObject().put("salt", Base64.encodeToString(
                            salt, Base64.NO_WRAP)).toString()
                        respond(peer, 200, challenge.toByteArray(Charsets.UTF_8))
                    }
                    "POST /sync HTTP/1.1" -> {
                        val length = headers["content-length"]?.toIntOrNull() ?: -1
                        require(length in 28..MAX_BYTES) { "Tamanho inválido" }
                        val encrypted = ByteArray(length)
                        DataInputStream(input).readFully(encrypted)
                        val incoming = json.decodeFromString<SyncPacket>(
                            decrypt(code, salt, encrypted).toString(Charsets.UTF_8)
                        )
                        // Capture BEFORE restoring the peer's data: both sides independently merge.
                        val outgoing = kotlinx.coroutines.runBlocking { snapshot() }
                        kotlinx.coroutines.runBlocking { merge(incoming) }
                        val response = encrypt(code, salt, json.encodeToString(outgoing).toByteArray())
                        respond(peer, 200, response)
                        return
                    }
                    else -> {
                        respond(peer, 404, "Não encontrado".toByteArray())
                        errors++
                    }
                }
            } catch (_: Exception) {
                errors++
                runCatching { respond(peer, 403, "Código ou pacote inválido".toByteArray()) }
            } finally {
                peer.close()
            }
        }
        throw IllegalStateException("Tempo de emparelhamento esgotado ou tentativas excedidas.")
    }

    private fun exchange(address: String, code: String, snapshot: SyncPacket): SyncPacket {
        val root = "http://$address"
        val challengeConnection = URL("$root/challenge").openConnection() as HttpURLConnection
        val salt = try {
            challengeConnection.connectTimeout = 7_000
            challengeConnection.readTimeout = 12_000
            challengeConnection.instanceFollowRedirects = false
            require(challengeConnection.responseCode == 200) { "TV inacessível." }
            val body = readLimited(challengeConnection.inputStream, 10_000)
            Base64.decode(JSONObject(body.toString(Charsets.UTF_8)).getString("salt"), Base64.DEFAULT)
        } finally { challengeConnection.disconnect() }
        require(salt.size == 16) { "Servidor inválido." }

        val payload = encrypt(code, salt, json.encodeToString(snapshot).toByteArray())
        require(payload.size <= MAX_BYTES) { "A biblioteca excede o limite de envio." }
        val connection = URL("$root/sync").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 7_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            require(connection.responseCode == 200) {
                "Código incorreto, ligação perdida ou pedido rejeitado."
            }
            return json.decodeFromString(
                decrypt(code, salt, readLimited(connection.inputStream, MAX_BYTES))
                    .toString(Charsets.UTF_8)
            )
        } finally { connection.disconnect() }
    }

    private fun encrypt(code: String, salt: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derivedKey(code, salt), GCMParameterSpec(128, iv))
        return iv + cipher.doFinal(plaintext)
    }

    private fun decrypt(code: String, salt: ByteArray, encrypted: ByteArray): ByteArray {
        require(encrypted.size >= 28 && encrypted.size <= MAX_BYTES) { "Pacote inválido." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derivedKey(code, salt),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        return cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
    }

    private fun derivedKey(code: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(code.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded, "AES")
        } finally { spec.clearPassword() }
    }

    private fun respond(socket: Socket, code: Int, body: ByteArray) {
        val name = if (code == 200) "OK" else "Rejected"
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $code $name\r\nContent-Type: application/octet-stream\r\n" +
                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.US_ASCII))
            write(body)
            flush()
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (bytes.size() == 0) null else throw IllegalArgumentException("HTTP incompleto")
            if (b == 10) break
            if (b != 13) bytes.write(b)
            require(bytes.size() < 2048) { "Cabeçalho HTTP demasiado grande" }
        }
        return bytes.toString(Charsets.US_ASCII.name())
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n == -1) break
            require(output.size() + n <= limit) { "Resposta demasiado grande" }
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    private fun validPeer(value: String): Boolean {
        val match = Regex("([0-9]{1,3}(?:\\.[0-9]{1,3}){3}):([0-9]{1,5})").matchEntire(value)
            ?: return false
        val port = match.groupValues[2].toIntOrNull() ?: return false
        if (port !in 1024..65535) return false
        return runCatching {
            val address = InetAddress.getByName(match.groupValues[1])
            address is Inet4Address && address.isSiteLocalAddress
        }.getOrDefault(false)
    }

    private fun localAddresses(): List<String> = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces()).flatMap { iface ->
            Collections.list(iface.inetAddresses).filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }.map { it.hostAddress ?: "" }
        }.filter { it.isNotBlank() }.distinct()
    }.getOrDefault(emptyList())

    override fun onDestroy() {
        discovery.close()
        server?.close()
        receiverJob?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val HISTORY_PREFS = "vista_stream_online_progress_v1"
    }
}

@Serializable
private data class SyncPacket(
    val version: Int = 1,
    val library: String,
    val onlineHistory: Map<String, String> = emptyMap(),
    val onlineHistorySets: Map<String, List<String>> = emptyMap(),
    val sources: List<NativeSavedStream> = emptyList(),
    val browserHistory: List<HarvesterMedia> = emptyList()
)
