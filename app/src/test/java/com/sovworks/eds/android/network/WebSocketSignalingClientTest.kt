package com.sovworks.eds.android.network

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import okhttp3.WebSocket
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.webrtc.SessionDescription
import org.webrtc.IceCandidate
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketSignalingClientTest {
    private lateinit var client: WebSocketSignalingClient
    private val listener: SignalingListener = mock()
    private val webSocket: WebSocket = mock()
    private val mockClient: OkHttpClient = mock()
    private val gson = Gson()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        whenever(mockClient.newWebSocket(any(), any())).thenReturn(webSocket)
        client = WebSocketSignalingClient(
            "http://localhost",
            "myId",
            "myPublicKey",
            mockClient,
            testScope
        )
        client.setListener(listener)
    }

    @Test
    fun `onMessage with OFFER should notify listener`() {
        val msgJson = """{"type":"signal","fromPublicKey":"otherKey","payload":{"type":"OFFER","data":"sdp-content"}}"""
        client.onMessage(webSocket, msgJson)

        verify(listener).onOfferReceivedFromKey(check {
            assertEquals("otherKey", it)
        }, check {
            assertEquals(SessionDescription.Type.OFFER, it.type)
            assertEquals("sdp-content", it.description)
        })
    }

    @Test
    fun `onMessage with ANSWER should notify listener`() {
        val msgJson = """{"type":"signal","fromPublicKey":"otherKey","payload":{"type":"ANSWER","data":"sdp-content"}}"""
        client.onMessage(webSocket, msgJson)

        verify(listener).onAnswerReceivedFromKey(check {
            assertEquals("otherKey", it)
        }, check {
            assertEquals(SessionDescription.Type.ANSWER, it.type)
            assertEquals("sdp-content", it.description)
        })
    }
    
    @Test
    fun `onMessage with CANDIDATE should notify listener`() {
        val candidateMap = mapOf("sdp" to "cand", "sdpMid" to "0", "sdpMLineIndex" to 1.0)
        val msgJson = """{"type":"signal","fromPublicKey":"otherKey","payload":{"type":"CANDIDATE","data":${gson.toJson(gson.toJson(candidateMap))}}}"""
        client.onMessage(webSocket, msgJson)

        verify(listener).onIceCandidateReceivedFromKey(check {
            assertEquals("otherKey", it)
        }, check {
            assertEquals("cand", it.sdp)
            assertEquals("0", it.sdpMid)
            assertEquals(1, it.sdpMLineIndex)
        })
    }

    @Test
    fun `sendOffer should send correct JSON`() {
        val sdp = SessionDescription(SessionDescription.Type.OFFER, "sdp-content")
        client.sendOffer("peerId", sdp)

        verify(webSocket).send(check<String> {
            val map = gson.fromJson(it, Map::class.java)
            assertEquals("peerId", map["to"])
            assertEquals("peerId", map["toPublicKey"])
            assertEquals("signal", map["type"])
            val payload = map["payload"] as Map<*, *>
            assertEquals("OFFER", payload["type"])
            assertEquals("sdp-content", payload["data"])
        })
    }

    @Test
    fun `sendAnswer should send correct JSON`() {
        val sdp = SessionDescription(SessionDescription.Type.ANSWER, "sdp-content")
        client.sendAnswer("peerId", sdp)

        verify(webSocket).send(check<String> {
            val map = gson.fromJson(it, Map::class.java)
            assertEquals("peerId", map["to"])
            assertEquals("peerId", map["toPublicKey"])
            assertEquals("signal", map["type"])
            val payload = map["payload"] as Map<*, *>
            assertEquals("ANSWER", payload["type"])
            assertEquals("sdp-content", payload["data"])
        })
    }

    @Test
    fun `sendIceCandidate should send correct JSON`() {
        val candidate = IceCandidate("0", 1, "cand")
        client.sendIceCandidate("peerId", candidate)

        verify(webSocket).send(check<String> {
            val map = gson.fromJson(it, Map::class.java)
            assertEquals("peerId", map["to"])
            assertEquals("peerId", map["toPublicKey"])
            assertEquals("signal", map["type"])
            val payload = map["payload"] as Map<*, *>
            assertEquals("CANDIDATE", payload["type"])
            val dataMap = gson.fromJson(payload["data"] as String, Map::class.java)
            assertEquals("cand", dataMap["sdp"])
            assertEquals("0", dataMap["sdpMid"])
            assertEquals(1.0, dataMap["sdpMLineIndex"])
        })
    }

    @Test
    fun `onFailure should trigger reconnect after delay`() {
        client.onFailure(webSocket, RuntimeException("Error"), null)
        
        // At first, no new connection attempt
        verify(mockClient, times(1)).newWebSocket(any(), any()) // only the one from init
        
        // Advance time by 5 seconds
        testDispatcher.scheduler.advanceTimeBy(5001)
        testDispatcher.scheduler.runCurrent()
        
        // Now there should be a second connection attempt
        verify(mockClient, times(2)).newWebSocket(any(), any())
    }
}
