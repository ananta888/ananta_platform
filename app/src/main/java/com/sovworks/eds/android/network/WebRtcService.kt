package com.sovworks.eds.android.network

import android.content.Context
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import kotlinx.coroutines.*
import java.util.UUID

object WebRtcService {
    private val lock = Any()
    private var signalingClient: SignalingClient? = null
    private var peerConnectionManager: PeerConnectionManager? = null
    private var serviceScope: CoroutineScope? = null
    private var pollJob: Job? = null

    @JvmStatic
    fun initialize(context: Context, settings: UserSettings) {
        synchronized(lock) {
            shutdownLocked()
            val myId = resolvePeerId(context, settings)
            val client = createSignalingClient(context, settings, myId) ?: return
            signalingClient = client
            peerConnectionManager = PeerConnectionManager(context, client, myId)
            if (client is HttpSignalingClient) {
                startPolling(client)
            }
        }
    }

    @JvmStatic
    fun getPeerConnectionManager(): PeerConnectionManager? = peerConnectionManager

    @JvmStatic
    fun shutdown() {
        synchronized(lock) {
            shutdownLocked()
        }
    }

    private fun shutdownLocked() {
        pollJob?.cancel()
        pollJob = null
        serviceScope?.cancel()
        serviceScope = null
        signalingClient?.shutdown()
        signalingClient = null
        peerConnectionManager?.shutdown()
        peerConnectionManager = null
    }

    private fun startPolling(client: HttpSignalingClient) {
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollJob = serviceScope?.launch {
            while (isActive) {
                client.pollMessages()
                delay(POLL_INTERVAL_SECONDS * 1000)
            }
        }
    }

    private fun createSignalingClient(
        context: Context,
        settings: UserSettings,
        myId: String
    ): SignalingClient? {
        return when (settings.getSignalingMode()) {
            UserSettingsCommon.SIGNALING_MODE_HTTP -> {
                val url = settings.getSignalingServerUrl()
                if (url.isBlank()) {
                    return null
                }
                HttpSignalingClient(url, myId)
            }
            UserSettingsCommon.SIGNALING_MODE_WEBSOCKET -> {
                val url = settings.getSignalingServerUrl()
                if (url.isBlank()) {
                    return null
                }
                WebSocketSignalingClient(url, myId)
            }
            else -> LocalSignalingClient(context, myId)
        }
    }

    private fun resolvePeerId(context: Context, settings: UserSettings): String {
        val identity = IdentityManager.loadIdentity(context)
        if (identity != null && identity.id.isNotBlank()) {
            return identity.id
        }
        val prefs = settings.sharedPreferences
        val cached = prefs.getString(UserSettingsCommon.SIGNALING_PEER_ID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(UserSettingsCommon.SIGNALING_PEER_ID, newId).commit()
        return newId
    }

    private const val POLL_INTERVAL_SECONDS = 2L
}
