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
        val keyPair = generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters

        val identity = Identity(
            id = name,
            publicKeyBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP),
            privateKeyBase64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        )

        saveIdentity(context, identity)
        return identity
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

    fun generateKeyPair(): AsymmetricCipherKeyPair {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        return generator.generateKeyPair()
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
