package com.sovworks.eds.android.ui.messenger

import com.sovworks.eds.android.network.DataChannelListener
import com.sovworks.eds.android.network.WebRtcService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ChatMessage(
    val senderId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean
)

object MessengerRepository : DataChannelListener {
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    init {
        // Register as listener when initialized
        // Note: In a real app, this might be handled by a more robust DI or lifecycle management
    }

    fun initialize() {
        WebRtcService.getPeerConnectionManager()?.getMultiplexer()?.addListener(this)
    }

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("TRUST_PACKAGE:")) return // Handle elsewhere or ignore here

        addMessage(peerId, ChatMessage(
            senderId = peerId,
            text = message,
            isMe = false
        ))
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        // Not used for text chat
    }

    fun sendMessage(peerId: String, text: String) {
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        manager.getMultiplexer().sendMessage(peerId, text)
        
        addMessage(peerId, ChatMessage(
            senderId = "me", // Should ideally be myId
            text = text,
            isMe = true
        ))
    }

    private fun addMessage(peerId: String, message: ChatMessage) {
        _messages.update { current ->
            val peerMessages = current[peerId] ?: emptyList()
            current + (peerId to (peerMessages + message))
        }
    }
}
