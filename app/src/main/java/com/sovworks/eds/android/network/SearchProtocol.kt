package com.sovworks.eds.android.network

import com.google.gson.annotations.SerializedName

data class SearchRequest(
    @SerializedName("id") val requestId: String,
    @SerializedName("q") val query: String,
    @SerializedName("ttl") var ttl: Int = 3,
    @SerializedName("sender") val senderId: String
)

data class SearchResponse(
    @SerializedName("rid") val requestId: String,
    @SerializedName("results") val results: List<SharedFile>,
    @SerializedName("peer") val peerId: String
)

data class SharedFile(
    @SerializedName("n") val name: String,
    @SerializedName("s") val size: Long,
    @SerializedName("h") val hash: String? = null
)

data class DiscoveryMessage(
    @SerializedName("type") val type: String, // "search" or "response"
    @SerializedName("payload") val payload: String // JSON string of SearchRequest or SearchResponse
)
