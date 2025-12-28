package com.sovworks.eds.android.network

import com.google.gson.annotations.SerializedName

data class StoreRequest(
    @SerializedName("recipient") val recipientId: String,
    @SerializedName("sender") val senderId: String,
    @SerializedName("payload") val encryptedPayload: String,
    @SerializedName("ts") val timestamp: Long,
    @SerializedName("sig") val signature: String? = null
)

data class RetrieveRequest(
    @SerializedName("recipient") val recipientId: String,
    @SerializedName("ts_since") val timestampSince: Long
)

data class OfflineMessageBundle(
    @SerializedName("messages") val messages: List<StoreRequest>
)

data class OfflineMessagingMessage(
    @SerializedName("type") val type: String, // "store", "retrieve", "bundle"
    @SerializedName("payload") val payload: String
)
