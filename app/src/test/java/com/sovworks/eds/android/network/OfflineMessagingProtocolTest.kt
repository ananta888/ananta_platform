package com.sovworks.eds.android.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineMessagingProtocolTest {
    private val gson = Gson()

    @Test
    fun testStoreRequestSerialization() {
        val request = StoreRequest(
            recipientId = "rec1",
            senderId = "send1",
            encryptedPayload = "secret",
            timestamp = 1000L
        )
        val json = gson.toJson(request)
        val deserialized = gson.fromJson(json, StoreRequest::class.java)
        
        assertEquals(request.recipientId, deserialized.recipientId)
        assertEquals(request.senderId, deserialized.senderId)
        assertEquals(request.encryptedPayload, deserialized.encryptedPayload)
        assertEquals(request.timestamp, deserialized.timestamp)
    }

    @Test
    fun testOfflineMessagingMessageWrapping() {
        val request = StoreRequest("rec1", "send1", "secret", 1000L)
        val msg = OfflineMessagingMessage("store", gson.toJson(request))
        val json = gson.toJson(msg)
        
        val decodedMsg = gson.fromJson(json, OfflineMessagingMessage::class.java)
        assertEquals("store", decodedMsg.type)
        
        val decodedRequest = gson.fromJson(decodedMsg.payload, StoreRequest::class.java)
        assertEquals("secret", decodedRequest.encryptedPayload)
    }
}
