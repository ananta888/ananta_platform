package com.sovworks.eds.android.identity

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.SecureRandom
import java.security.Security

object IdentityManager {

    init {
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
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
