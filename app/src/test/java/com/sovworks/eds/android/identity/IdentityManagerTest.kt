package com.sovworks.eds.android.identity

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityManagerTest {

    @Test
    fun testKeyPairGeneration() {
        val keyPair = IdentityManager.generateKeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)
        
        assertTrue(keyPair.private is Ed25519PrivateKeyParameters)
        assertTrue(keyPair.public is Ed25519PublicKeyParameters)
    }

    @Test
    fun testSigningAndVerification() {
        val keyPair = IdentityManager.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        
        val data = "Hello, Ananta!".toByteArray()
        val signature = IdentityManager.sign(privateKey, data)
        
        assertNotNull(signature)
        assertTrue(IdentityManager.verify(publicKey, data, signature))
    }

    @Test
    fun testInvalidSignature() {
        val keyPair = IdentityManager.generateKeyPair()
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        
        val data = "Hello, Ananta!".toByteArray()
        val signature = IdentityManager.sign(keyPair.private as Ed25519PrivateKeyParameters, data)
        
        val tamperedData = "Hello, World!".toByteArray()
        assertTrue(!IdentityManager.verify(publicKey, tamperedData, signature))
    }

    @Test
    fun testRecoveryFromSeed() {
        val seed = IdentityManager.generateSeed()
        val keyPair1 = IdentityManager.generateKeyPairFromSeed(seed)
        val keyPair2 = IdentityManager.generateKeyPairFromSeed(seed)
        
        val pub1 = keyPair1.public as Ed25519PublicKeyParameters
        val pub2 = keyPair2.public as Ed25519PublicKeyParameters
        
        assertTrue(pub1.encoded.contentEquals(pub2.encoded))
    }
}
