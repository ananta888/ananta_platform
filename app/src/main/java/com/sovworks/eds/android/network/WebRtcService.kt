package com.sovworks.eds.android.network

import android.content.Context
import android.util.Log
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
    private const val TAG = "WebRtcService"

    @JvmStatic
    fun initialize(context: Context, settings: UserSettings) {
        synchronized(lock) {
            shutdownLocked()
            
            // Start background integrity check
            IntegrityCheckWorker.enqueuePeriodicWork(context)
            com.sovworks.eds.android.identity.KeyRotationWorker.enqueuePeriodicWork(context)

            val myId = resolvePeerId(context, settings)
            val client = createSignalingClient(context, settings, myId) ?: return
            signalingClient = client
            peerConnectionManager = PeerConnectionManager(context, client, myId)
            
            // Initial state: assume foreground for now, but usually follow app state
            if (client is HttpSignalingClient || client is MultiSignalingClient) {
                startPolling(client)
            }
        }
    }

    @JvmStatic
    fun onAppForeground(context: Context) {
        synchronized(lock) {
            SignalingWorker.stopWork(context)
            val client = signalingClient
            if ((client is HttpSignalingClient || client is MultiSignalingClient) && pollJob == null) {
                startPolling(client!!)
            }
        }
    }

    @JvmStatic
    fun onAppBackground(context: Context) {
        synchronized(lock) {
            pollJob?.cancel()
            pollJob = null
            val settings = UserSettings.getSettings(context)
            if (settings.getSignalingMode() == UserSettingsCommon.SIGNALING_MODE_HTTP) {
                SignalingWorker.enqueuePeriodicWork(context)
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

    private fun startPolling(client: SignalingClient) {
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        pollJob = serviceScope?.launch {
            while (isActive) {
                if (client is HttpSignalingClient) {
                    client.pollMessages()
                } else if (client is MultiSignalingClient) {
                    client.pollMessages()
                }
                delay(POLL_INTERVAL_SECONDS * 1000)
            }
        }
    }

    private fun createSignalingClient(
        context: Context,
        settings: UserSettings,
        myId: String
    ): SignalingClient? {
        val mode = settings.getSignalingMode()
        val urls = settings.getSignalingServerUrls()
        Log.d(TAG, "Signaling mode=$mode urls=$urls")

        if (mode == UserSettingsCommon.SIGNALING_MODE_HTTP || mode == UserSettingsCommon.SIGNALING_MODE_WEBSOCKET) {
            if (urls.isEmpty()) return null
            val identity = IdentityManager.loadIdentity(context) ?: return null
            val clients = urls.map { url ->
                if (mode == UserSettingsCommon.SIGNALING_MODE_HTTP) {
                    Log.d(TAG, "Using HTTP signaling: $url")
                    HttpSignalingClient(url, myId)
                } else {
                    Log.d(TAG, "Using WebSocket signaling: $url")
                    WebSocketSignalingClient(url, myId, identity.publicKeyBase64)
                }
            }
            return MultiSignalingClient(clients)
        }

        Log.d(TAG, "Using local signaling")
        return LocalSignalingClient(context, myId)
    }

    @JvmStatic
    fun resolvePeerId(context: Context, settings: UserSettings): String {
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
