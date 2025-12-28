package com.sovworks.eds.android.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchProtocolTest {
    private val gson = Gson()

    @Test
    fun testSearchRequestSerialization() {
        val request = SearchRequest(
            requestId = "123",
            query = "test",
            ttl = 3,
            senderId = "peer1"
        )
        val json = gson.toJson(request)
        val deserialized = gson.fromJson(json, SearchRequest::class.java)
        
        assertEquals(request.requestId, deserialized.requestId)
        assertEquals(request.query, deserialized.query)
        assertEquals(request.ttl, deserialized.ttl)
        assertEquals(request.senderId, deserialized.senderId)
    }

    @Test
    fun testDiscoveryMessageWrapping() {
        val request = SearchRequest("123", "test", 3, "peer1")
        val msg = DiscoveryMessage("search", gson.toJson(request))
        val json = gson.toJson(msg)
        
        val decodedMsg = gson.fromJson(json, DiscoveryMessage::class.java)
        assertEquals("search", decodedMsg.type)
        
        val decodedRequest = gson.fromJson(decodedMsg.payload, SearchRequest::class.java)
        assertEquals("test", decodedRequest.query)
    }
}
