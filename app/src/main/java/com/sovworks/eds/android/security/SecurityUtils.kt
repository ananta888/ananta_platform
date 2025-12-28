package com.sovworks.eds.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecurityUtils {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "IdentityKey"
    private const val DB_KEY_ALIAS = "DatabaseKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateSecretKey(alias: String = KEY_ALIAS): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey?.let { return it }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun getDatabasePassphrase(context: android.content.Context): ByteArray {
        val prefs = context.getSharedPreferences("security_prefs", android.content.Context.MODE_PRIVATE)
        val encryptedPassphrase = prefs.getString("db_passphrase", null)
        val iv = prefs.getString("db_passphrase_iv", null)

        if (encryptedPassphrase != null && iv != null) {
            val decrypted = decrypt(encryptedPassphrase, iv, DB_KEY_ALIAS)
            return Base64.decode(decrypted, Base64.NO_WRAP)
        }

        // Generate a new random passphrase
        val random = java.security.SecureRandom()
        val passphrase = ByteArray(32)
        random.nextBytes(passphrase)
        val passphraseBase64 = Base64.encodeToString(passphrase, Base64.NO_WRAP)

        val result = encrypt(passphraseBase64, DB_KEY_ALIAS)
        prefs.edit()
            .putString("db_passphrase", result.data)
            .putString("db_passphrase_iv", result.iv)
            .apply()

        return passphrase
    }

    fun encrypt(data: String, alias: String = KEY_ALIAS): EncryptionResult {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(alias))
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return EncryptionResult(
            Base64.encodeToString(encryptedData, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(encryptedData: String, iv: String, alias: String = KEY_ALIAS): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(alias), spec)
        val decodedData = Base64.decode(encryptedData, Base64.NO_WRAP)
        return String(cipher.doFinal(decodedData), Charsets.UTF_8)
    }

    data class EncryptionResult(val data: String, val iv: String)
}
