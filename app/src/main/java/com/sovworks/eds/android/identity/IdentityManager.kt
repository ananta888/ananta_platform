package com.sovworks.eds.android.identity

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import com.sovworks.eds.android.security.SecurityUtils
import java.io.File
import java.security.SecureRandom
import java.security.Security

object IdentityManager {
    private const val IDENTITY_FILE = "user_identity.json"
    private val gson = Gson()

    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    fun createNewIdentity(context: Context, name: String): Identity {
        val seed = generateSeed()
        return recoverIdentity(context, seed, name)
    }

    fun rotateKeys(context: Context): Identity? {
        val currentIdentity = loadIdentity(context) ?: return null
        val oldPrivateKey = getDecryptedPrivateKey(currentIdentity) ?: return null

        val newKeyPair = generateKeyPair()
        val newPrivateKey = newKeyPair.private as Ed25519PrivateKeyParameters
        val newPublicKey = newKeyPair.public as Ed25519PublicKeyParameters

        val newPublicKeyEncoded = Base64.encodeToString(newPublicKey.encoded, Base64.NO_WRAP)

        // Sign the new public key with the old private key as proof of rotation
        val rotationSignature = sign(oldPrivateKey, newPublicKey.encoded)

        val privateKeyRaw = Base64.encodeToString(newPrivateKey.encoded, Base64.NO_WRAP)
        val encryptionResult = SecurityUtils.encrypt(privateKeyRaw)

        val newIdentity = currentIdentity.copy(
            publicKeyBase64 = newPublicKeyEncoded,
            privateKeyBase64 = encryptionResult.data,
            ivBase64 = encryptionResult.iv,
            previousPublicKeyBase64 = currentIdentity.publicKeyBase64,
            rotationSignatureBase64 = Base64.encodeToString(rotationSignature, Base64.NO_WRAP)
        )

        saveIdentity(context, newIdentity)
        return newIdentity
    }

    fun recoverIdentity(context: Context, seed: ByteArray, name: String): Identity {
        val keyPair = generateKeyPairFromSeed(seed)
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters

        val privateKeyRaw = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        val encryptionResult = SecurityUtils.encrypt(privateKeyRaw)

        val identity = Identity(
            id = name,
            publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
            privateKeyBase64 = encryptionResult.data,
            ivBase64 = encryptionResult.iv
        )

        saveIdentity(context, identity)
        return identity
    }

    fun getDecryptedPrivateKey(identity: Identity): Ed25519PrivateKeyParameters? {
        val encryptedKey = identity.getEncryptedPrivateKey() ?: return null
        val iv = identity.getIv() ?: return identity.getPrivateKey() // Fallback for old unencrypted keys

        return try {
            val decryptedKeyRaw = SecurityUtils.decrypt(encryptedKey, iv)
            identity.getPrivateKey(decryptedKeyRaw)
        } catch (e: Exception) {
            // Fallback for migration or if encryption failed
            identity.getPrivateKey()
        }
    }

    private fun saveIdentity(context: Context, identity: Identity) {
        val file = File(context.filesDir, IDENTITY_FILE)
        file.writeText(gson.toJson(identity))
    }

    fun loadIdentity(context: Context): Identity? {
        val file = File(context.filesDir, IDENTITY_FILE)
        return if (file.exists()) {
            gson.fromJson(file.readText(), Identity::class.java)
        } else {
            null
        }
    }

    fun generateSeed(): ByteArray {
        val seed = ByteArray(32)
        SecureRandom().nextBytes(seed)
        return seed
    }

    fun generateKeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        return generator.generateKeyPair()
    }

    fun generateKeyPairFromSeed(seed: ByteArray): AsymmetricCipherKeyPair {
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey()
        return AsymmetricCipherKeyPair(publicKey, privateKey)
    }

    fun sign(privateKey: Ed25519PrivateKeyParameters, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun verify(publicKey: Ed25519PublicKeyParameters, data: ByteArray, signature: ByteArray): Boolean {
        val signer = Ed25519Signer()
        signer.init(false, publicKey)
        signer.update(data, 0, data.size)
        return signer.verifySignature(signature)
    }

    fun getPublicKeyBytes(publicKey: Ed25519PublicKeyParameters): ByteArray {
        return publicKey.encoded
    }

    fun getPrivateKeyBytes(privateKey: Ed25519PrivateKeyParameters): ByteArray {
        return privateKey.encoded
    }
}
