package com.sovworks.eds.android.network

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

class PfsSession(private val isInitiator: Boolean) {
    private val rng = SecureRandom()
    private var localKey: X25519PrivateKeyParameters? = null
    private var remoteKey: X25519PublicKeyParameters? = null
    private var sendChainKey: ByteArray? = null
    private var recvChainKey: ByteArray? = null
    private var helloSent = false

    fun ensureHello(sendControl: (String) -> Unit) {
        if (helloSent) {
            return
        }
        val key = localKey ?: X25519PrivateKeyParameters(rng).also { localKey = it }
        val pubKey = key.generatePublicKey().encoded
        val payload = Base64.encodeToString(pubKey, Base64.NO_WRAP)
        sendControl("${PFS_HELLO_PREFIX}$payload")
        helloSent = true
    }

    fun handleControl(message: String, sendControl: (String) -> Unit): Boolean {
        if (message.startsWith(PFS_HELLO_PREFIX)) {
            val payload = message.removePrefix(PFS_HELLO_PREFIX)
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            remoteKey = X25519PublicKeyParameters(decoded, 0)
            if (localKey == null) {
                localKey = X25519PrivateKeyParameters(rng)
                ensureHello(sendControl)
            }
            maybeCompleteHandshake()
            return true
        }
        if (message.startsWith(PFS_REKEY_PREFIX)) {
            val payload = message.removePrefix(PFS_REKEY_PREFIX)
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            remoteKey = X25519PublicKeyParameters(decoded, 0)
            maybeCompleteHandshake()
            return true
        }
        return false
    }

    fun isReady(): Boolean = sendChainKey != null && recvChainKey != null

    fun encryptText(plaintext: String): String? {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8)) ?: return null
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptText(payload: String): String? {
        val decoded = Base64.decode(payload, Base64.NO_WRAP)
        val decrypted = decrypt(decoded) ?: return null
        return String(decrypted, Charsets.UTF_8)
    }

    fun encryptBinary(data: ByteArray): ByteArray? = encrypt(data)

    fun decryptBinary(data: ByteArray): ByteArray? = decrypt(data)

    private fun encrypt(data: ByteArray): ByteArray? {
        val chainKey = sendChainKey ?: return null
        val (messageKey, nextChainKey) = deriveMessageKey(chainKey)
        sendChainKey = nextChainKey

        val iv = ByteArray(GCM_IV_SIZE).also { rng.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(messageKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    private fun decrypt(data: ByteArray): ByteArray? {
        val chainKey = recvChainKey ?: return null
        if (data.size <= GCM_IV_SIZE) {
            return null
        }
        val iv = data.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = data.copyOfRange(GCM_IV_SIZE, data.size)
        val (messageKey, nextChainKey) = deriveMessageKey(chainKey)
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val keySpec = SecretKeySpec(messageKey, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            recvChainKey = nextChainKey
            plaintext
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveMessageKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = hmacSha256(chainKey, MSG_KEY_INFO)
        val nextChainKey = hmacSha256(chainKey, CHAIN_KEY_INFO)
        return messageKey to nextChainKey
    }

    private fun maybeCompleteHandshake() {
        val local = localKey ?: return
        val remote = remoteKey ?: return
        val agreement = X25519Agreement()
        agreement.init(local)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(remote, sharedSecret, 0)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, null, HKDF_INFO))
        val output = ByteArray(HKDF_OUTPUT_SIZE)
        hkdf.generateBytes(output, 0, output.size)

        val first = output.copyOfRange(0, CHAIN_KEY_SIZE)
        val second = output.copyOfRange(CHAIN_KEY_SIZE, CHAIN_KEY_SIZE * 2)
        if (isInitiator) {
            sendChainKey = first
            recvChainKey = second
        } else {
            sendChainKey = second
            recvChainKey = first
        }
    }

    private fun hmacSha256(key: ByteArray, info: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(info)
    }

    companion object {
        const val PFS_HELLO_PREFIX = "PFS_HELLO:"
        const val PFS_REKEY_PREFIX = "PFS_REKEY:"
        const val PFS_MESSAGE_PREFIX = "PFS_MSG:"

        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_SIZE = 12
        private const val CHAIN_KEY_SIZE = 32
        private const val HKDF_OUTPUT_SIZE = CHAIN_KEY_SIZE * 2
        private val HKDF_INFO = "ananta-pfs-v1".toByteArray(Charsets.UTF_8)
        private val MSG_KEY_INFO = "message".toByteArray(Charsets.UTF_8)
        private val CHAIN_KEY_INFO = "chain".toByteArray(Charsets.UTF_8)
    }
}
