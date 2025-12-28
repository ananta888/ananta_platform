package com.sovworks.eds.android.network

import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.db.AppDatabase
import com.sovworks.eds.android.db.OfflineMessageEntity
import com.sovworks.eds.android.identity.IdentityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OfflineMessageManager private constructor(private val context: Context) {
    private val gson = Gson()
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var multiplexer: DataChannelMultiplexer? = null

    fun setMultiplexer(mux: DataChannelMultiplexer) {
        this.multiplexer = mux
    }

    fun onMessageReceived(peerId: String, json: String) {
        try {
            val msg = gson.fromJson(json, OfflineMessagingMessage::class.java)
            when (msg.type) {
                "store" -> handleStoreRequest(gson.fromJson(msg.payload, StoreRequest::class.java))
                "retrieve" -> handleRetrieveRequest(peerId, gson.fromJson(msg.payload, RetrieveRequest::class.java))
                "bundle" -> handleBundle(gson.fromJson(msg.payload, OfflineMessageBundle::class.java))
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    private fun handleStoreRequest(request: StoreRequest) {
        scope.launch {
            val entity = OfflineMessageEntity(
                recipientId = request.recipientId,
                senderId = request.senderId,
                encryptedPayload = request.encryptedPayload,
                timestamp = request.timestamp,
                signature = request.signature
            )
            db.offlineMessageDao().insert(entity)
        }
    }

    private fun handleRetrieveRequest(peerId: String, request: RetrieveRequest) {
        scope.launch {
            val messages = db.offlineMessageDao().getMessagesForRecipient(request.recipientId)
            if (messages.isNotEmpty()) {
                val storeRequests = messages.map {
                    StoreRequest(it.recipientId, it.senderId, it.encryptedPayload, it.timestamp, it.signature)
                }
                val bundle = OfflineMessageBundle(storeRequests)
                val msg = OfflineMessagingMessage("bundle", gson.toJson(bundle))
                multiplexer?.sendOfflineMessagingMessage(peerId, gson.toJson(msg))
                
                // Optional: Nachrichten nach dem Senden löschen oder als gesendet markieren
                // db.offlineMessageDao().deleteAllForRecipient(request.recipientId)
            }
        }
    }

    private fun handleBundle(bundle: OfflineMessageBundle) {
        // Diese Nachrichten sind für uns! 
        // Wir müssen sie in den regulären Chat-Workflow einspeisen.
        bundle.messages.forEach { req ->
            // Hier müssten wir den ChatManager informieren
            // Der Einfachheit halber loggen wir es hier nur oder triggern einen Listener
            notifyMessageListeners(req)
        }
    }

    private fun notifyMessageListeners(request: StoreRequest) {
        // Implementierung der Benachrichtigung des UI/ChatManagers
    }

    fun requestMessages(peerId: String) {
        val identity = IdentityManager.loadIdentity(context) ?: return
        val request = RetrieveRequest(identity.getFingerprint(), 0) // Seit wann? 0 für alle
        val msg = OfflineMessagingMessage("retrieve", gson.toJson(request))
        multiplexer?.sendOfflineMessagingMessage(peerId, gson.toJson(msg))
    }

    fun storeMessage(peerId: String, recipientId: String, senderId: String, payload: String) {
        val request = StoreRequest(recipientId, senderId, payload, System.currentTimeMillis())
        val msg = OfflineMessagingMessage("store", gson.toJson(request))
        multiplexer?.sendOfflineMessagingMessage(peerId, gson.toJson(msg))
    }

    fun storeRelayMessage(recipientId: String, senderId: String, payload: String) {
        val trustStore = com.sovworks.eds.android.trust.TrustStore.getInstance(context)
        // Nur speichern, wenn wir dem Empfänger vertrauen (Trust Level > 3)
        val trust = trustStore.getKey(recipientId)
        if (trust != null && trust.getTrustLevel() > 3) {
            handleStoreRequest(StoreRequest(recipientId, senderId, payload, System.currentTimeMillis()))
            com.sovworks.eds.android.Logger.debug("Stored offline message for $recipientId from $senderId")
        }
    }

    companion object {
        @Volatile
        private var instance: OfflineMessageManager? = null

        fun getInstance(context: Context): OfflineMessageManager {
            return instance ?: synchronized(this) {
                instance ?: OfflineMessageManager(context).also { instance = it }
            }
        }
    }
}
