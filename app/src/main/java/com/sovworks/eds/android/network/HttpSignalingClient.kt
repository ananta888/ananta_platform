package com.sovworks.eds.android.network

import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpSignalingClient(
    private val serverUrl: String,
    private val myId: String
) : SignalingClient {
    companion object {
        private const val TAG = "HttpSignalingClient"
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var listener: SignalingListener? = null
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override fun sendOffer(peerId: String, sdp: SessionDescription) {
        sendPayload(peerId, "OFFER", sdp.description)
    }

    override fun sendAnswer(peerId: String, sdp: SessionDescription) {
        sendPayload(peerId, "ANSWER", sdp.description)
    }

    override fun sendIceCandidate(peerId: String, candidate: IceCandidate) {
        val candidateMap = mapOf(
            "sdp" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        sendPayload(peerId, "CANDIDATE", gson.toJson(candidateMap))
    }

    override fun sendConnectionRequest(peerId: String) {
        sendPayload(peerId, "CONNECT_REQUEST", "")
    }

    override fun sendConnectionAccept(peerId: String) {
        sendPayload(peerId, "CONNECT_ACCEPT", "")
    }

    override fun setListener(listener: SignalingListener) {
        this.listener = listener
    }

    private fun sendPayload(peerId: String, type: String, data: String) {
        val bodyMap = mapOf(
            "from" to myId,
            "to" to peerId,
            "type" to type,
            "data" to data
        )
        val body = gson.toJson(bodyMap).toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$serverUrl/message")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to send $type to $peerId", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.w(TAG, "Send $type failed with HTTP ${response.code}")
                }
                response.close()
            }
        })
    }

    fun pollMessages() {
        val request = Request.Builder()
            .url("$serverUrl/messages?id=$myId")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to poll messages for $myId", e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Poll messages failed with HTTP ${response.code}")
                        return
                    }
                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        Log.w(TAG, "Poll messages returned empty body")
                        return
                    }
                    val messages = gson.fromJson(body, Array<SignalingMessage>::class.java)
                    messages.forEach { msg ->
                        processMessage(msg)
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    fun registerFcmToken(token: String) {
        val bodyMap = mapOf(
            "id" to myId,
            "fcmToken" to token
        )
        val body = gson.toJson(bodyMap).toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$serverUrl/register-fcm")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "Failed to register FCM token", e)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.w(TAG, "Register FCM token failed with HTTP ${response.code}")
                }
                response.close()
            }
        })
    }

    private fun processMessage(msg: SignalingMessage) {
        when (msg.type) {
            "OFFER" -> listener?.onOfferReceived(msg.from, SessionDescription(SessionDescription.Type.OFFER, msg.data))
            "ANSWER" -> listener?.onAnswerReceived(msg.from, SessionDescription(SessionDescription.Type.ANSWER, msg.data))
            "CANDIDATE" -> {
                val candidateMap = gson.fromJson(msg.data, Map::class.java)
                val candidate = IceCandidate(
                    candidateMap["sdpMid"] as String,
                    (candidateMap["sdpMLineIndex"] as Double).toInt(),
                    candidateMap["sdp"] as String
                )
                listener?.onIceCandidateReceived(msg.from, candidate)
            }
            "CONNECT_REQUEST" -> listener?.onConnectionRequestReceived(msg.from)
            "CONNECT_ACCEPT" -> listener?.onConnectionAcceptReceived(msg.from)
        }
    }

    private data class SignalingMessage(val from: String, val type: String, val data: String)
}
