package com.sovworks.eds.android.trust

import android.util.Base64
import com.sovworks.eds.android.identity.IdentityManager
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TrustNetworkManagerTest {

    @Test
    fun testTrustRankCalculation() {
        val context = RuntimeEnvironment.getApplication()
        val trustStore = TrustStore.getInstance(context)
        
        val alice = "alice-pub"
        val bob = "bob-pub"
        val charlie = "charlie-pub"
        
        // 1. Wir vertrauen Alice direkt (5 Sterne)
        val aliceKey = TrustedKey(alice, alice, "Alice")
        aliceKey.trustLevel = 5
        aliceKey.status = TrustedKey.TrustStatus.TRUSTED
        trustStore.addKey(aliceKey)
        
        // 2. Alice empfiehlt Bob (4 Sterne)
        val bobKey = TrustedKey(bob, bob, "Bob")
        bobKey.addRecommendation(TrustRecommendation(alice, 4))
        trustStore.addKey(bobKey)
        
        // 3. Bob empfiehlt Charlie (3 Sterne)
        val charlieKey = TrustedKey(charlie, charlie, "Charlie")
        charlieKey.addRecommendation(TrustRecommendation(bob, 3))
        trustStore.addKey(charlieKey)
        
        // Berechne Ränge
        val aliceRank = TrustNetworkManager.calculateTrustRank(context, alice)
        val bobRank = TrustNetworkManager.calculateTrustRank(context, bob)
        val charlieRank = TrustNetworkManager.calculateTrustRank(context, charlie)
        
        assertEquals(5.0, aliceRank, 0.001)
        // BobRank = Direct(0) + AliceRank(5) * RecLevel(4)/5 = 0 + 5 * 0.8 = 4.0
        assertEquals(4.0, bobRank, 0.001)
        // CharlieRank = Direct(0) + BobRank(4) * RecLevel(3)/5 = 0 + 4 * 0.6 = 2.4
        assertEquals(2.4, charlieRank, 0.001)
    }

    @Test
    fun testKeyRotationVerification() {
        // 1. Altes Key-Paar generieren
        val oldKeyPair = IdentityManager.generateKeyPair()
        val oldPrivateKey = oldKeyPair.private as Ed25519PrivateKeyParameters
        val oldPublicKey = oldKeyPair.public as Ed25519PublicKeyParameters
        val oldPublicKeyBase64 = java.util.Base64.getEncoder().encodeToString(oldPublicKey.encoded)

        // 2. Neues Key-Paar generieren
        val newKeyPair = IdentityManager.generateKeyPair()
        val newPublicKey = newKeyPair.public as Ed25519PublicKeyParameters
        val newPublicKeyBase64 = java.util.Base64.getEncoder().encodeToString(newPublicKey.encoded)

        // 3. Rotation signieren (neuer Public Key mit altem Private Key signiert)
        val signature = IdentityManager.sign(oldPrivateKey, newPublicKey.encoded)
        val signatureBase64 = java.util.Base64.getEncoder().encodeToString(signature)

        // 4. Zertifikat erstellen
        val certificate = KeyRotationCertificate(
            oldPublicKeyBase64 = oldPublicKeyBase64,
            newPublicKeyBase64 = newPublicKeyBase64,
            signatureBase64 = signatureBase64
        )

        // 5. Verifizieren
        val isValid = TrustNetworkManager.verifyRotationCertificate(certificate)
        assertTrue(isValid)
    }
}
