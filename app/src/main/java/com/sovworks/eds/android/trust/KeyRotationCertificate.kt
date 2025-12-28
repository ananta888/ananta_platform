package com.sovworks.eds.android.trust

data class KeyRotationCertificate(
    val oldPublicKeyBase64: String,
    val newPublicKeyBase64: String,
    val signatureBase64: String
)
