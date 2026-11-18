package eu.kanade.tachiyomi.ui.inkshelf.shell

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A device-local Metron API token. The token is never included in code,
 * GitHub, logs, a URL, or unencrypted application preferences.
 *
 * AndroidKeyStore's AES key is hardware protected where the device supports
 * it. Android backup cannot export the key, so restored credentials must be
 * entered again if the device changes.
 */
internal class InkShelf2MetronTokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "inkshelf2_metron_secret_v1",
        Context.MODE_PRIVATE,
    )

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    fun hasToken(): Boolean = !prefs.getString(PREF_TOKEN, null).isNullOrBlank()

    fun save(token: String) {
        val trimmed = token.trim()
        require(trimmed.isNotEmpty()) { "Enter a Metron token first." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val serialized = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        prefs.edit().putString(PREF_TOKEN, serialized).apply()
    }

    fun read(): String? {
        val stored = prefs.getString(PREF_TOKEN, null) ?: return null
        return runCatching {
            val payload = Base64.decode(stored, Base64.NO_WRAP)
            if (payload.size <= 12) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8)
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(PREF_TOKEN).apply()
    }

    private companion object {
        const val PREF_TOKEN = "token_encrypted"
        const val KEY_ALIAS = "inkshelf2_metron_key_v1"
    }
}

/** The only permitted endpoint is the official HTTPS Metron API. */
internal object InkShelf2MetronApi {
    sealed class ConnectionResult {
        data object Connected : ConnectionResult()
        data object InvalidToken : ConnectionResult()
        data object RateLimited : ConnectionResult()
        data class Error(val message: String) : ConnectionResult()
    }

    suspend fun testConnection(token: String): ConnectionResult = withContext(Dispatchers.IO) {
        val connection = (URL("https://metron.cloud/api/series/?name=batman")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            instanceFollowRedirects = false
        }

        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> ConnectionResult.Connected
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    ConnectionResult.InvalidToken
                429 -> ConnectionResult.RateLimited
                else -> ConnectionResult.Error("Metron returned HTTP ${connection.responseCode}.")
            }
        } catch (_: Exception) {
            // No exception text: network messages can contain addresses,
            // private environment details, or credentials.
            ConnectionResult.Error("Unable to reach Metron. Check your connection.")
        } finally {
            connection.disconnect()
        }
    }
}
