package com.sovworks.eds.android.ui.messenger

import com.sovworks.eds.android.network.DataChannelListener
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.db.AppDatabase
import com.sovworks.eds.android.db.ChatMessageEntity
import com.sovworks.eds.android.db.ChatGroupEntity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.sovworks.eds.android.security.SecurityUtils
import javax.crypto.SecretKey

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
    val memberIds: Set<String>,
    val groupKey: SecretKey? = null
)

object MessengerRepository : DataChannelListener {
    private val _messages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ChatMessage>>> = _messages.asStateFlow()

    private val _groups = MutableStateFlow<Map<String, ChatGroup>>(emptyMap())
    val groups: StateFlow<Map<String, ChatGroup>> = _groups.asStateFlow()

    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize(context: Context) {
        WebRtcService.getPeerConnectionManager()?.getMultiplexer()?.addListener(this)
        
        database = AppDatabase.getDatabase(context)
        
        // Load messages from database
        scope.launch {
            database?.chatDao()?.getAllMessages()?.collect { entities ->
                val mappedMessages = entities.groupBy { it.chatId }.mapValues { entry ->
                    entry.value.map { entity ->
                        ChatMessage(
                            senderId = entity.senderId,
                            text = entity.text,
                            timestamp = entity.timestamp,
                            isMe = entity.isMe,
                            groupId = entity.groupId
                        )
                    }
                }
                _messages.value = mappedMessages
            }
        }

        // Load groups from database
        scope.launch {
            database?.groupDao()?.getAllGroups()?.collect { entities ->
                val mappedGroups = entities.associate { entity ->
                    val groupKey = entity.groupKeyBase64?.let { SecurityUtils.stringToKey(it) }
                    entity.id to ChatGroup(
                        id = entity.id,
                        name = entity.name,
                        memberIds = entity.memberIds.split(",").filter { it.isNotEmpty() }.toSet(),
                        groupKey = groupKey
                    )
                }
                _groups.value = mappedGroups
            }
        }
    }

    fun createGroup(name: String, memberIds: Set<String>): String {
        val groupId = "group_${System.currentTimeMillis()}"
        val groupKey = SecurityUtils.generateGroupKey()
        val group = ChatGroup(groupId, name, memberIds, groupKey)
        _groups.update { it + (groupId to group) }

        // Save to database
        scope.launch {
            database?.groupDao()?.insertGroup(ChatGroupEntity(
                id = groupId,
                name = name,
                memberIds = memberIds.joinToString(","),
                groupKeyBase64 = SecurityUtils.keyToString(groupKey)
            ))
        }

        // Send invite to all members
        val invitePayload = "GROUP_INVITE:$groupId:$name:${memberIds.joinToString(",")}:${SecurityUtils.keyToString(groupKey)}"
        val manager = WebRtcService.getPeerConnectionManager()
        memberIds.forEach { memberId ->
            manager?.getMultiplexer()?.sendMessage(memberId, invitePayload)
        }

        return groupId
    }

    private fun handleGroupInvite(peerId: String, payload: String) {
        // Format: GROUP_INVITE:groupId:name:memberIds:groupKey
        val parts = payload.removePrefix("GROUP_INVITE:").split(":", limit = 4)
        if (parts.size < 4) return
        val groupId = parts[0]
        val name = parts[1]
        val memberIds = parts[2].split(",").toSet()
        val groupKey = SecurityUtils.stringToKey(parts[3])

        val group = ChatGroup(groupId, name, memberIds, groupKey)
        _groups.update { it + (groupId to group) }

        // Save to database
        scope.launch {
            database?.groupDao()?.insertGroup(ChatGroupEntity(
                id = groupId,
                name = name,
                memberIds = parts[2],
                groupKeyBase64 = parts[3]
            ))
        }
    }

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("TRUST_PACKAGE:")) return 

        if (message.startsWith("GROUP_INVITE:")) {
            handleGroupInvite(peerId, message)
            return
        }

        if (message.startsWith("GROUP_MSG:")) {
            handleGroupMessage(peerId, message)
            return
        }

        if (message.startsWith("GROUP_MSG_E2EE:")) {
            handleGroupMessageE2EE(peerId, message)
            return
        }

        val chatMessage = ChatMessage(
            senderId = peerId,
            text = message,
            isMe = false
        )
        addMessage(peerId, chatMessage)
    }

    private fun handleGroupMessage(peerId: String, payload: String) {
        // Format: GROUP_MSG:groupId:actualMessage
        val parts = payload.removePrefix("GROUP_MSG:").split(":", limit = 2)
        if (parts.size < 2) return
        val groupId = parts[0]
        val text = parts[1]

        val chatMessage = ChatMessage(
            senderId = peerId,
            text = text,
            isMe = false,
            groupId = groupId
        )
        addMessage(groupId, chatMessage)
    }

    private fun handleGroupMessageE2EE(peerId: String, payload: String) {
        // Format: GROUP_MSG_E2EE:groupId:iv:encryptedText
        val parts = payload.removePrefix("GROUP_MSG_E2EE:").split(":", limit = 3)
        if (parts.size < 3) return
        val groupId = parts[0]
        val iv = parts[1]
        val encryptedText = parts[2]

        val group = _groups.value[groupId] ?: return
        val groupKey = group.groupKey ?: return // In real scenario, we might need to fetch it

        try {
            val decryptedText = SecurityUtils.decrypt(encryptedText, iv, groupKey)
            val chatMessage = ChatMessage(
                senderId = peerId,
                text = decryptedText,
                isMe = false,
                groupId = groupId
            )
            addMessage(groupId, chatMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        // Not used for text chat
    }

    fun sendMessage(peerId: String, text: String) {
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        manager.getMultiplexer().sendMessage(peerId, text)
        
        val chatMessage = ChatMessage(
            senderId = "me", 
            text = text,
            isMe = true
        )
        addMessage(peerId, chatMessage)
    }

    fun sendGroupMessage(groupId: String, text: String) {
        val group = _groups.value[groupId] ?: return
        val manager = WebRtcService.getPeerConnectionManager() ?: return
        
        val payload = if (group.groupKey != null) {
            val encrypted = SecurityUtils.encrypt(text.toByteArray(Charsets.UTF_8), group.groupKey)
            "GROUP_MSG_E2EE:$groupId:${encrypted.iv}:${encrypted.data}"
        } else {
            "GROUP_MSG:$groupId:$text"
        }

        group.memberIds.forEach { memberId ->
            manager.getMultiplexer().sendMessage(memberId, payload)
        }

        val chatMessage = ChatMessage(
            senderId = "me",
            text = text,
            isMe = true,
            groupId = groupId
        )
        addMessage(groupId, chatMessage)
    }

    private fun addMessage(chatId: String, message: ChatMessage) {
        _messages.update { current ->
            val chatMessages = current[chatId] ?: emptyList()
            current + (chatId to (chatMessages + message))
        }

        // Save to database
        scope.launch {
            database?.chatDao()?.insertMessage(
                ChatMessageEntity(
                    chatId = chatId,
                    senderId = message.senderId,
                    text = message.text,
                    timestamp = message.timestamp,
                    isMe = message.isMe,
                    groupId = message.groupId
                )
            )
        }
    }
}
