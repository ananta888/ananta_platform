package com.sovworks.eds.android.network

import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.SecureRandom
import java.util.Arrays

@RunWith(RobolectricTestRunner::class)
class PfsSessionTest {

    private fun generateIdentityKeys(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        return (keyPair.private as Ed25519PrivateKeyParameters) to (keyPair.public as Ed25519PublicKeyParameters)
    }

    @Test
    fun testHandshakeAndCommunication() {
        val (alicePriv, alicePub) = generateIdentityKeys()
        val (bobPriv, bobPub) = generateIdentityKeys()

        val alice = PfsSession(isInitiator = true, localIdentityKey = alicePriv, remoteIdentityKey = bobPub)
        val bob = PfsSession(isInitiator = false, localIdentityKey = bobPriv, remoteIdentityKey = alicePub)

        var aliceToBobMsg: String? = null
        var bobToAliceMsg: String? = null

        // Alice starts handshake
        alice.ensureHello { aliceToBobMsg = it }
        assertNotNull(aliceToBobMsg)
        assertTrue(aliceToBobMsg!!.startsWith(PfsSession.PFS_HELLO_PREFIX))

        // Bob receives Alice's hello and responds with his own hello
        bob.handleControl(aliceToBobMsg!!, { bobToAliceMsg = it })
        assertNotNull(bobToAliceMsg)
        assertTrue(bobToAliceMsg!!.startsWith(PfsSession.PFS_HELLO_PREFIX))

        // Alice receives Bob's hello
        alice.handleControl(bobToAliceMsg!!, { fail("Alice should not send more control messages") })

        assertTrue(alice.isReady())
        assertTrue(bob.isReady())

        // Alice sends a message to Bob
        val originalText = "Hello Bob!"
        val encryptedBase64 = alice.encryptText(originalText)
        assertNotNull(encryptedBase64)

        val decryptedText = bob.decryptText(encryptedBase64!!)
        assertEquals(originalText, decryptedText)

        // Bob sends a message back
        val responseText = "Hi Alice!"
        val responseEncrypted = bob.encryptText(responseText)
        assertNotNull(responseEncrypted)

        val responseDecrypted = alice.decryptText(responseEncrypted!!)
        assertEquals(responseText, responseDecrypted)
    }

    @Test
    fun testSequentialMessages() {
        val (alicePriv, alicePub) = generateIdentityKeys()
        val (bobPriv, bobPub) = generateIdentityKeys()

        val alice = PfsSession(isInitiator = true, localIdentityKey = alicePriv, remoteIdentityKey = bobPub)
        val bob = PfsSession(isInitiator = false, localIdentityKey = bobPriv, remoteIdentityKey = alicePub)

        alice.ensureHello { msg -> bob.handleControl(msg, { msg2 -> alice.handleControl(msg2, {}) }) }

        for (i in 1..10) {
            val text = "Message $i"
            val enc = alice.encryptText(text)
            val dec = bob.decryptText(enc!!)
            assertEquals(text, dec)
        }
    }

    @Test
    fun testDoubleRatchet() {
        val (alicePriv, alicePub) = generateIdentityKeys()
        val (bobPriv, bobPub) = generateIdentityKeys()

        val alice = PfsSession(isInitiator = true, localIdentityKey = alicePriv, remoteIdentityKey = bobPub)
        val bob = PfsSession(isInitiator = false, localIdentityKey = bobPriv, remoteIdentityKey = alicePub)

        alice.ensureHello { msg -> bob.handleControl(msg, { msg2 -> alice.handleControl(msg2, {}) }) }

        // Alice -> Bob
        val enc1 = alice.encryptText("Alice 1")
        assertEquals("Alice 1", bob.decryptText(enc1!!))

        // Bob -> Alice (triggers Ratchet at Alice upon receipt)
        val enc2 = bob.encryptText("Bob 1")
        assertEquals("Bob 1", alice.decryptText(enc2!!))

        // Alice -> Bob (triggers Ratchet at Bob upon receipt)
        val enc3 = alice.encryptText("Alice 2")
        assertEquals("Alice 2", bob.decryptText(enc3!!))
    }
}
