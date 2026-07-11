package eu.kanade.tachiyomi.data.suwayomi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class SuwayomiTokens(
    val accessToken: String,
    val refreshToken: String,
)

/** Token persistence is deliberately separate from backupable connection preferences. */
internal interface SuwayomiTokenStore {
    fun read(serverKey: String): SuwayomiTokens?
    fun write(serverKey: String, tokens: SuwayomiTokens)
    fun clear(serverKey: String)
}

internal class AndroidSuwayomiTokenStore(context: Context) : SuwayomiTokenStore {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(serverKey: String): SuwayomiTokens? = runCatching {
        val encoded = preferences.getString(preferenceKey(serverKey), null) ?: return null
        decrypt(encoded).split('\n', limit = 2).let { values ->
            values.takeIf { it.size == 2 && it.all(String::isNotBlank) }
                ?.let { SuwayomiTokens(accessToken = it[0], refreshToken = it[1]) }
        }
    }.getOrNull()

    override fun write(serverKey: String, tokens: SuwayomiTokens) {
        preferences.edit().putString(
            preferenceKey(serverKey),
            encrypt("${tokens.accessToken}\n${tokens.refreshToken}"),
        ).apply()
    }

    override fun clear(serverKey: String) {
        preferences.edit().remove(preferenceKey(serverKey)).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH) { "Invalid encrypted token payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, bytes.copyOfRange(0, IV_LENGTH)))
        }
        return cipher.doFinal(bytes.copyOfRange(IV_LENGTH, bytes.size)).toString(StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private fun preferenceKey(serverKey: String): String = MessageDigest.getInstance("SHA-256")
        .digest(serverKey.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val FILE_NAME = "amatsubu_suwayomi_tokens"
        const val KEY_ALIAS = "amatsubu_suwayomi_token_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
