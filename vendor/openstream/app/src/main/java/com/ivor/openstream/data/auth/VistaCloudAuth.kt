package com.ivor.openstream.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Vista Cloud Auth / Supabase. Only the PUBLIC publishable key is bundled in VS.
 * This client does not transmit media bytes, playback sources or local caches.
 * Every API request must execute on a background thread.
 */
class VistaCloudAuth(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("vista_cloud_secure_auth_v1", Context.MODE_PRIVATE)
    private val lock = Any()

    companion object {
        const val URL = "https://hleekwlptmisfvamgext.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_7H5NVq8e6_6aNsgU8p7S-A_lBdiLspn"
        private const val ALIAS = "vista-stream-supabase-v1"
    }

    data class Pairing(
        val id: String,
        val pollSecret: String,
        val verificationCode: String,
        val qrUri: String,
        val expiresAtMs: Long
    )
    data class PollResult(val pending: Boolean, val username: String? = null)
    data class Identity(val username: String, val displayName: String)

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (entry != null) return entry.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private fun store(session: JSONObject) = synchronized(lock) {
        require(session.optString("access_token").isNotEmpty() &&
            session.optString("refresh_token").isNotEmpty()) { "Sessão Supabase inválida" }
        if (!session.has("expires_at")) {
            session.put("expires_at", System.currentTimeMillis() / 1000 + session.optLong("expires_in", 3600))
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(session.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        Unit
    }

    private fun load(): JSONObject? = synchronized(lock) {
        val iv = prefs.getString("iv", null) ?: return@synchronized null
        val payload = prefs.getString("payload", null) ?: return@synchronized null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP)), Charsets.UTF_8))
        } catch (_: Exception) {
            prefs.edit().clear().commit()
            null
        }
    }

    fun hasSession(): Boolean = load() != null
    fun logout() {
        // Revoke the current device session when network is available.
        try { request("POST", "/auth/v1/logout", JSONObject(), bearer = token()) }
        catch (_: Exception) { /* Local logout must still succeed offline. */ }
        synchronized(lock) { prefs.edit().clear().commit() }
    }

    private fun request(method: String, path: String, body: JSONObject? = null, bearer: String? = null): String {
        val connection = URL(URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.setRequestProperty("apikey", PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer " + bearer)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val data = (if (status >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader()?.use { it.readText().take(32_000) }.orEmpty()
            if (status !in 200..299) {
                val info = runCatching { JSONObject(data) }.getOrNull()
                val message = info?.optString("error_description")?.takeIf { it.isNotBlank() }
                    ?: info?.optString("msg")?.takeIf { it.isNotBlank() }
                    ?: info?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: "Erro HTTP " + status
                throw IllegalStateException(if (status == 429) "Demasiadas tentativas. Tenta novamente." else message)
            }
            return data
        } finally { connection.disconnect() }
    }

    private fun post(path: String, body: JSONObject, bearer: String? = null): JSONObject =
        JSONObject(request("POST", path, body, bearer))

    private fun action(name: String, body: JSONObject = JSONObject(), bearer: String? = null): JSONObject =
        post("/functions/v1/vista-auth", body.put("action", name), bearer)

    fun signInPassword(identifier: String, password: String) {
        val clean = identifier.trim().lowercase(Locale.ROOT)
        require(clean.isNotBlank() && password.isNotEmpty()) { "Indica o utilizador e a palavra-passe." }
        val response = if ("@" in clean) {
            post("/auth/v1/token?grant_type=password",
                JSONObject().put("email", clean).put("password", password))
        } else {
            action("username_password",
                JSONObject().put("username", clean).put("password", password))
        }
        store(response)
    }

    /**
     * A rotating code by itself is short lived and one-use. Only Supabase
     * produces a signed session: never invent a local identity from username.
     */
    fun signInCode(username: String, code: String) {
        val result = action("code_login", JSONObject()
            .put("username", username.trim().lowercase(Locale.ROOT))
            .put("code", code.trim().uppercase(Locale.ROOT)))
        verifyTokenHash(result.getString("token_hash"))
    }

    private fun verifyTokenHash(hash: String) {
        require(hash.length in 20..1024) { "Token de autenticação inválido" }
        val verified = post("/auth/v1/verify",
            JSONObject().put("token_hash", hash).put("type", "email"))
        store(verified)
    }

    fun token(): String = synchronized(lock) {
        var session = load() ?: throw IllegalStateException("Inicia sessão no Vista ID.")
        if (session.optLong("expires_at") < System.currentTimeMillis() / 1000 + 90) {
            val updated = post("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", session.getString("refresh_token")))
            store(updated)
            session = updated
        }
        session.getString("access_token")
    }

    fun profile(): Identity {
        val result = request("GET",
            "/rest/v1/vid_profiles?select=username,display_name&limit=1", bearer = token())
        val rows = org.json.JSONArray(result)
        require(rows.length() == 1) { "Perfil Vista ID indisponível" }
        val profile = rows.getJSONObject(0)
        return Identity(profile.getString("username"), profile.optString("display_name"))
    }

    fun createPairing(): Pairing {
        val result = action("pair_create", JSONObject().put("app_id", "vs"))
        return Pairing(
            result.getString("request_id"),
            result.getString("poll_secret"),
            result.getString("verification_code"),
            result.getString("qr_uri"),
            System.currentTimeMillis() + result.optLong("expires_in", 120) * 1_000
        )
    }

    fun pollPairing(pairing: Pairing): PollResult {
        if (System.currentTimeMillis() >= pairing.expiresAtMs) {
            throw IllegalStateException("Este QR expirou. Gera outro.")
        }
        val result = action("pair_redeem",
            JSONObject().put("request_id", pairing.id)
                .put("poll_secret", pairing.pollSecret))
        if (result.optString("status") == "pending") return PollResult(true)
        require(result.optString("status") == "approved") { "Ligação não aprovada" }
        verifyTokenHash(result.getString("token_hash"))
        return PollResult(false, profile().username)
    }

    fun cloudData(): JSONObject {
        val result = request("GET",
            "/rest/v1/vs_user_data?select=settings,favorites,progress,version,updated_at&limit=1",
            bearer = token())
        val rows = org.json.JSONArray(result)
        require(rows.length() == 1) { "Dados VistaStream indisponíveis" }
        return rows.getJSONObject(0)
    }
}
