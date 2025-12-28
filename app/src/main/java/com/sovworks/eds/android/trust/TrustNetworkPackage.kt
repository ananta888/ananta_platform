package com.sovworks.eds.android.trust

import com.sovworks.eds.android.identity.Identity
import com.sovworks.eds.android.identity.IdentityManager

data class TrustNetworkPackage(
    val issuerPublicKeyBase64: String,
    val trustedKeys: List<TrustedKey>,
    val signatureBase64: String
)
