package eu.kanade.tachiyomi.ui.manga

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * User-owned Metron token. Never hardcode it into GitHub or send it to a source
 * extension. AES-GCM ciphertext is stored only in this app's private preferences.
 */
internal object InkShelfMetronCredentials {
    private const val PREFS = "inkshelf_metron_credentials"
    private const val KEY_ALIAS = "inkshelf_metron_api_token_v1"
    private const val KEY_CIPHERTEXT = "token_ciphertext"
    private const val KEY_IV = "token_iv"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    fun save(context: Context, token: String): Boolean {
        val clean = token.trim()
        if (clean.isBlank()) {
            clear(context)
            return true
        }
        if (clean.length > 2048 || clean.any { it.isWhitespace() }) return false
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val ciphertext = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            prefs(context).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        }.getOrDefault(false)
    }

    fun get(context: Context): String? {
        val store = prefs(context)
        val ciphertext = store.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = store.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}
