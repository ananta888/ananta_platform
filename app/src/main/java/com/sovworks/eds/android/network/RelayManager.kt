package com.sovworks.eds.android.network

import android.content.Context
import com.sovworks.eds.android.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.nio.ByteBuffer
import java.util.UUID

class RelayManager private constructor(private val context: Context) {
    private val activeRelays = ConcurrentHashMap<String, RelaySession>()
    private val relayListeners = ConcurrentHashMap<String, (String) -> Unit>() // sessionId -> callback
    private val scope = CoroutineScope(Dispatchers.IO)

    data class RelaySession(
        val sessionId: String,
        val peerA: String,
        val peerC: String
    )

    fun handleRelayRequest(fromPeerId: String, payload: String) {
        val parts = payload.split(":")
        if (parts.size < 3) return
        val targetPeerId = parts[1]
        val sessionId = parts[2]

        Logger.debug("RelayManager: Received relay request from $fromPeerId to $targetPeerId (Session: $sessionId)")

        val session = RelaySession(sessionId, fromPeerId, targetPeerId)
        activeRelays[sessionId] = session

        SearchManager.getInstance(context).sendRelayReady(fromPeerId, sessionId)
    }

    fun onRelayReady(fromPeerId: String, sessionId: String) {
        Logger.debug("RelayManager: Relay ready from $fromPeerId for session $sessionId")
        relayListeners[sessionId]?.invoke(fromPeerId)
    }

    fun registerRelayListener(sessionId: String, callback: (String) -> Unit) {
        relayListeners[sessionId] = callback
    }

    fun onRelayControlReceived(fromPeerId: String, message: String) {
        if (message.startsWith("RELAY_CTRL:")) {
            val parts = message.split(":", limit = 3)
            if (parts.size < 3) return
            val sessionId = parts[1]
            val ctrlMsg = parts[2]
            
            val session = activeRelays[sessionId]
            if (session != null) {
                val targetPeerId = if (fromPeerId == session.peerA) session.peerC else session.peerA
                val multiplexer = WebRtcService.getPeerConnectionManager()?.getMultiplexer()
                if (multiplexer?.isConnected(targetPeerId) == true) {
                    multiplexer.sendRelayControl(targetPeerId, message)
                } else {
                    // Offline zwischenspeichern
                    OfflineMessageManager.getInstance(context).storeRelayMessage(targetPeerId, fromPeerId, message)
                }
            } else {
                // Wir sind Endpunkt
                relayControlListeners[sessionId]?.invoke(fromPeerId, ctrlMsg)
            }
        }
    }

    private val relayControlListeners = ConcurrentHashMap<String, (String, String) -> Unit>()
    
    fun registerRelayControlListener(sessionId: String, callback: (String, String) -> Unit) {
        relayControlListeners[sessionId] = callback
    }

    fun onRelayDataReceived(fromPeerId: String, data: ByteArray) {
        if (data.size < 16) return
        val bb = ByteBuffer.wrap(data)
        val mostSigBits = bb.long
        val leastSigBits = bb.long
        val sessionId = UUID(mostSigBits, leastSigBits).toString()

        val session = activeRelays[sessionId]
        if (session != null) {
            val targetPeerId = if (fromPeerId == session.peerA) session.peerC else session.peerA
            scope.launch {
                WebRtcService.getPeerConnectionManager()?.getMultiplexer()?.sendRelayData(targetPeerId, data)
            }
        } else {
            // Wir sind Endpunkt. Daten auspacken und an Listener verteilen
            val actualData = data.sliceArray(16 until data.size)
            relayDataListeners[sessionId]?.invoke(fromPeerId, actualData)
        }
    }

    private val relayDataListeners = ConcurrentHashMap<String, (String, ByteArray) -> Unit>()
    
    fun registerRelayDataListener(sessionId: String, callback: (String, ByteArray) -> Unit) {
        relayDataListeners[sessionId] = callback
    }

    fun createRelayDataPacket(sessionId: String, payload: ByteArray): ByteArray {
        val uuid = UUID.fromString(sessionId)
        val bb = ByteBuffer.allocate(16 + payload.size)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        bb.put(payload)
        return bb.array()
    }

    companion object {
        @Volatile
        private var instance: RelayManager? = null

        fun getInstance(context: Context): RelayManager {
            return instance ?: synchronized(this) {
                instance ?: RelayManager(context).also { instance = it }
            }
        }
    }
}
