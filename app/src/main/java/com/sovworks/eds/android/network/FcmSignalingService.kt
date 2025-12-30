package com.sovworks.eds.android.network

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.sovworks.eds.android.settings.UserSettings
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class FcmSignalingService : FirebaseMessagingService() {
    private val gson = Gson()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload.
        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            val from = remoteMessage.data["from"]
            val data = remoteMessage.data["data"]

            if (type != null && from != null && data != null) {
                processSignalingMessage(from, type, data)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        val settings = UserSettings.getSettings(applicationContext)
        val myId = WebRtcService.resolvePeerId(applicationContext, settings)
        val urls = settings.getSignalingServerUrls()
        urls.forEach { url ->
            HttpSignalingClient(url, myId).registerFcmToken(token)
        }
    }

    private fun processSignalingMessage(from: String, type: String, data: String) {
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        
        when (type) {
            "OFFER" -> manager.onOfferReceived(from, SessionDescription(SessionDescription.Type.OFFER, data))
            "ANSWER" -> manager.onAnswerReceived(from, SessionDescription(SessionDescription.Type.ANSWER, data))
            "CANDIDATE" -> {
                try {
                    val candidateMap = gson.fromJson(data, Map::class.java)
                    val candidate = IceCandidate(
                        candidateMap["sdpMid"] as String,
                        (candidateMap["sdpMLineIndex"] as Double).toInt(),
                        candidateMap["sdp"] as String
                    )
                    manager.onIceCandidateReceived(from, candidate)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing candidate data", e)
                }
            }
            "CONNECT_REQUEST" -> manager.onConnectionRequestReceived(from)
            "CONNECT_ACCEPT" -> manager.onConnectionAcceptReceived(from)
        }
    }

    companion object {
        private const val TAG = "FcmSignalingService"
    }
}
