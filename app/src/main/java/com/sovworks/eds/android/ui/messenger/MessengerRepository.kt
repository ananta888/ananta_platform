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
    val isMe: Boolean,
    val groupId: String? = null
)

data class ChatGroup(
    val id: String,
    val name: String,
    val memberIds: Set<String>
)

object MessengerRepository : DataChannelListener {
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    private val _groups = MutableStateFlow<Map<String, ChatGroup>>(emptyMap())
    val groups: StateFlow<Map<String, ChatGroup>> = _groups.asStateFlow()

    fun initialize() {
        WebRtcService.getPeerConnectionManager()?.getMultiplexer()?.addListener(this)
    }

    fun createGroup(name: String, memberIds: Set<String>): String {
        val groupId = "group_${System.currentTimeMillis()}"
        val group = ChatGroup(groupId, name, memberIds)
        _groups.update { it + (groupId to group) }
        return groupId
    }

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("TRUST_PACKAGE:")) return 

        if (message.startsWith("GROUP_MSG:")) {
            handleGroupMessage(peerId, message)
            return
        }

        addMessage(peerId, ChatMessage(
            senderId = peerId,
            text = message,
            isMe = false
        ))
    }

    private fun handleGroupMessage(peerId: String, payload: String) {
        // Format: GROUP_MSG:groupId:actualMessage
        val parts = payload.removePrefix("GROUP_MSG:").split(":", limit = 2)
        if (parts.size < 2) return
        val groupId = parts[0]
        val text = parts[1]

        addMessage(groupId, ChatMessage(
            senderId = peerId,
            text = text,
            isMe = false,
            groupId = groupId
        ))
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        // Not used for text chat
    }

    fun sendMessage(peerId: String, text: String) {
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        manager.getMultiplexer().sendMessage(peerId, text)
        
        addMessage(peerId, ChatMessage(
            senderId = "me", 
            text = text,
            isMe = true
        ))
    }

    fun sendGroupMessage(groupId: String, text: String) {
        val group = _groups.value[groupId] ?: return
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        
        val payload = "GROUP_MSG:$groupId:$text"
        group.memberIds.forEach { memberId ->
            manager.getMultiplexer().sendMessage(memberId, payload)
        }

        addMessage(groupId, ChatMessage(
            senderId = "me",
            text = text,
            isMe = true,
            groupId = groupId
        ))
    }

    private fun addMessage(chatId: String, message: ChatMessage) {
        _messages.update { current ->
            val chatMessages = current[chatId] ?: emptyList()
            current + (chatId to (chatMessages + message))
        }
    }
}
