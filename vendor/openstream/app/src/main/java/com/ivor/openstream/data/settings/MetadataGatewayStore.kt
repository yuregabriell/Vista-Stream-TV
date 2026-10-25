package com.ivor.openstream.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private endpoint and per-device gateway token. TMDB/TVDB keys remain on
 * the server. No fixed gateway credential is present in the APK.
 */
@Singleton
class MetadataGatewayStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("vs_metadata_gateway", Context.MODE_PRIVATE)

    data class Gateway(val baseUrl: String, val accessToken: String)

    fun active(): Boolean = current() != null

    fun current(): Gateway? {
        val url = validateUrl(preferences.getString("base_url", "").orEmpty())
            ?: return null
        val token = decrypt(preferences.getString("token_encrypted", "").orEmpty()).orEmpty()
        if (token.isBlank()) return null
        return Gateway(url, token)
    }

    fun configuredUrl(): String = preferences.getString("base_url", "").orEmpty()

    fun save(rawUrl: String, token: String): Boolean {
        val url = validateUrl(rawUrl) ?: return false
        if (token.length !in 20..250 || token.contains('\n') || token.contains('\r')) return false
        val encrypted = encrypt(token) ?: return false
        return preferences.edit().putString("base_url", url)
            .putString("token_encrypted", encrypted).commit()
    }

    fun clear() { preferences.edit().clear().apply() }

    companion object {
        // This is a public service ADDRESS, not an API credential.
        // TMDB and TVDB credentials are never compiled into Android.
        const val PUBLIC_GATEWAY_URL = "https://vista-stream-metadata.onrender.com"
        private const val KEY_ALIAS = "vista_metadata_gateway_aes_v1"

        /** Require HTTPS and a bare host. Never accept LAN/http or URL credentials. */
        fun validateUrl(raw: String): String? {
            val input = raw.trim().trimEnd('/')
            val parsed = input.toHttpUrlOrNull() ?: return null
            if (parsed.scheme != "https" || parsed.host.isBlank() ||
                parsed.username.isNotEmpty() || parsed.password.isNotEmpty() ||
                parsed.encodedQuery != null || parsed.fragment != null ||
                parsed.encodedPath != "/" || input.length > 300) return null
            return parsed.scheme + "://" + parsed.host +
                if (parsed.port != 443) ":" + parsed.port else ""
        }

        private fun key(): SecretKey {
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (keystore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            return generator.generateKey()
        }

        private fun encrypt(input: String): String? = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(cipher.iv + cipher.doFinal(input.toByteArray(Charsets.UTF_8)),
                Base64.NO_WRAP)
        }.getOrNull()

        private fun decrypt(encoded: String): String? = runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
}
