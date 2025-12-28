package com.sovworks.eds.android.network

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
                // TODO: Error handling
            }

            override fun onResponse(call: Call, response: Response) {
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
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val messages = gson.fromJson(body, Array<SignalingMessage>::class.java)
                messages.forEach { msg ->
                    processMessage(msg)
                }
                response.close()
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
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
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
        }
    }

    private data class SignalingMessage(val from: String, val type: String, val data: String)
}
