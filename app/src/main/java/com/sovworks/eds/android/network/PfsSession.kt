package com.sovworks.eds.android.network

import android.util.Base64
import java.security.SecureRandom
import java.util.Arrays
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
    
    private var rootKey: ByteArray? = null
    private var sendChainKey: ByteArray? = null
    private var recvChainKey: ByteArray? = null
    
    private var dhLocalKey = X25519PrivateKeyParameters(rng)
    private var dhRemoteKey: X25519PublicKeyParameters? = null
    
    private var helloSent = false

    fun ensureHello(sendControl: (String) -> Unit) {
        if (helloSent) {
            return
        }
        val key = localKey ?: X25519PrivateKeyParameters(rng).also { localKey = it }
        val pubKey = key.generatePublicKey().encoded
        val dhPubKey = dhLocalKey.generatePublicKey().encoded
        
        val payload = Base64.encodeToString(pubKey + dhPubKey, Base64.NO_WRAP)
        sendControl("${PFS_HELLO_PREFIX}$payload")
        helloSent = true
    }

    fun handleControl(message: String, sendControl: (String) -> Unit): Boolean {
        if (message.startsWith(PFS_HELLO_PREFIX)) {
            val payload = message.removePrefix(PFS_HELLO_PREFIX)
            val decoded = Base64.decode(payload, Base64.NO_WRAP)
            if (decoded.size < 64) return false
            
            val pubKeyBytes = decoded.copyOfRange(0, 32)
            val dhPubKeyBytes = decoded.copyOfRange(32, 64)
            
            remoteKey = X25519PublicKeyParameters(pubKeyBytes, 0)
            dhRemoteKey = X25519PublicKeyParameters(dhPubKeyBytes, 0)
            
            if (localKey == null) {
                localKey = X25519PrivateKeyParameters(rng)
                ensureHello(sendControl)
            }
            maybeCompleteHandshake()
            return true
        }
        return false
    }

    fun isReady(): Boolean = rootKey != null && sendChainKey != null && recvChainKey != null

    fun encryptText(plaintext: String): String? {
        val encrypted = encrypt(plaintext.toByteArray(Charsets.UTF_8)) ?: return null
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptText(payload: String): String? {
        val decoded = try { Base64.decode(payload, Base64.NO_WRAP) } catch (e: Exception) { return null }
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
        val dhPub = dhLocalKey.generatePublicKey().encoded
        
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val keySpec = SecretKeySpec(messageKey, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(data)
        
        // Format: IV (12) + DH_PUB (32) + Ciphertext
        return iv + dhPub + ciphertext
    }

    private fun decrypt(data: ByteArray): ByteArray? {
        if (data.size <= GCM_IV_SIZE + 32) {
            return null
        }
        val iv = data.copyOfRange(0, GCM_IV_SIZE)
        val remoteDhPubBytes = data.copyOfRange(GCM_IV_SIZE, GCM_IV_SIZE + 32)
        val ciphertext = data.copyOfRange(GCM_IV_SIZE + 32, data.size)
        
        val newRemoteDhKey = X25519PublicKeyParameters(remoteDhPubBytes, 0)
        if (dhRemoteKey == null || !Arrays.equals(newRemoteDhKey.encoded, dhRemoteKey?.encoded)) {
            performDhRatchet(newRemoteDhKey)
        }

        val chainKey = recvChainKey ?: return null
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

    private fun performDhRatchet(newRemoteDhKey: X25519PublicKeyParameters) {
        dhRemoteKey = newRemoteDhKey
        val root = rootKey ?: return
        
        // Receiving chain update
        val sharedSecret1 = calculateAgreement(dhLocalKey, newRemoteDhKey)
        val (newRoot1, newRecvChain) = kdfRatchet(root, sharedSecret1)
        rootKey = newRoot1
        recvChainKey = newRecvChain
        
        // New local DH key for next sending chain
        dhLocalKey = X25519PrivateKeyParameters(rng)
        val sharedSecret2 = calculateAgreement(dhLocalKey, newRemoteDhKey)
        val (newRoot2, newSendChain) = kdfRatchet(rootKey!!, sharedSecret2)
        rootKey = newRoot2
        sendChainKey = newSendChain
    }

    private fun kdfRatchet(rootKey: ByteArray, sharedSecret: ByteArray): Pair<ByteArray, ByteArray> {
        val output = hkdf(sharedSecret, rootKey, HKDF_INFO_RATCHET, 64)
        return output.copyOfRange(0, 32) to output.copyOfRange(32, 64)
    }

    private fun deriveMessageKey(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val messageKey = hmacSha256(chainKey, MSG_KEY_INFO)
        val nextChainKey = hmacSha256(chainKey, CHAIN_KEY_INFO)
        return messageKey to nextChainKey
    }

    private fun maybeCompleteHandshake() {
        val local = localKey ?: return
        val remote = remoteKey ?: return
        val sharedSecret = calculateAgreement(local, remote)

        rootKey = hkdf(sharedSecret, null, HKDF_INFO_ROOT, 32)
        
        // Initial chains
        if (isInitiator) {
            // Initiator sends first, so we need a sending chain
            // Standard Double Ratchet: initiator has the other's DH public key
            // Here we just initialize both from root for simplicity in the first step
            val output = hkdf(rootKey!!, null, HKDF_INFO_CHAIN, 64)
            sendChainKey = output.copyOfRange(0, 32)
            recvChainKey = output.copyOfRange(32, 64)
        } else {
            val output = hkdf(rootKey!!, null, HKDF_INFO_CHAIN, 64)
            sendChainKey = output.copyOfRange(32, 64)
            recvChainKey = output.copyOfRange(0, 32)
        }
    }

    private fun calculateAgreement(privateKey: X25519PrivateKeyParameters, publicKey: X25519PublicKeyParameters): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val sharedSecret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKey, sharedSecret, 0)
        return sharedSecret
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        val output = ByteArray(length)
        hkdf.generateBytes(output, 0, output.size)
        return output
    }

    private fun hmacSha256(key: ByteArray, info: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(info)
    }

    companion object {
        const val PFS_HELLO_PREFIX = "PFS_HELLO:"
        const val PFS_MESSAGE_PREFIX = "PFS_MSG:"

        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_SIZE = 12
        
        private val HKDF_INFO_ROOT = "ananta-pfs-root".toByteArray()
        private val HKDF_INFO_CHAIN = "ananta-pfs-chain".toByteArray()
        private val HKDF_INFO_RATCHET = "ananta-pfs-ratchet".toByteArray()
        private val MSG_KEY_INFO = "message".toByteArray()
        private val CHAIN_KEY_INFO = "chain".toByteArray()
    }
}
