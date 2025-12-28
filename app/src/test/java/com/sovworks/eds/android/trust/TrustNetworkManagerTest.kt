package com.sovworks.eds.android.trust

import android.util.Base64
import com.sovworks.eds.android.identity.IdentityManager
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrustNetworkManagerTest {

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
