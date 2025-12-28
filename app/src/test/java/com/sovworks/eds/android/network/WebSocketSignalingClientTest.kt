package com.sovworks.eds.android.network

import com.google.gson.Gson
import okhttp3.WebSocket
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.check
import org.webrtc.SessionDescription
import org.webrtc.IceCandidate
import org.junit.Assert.assertEquals

class WebSocketSignalingClientTest {
    private lateinit var client: WebSocketSignalingClient
    private val listener: SignalingListener = mock()
    private val webSocket: WebSocket = mock()

    @Before
    fun setup() {
        client = WebSocketSignalingClient("http://localhost", "myId")
        client.setListener(listener)
    }

    @Test
    fun `onMessage with OFFER should notify listener`() {
        val msgJson = """{"from":"other","type":"OFFER","data":"sdp-content"}"""
        client.onMessage(webSocket, msgJson)
        
        verify(listener).onOfferReceived(check {
            assertEquals("other", it)
        }, check {
            assertEquals(SessionDescription.Type.OFFER, it.type)
            assertEquals("sdp-content", it.description)
        })
    }

    @Test
    fun `onMessage with ANSWER should notify listener`() {
        val msgJson = """{"from":"other","type":"ANSWER","data":"sdp-content"}"""
        client.onMessage(webSocket, msgJson)
        
        verify(listener).onAnswerReceived(check {
            assertEquals("other", it)
        }, check {
            assertEquals(SessionDescription.Type.ANSWER, it.type)
            assertEquals("sdp-content", it.description)
        })
    }
    
    @Test
    fun `onMessage with CANDIDATE should notify listener`() {
        val candidateData = """{"sdp":"cand","sdpMid":"0","sdpMLineIndex":1.0}"""
        val msgJson = """{"from":"other","type":"CANDIDATE","data":$candidateData}"""
        client.onMessage(webSocket, msgJson)
        
        verify(listener).onIceCandidateReceived(check {
            assertEquals("other", it)
        }, check {
            assertEquals("cand", it.sdp)
            assertEquals("0", it.sdpMid)
            assertEquals(1, it.sdpMLineIndex)
        })
    }
}
