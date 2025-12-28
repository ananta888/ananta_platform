package com.sovworks.eds.android.trust

data class TrustRecommendation(
    val recommenderFingerprint: String,
    val trustLevel: Int,
    val timestamp: Long = System.currentTimeMillis()
)
