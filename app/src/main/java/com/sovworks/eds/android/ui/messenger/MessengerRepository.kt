package com.sovworks.eds.android.ui.messenger

import com.sovworks.eds.android.network.DataChannelListener
import com.sovworks.eds.android.network.OfflineMessageListener
import com.sovworks.eds.android.network.OfflineMessageManager
import com.sovworks.eds.android.network.PfsSession
import com.sovworks.eds.android.network.SignalingRelayBus
import com.sovworks.eds.android.network.StoreRequest
import com.sovworks.eds.android.network.WebRtcService
import com.sovworks.eds.android.db.AppDatabase
import com.sovworks.eds.android.db.ChatMessageEntity
import com.sovworks.eds.android.db.ChatGroupEntity
import android.content.Context
import com.google.gson.Gson
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustNetworkManager
import com.sovworks.eds.android.trust.TrustNetworkPackage
import com.sovworks.eds.android.trust.TrustStore
import com.sovworks.eds.android.trust.TrustedKey
import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.sovworks.eds.android.security.SecurityUtils
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
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

    private var context: Context? = null
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val relaySessions = SignalingRelaySessionManager()
    private val offlineMessageListener = object : OfflineMessageListener {
        override fun onOfflineMessageReceived(request: StoreRequest) {
            processMessage(request.senderId, request.encryptedPayload, request.timestamp)
        }
    }
    private val relayListener: (String, String) -> Unit = { peerId, payload ->
        relaySessions.handleIncoming(peerId, payload)
    }

    fun initialize(context: Context) {
        this.context = context.applicationContext
        WebRtcService.getPeerConnectionManager()?.getMultiplexer()?.addListener(this)
        OfflineMessageManager.getInstance(context).addListener(offlineMessageListener)
        SignalingRelayBus.addListener(relayListener)
        
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
        memberIds.forEach { memberId ->
            sendMessageWithFallback(memberId, invitePayload)
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
        processMessage(peerId, message, System.currentTimeMillis())
    }

    private fun handleGroupMessage(peerId: String, payload: String, timestamp: Long) {
        // Format: GROUP_MSG:groupId:actualMessage
        val parts = payload.removePrefix("GROUP_MSG:").split(":", limit = 2)
        if (parts.size < 2) return
        val groupId = parts[0]
        val text = parts[1]

        val chatMessage = ChatMessage(
            senderId = peerId,
            text = text,
            timestamp = timestamp,
            isMe = false,
            groupId = groupId
        )
        addMessage(groupId, chatMessage)
    }

    private fun handleGroupMessageE2EE(peerId: String, payload: String, timestamp: Long) {
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
            
            if (decryptedText.startsWith("TRUST_SYNC:")) {
                val json = decryptedText.substringAfter("TRUST_SYNC:")
                val networkPackage = Gson().fromJson(json, TrustNetworkPackage::class.java)
                TrustNetworkManager.verifyAndImportNetwork(context!!, networkPackage)
                return
            }

            val chatMessage = ChatMessage(
                senderId = peerId,
                text = decryptedText,
                timestamp = timestamp,
                isMe = false,
                groupId = groupId
            )
            addMessage(groupId, chatMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processMessage(peerId: String, message: String, timestamp: Long) {
        if (message.startsWith("TRUST_PACKAGE:")) return

        if (message.startsWith("GROUP_INVITE:")) {
            handleGroupInvite(peerId, message)
            return
        }

        if (message.startsWith("GROUP_MSG:")) {
            handleGroupMessage(peerId, message, timestamp)
            return
        }

        if (message.startsWith("GROUP_MSG_E2EE:")) {
            handleGroupMessageE2EE(peerId, message, timestamp)
            return
        }

        val chatMessage = ChatMessage(
            senderId = peerId,
            text = message,
            timestamp = timestamp,
            isMe = false
        )
        addMessage(peerId, chatMessage)
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        // Not used for text chat
    }

    fun sendMessage(peerId: String, text: String) {
        sendMessageWithFallback(peerId, text)

        val chatMessage = ChatMessage(
            senderId = "me",
            text = text,
            isMe = true
        )
        addMessage(peerId, chatMessage)
    }

    fun sendMessageToPeers(peerIds: Set<String>, text: String) {
        if (peerIds.isEmpty()) return
        peerIds.forEach { peerId ->
            sendMessage(peerId, text)
        }
    }

    fun sendGroupMessage(groupId: String, text: String) {
        val group = _groups.value[groupId] ?: return

        val payload = if (group.groupKey != null) {
            val encrypted = SecurityUtils.encrypt(text.toByteArray(Charsets.UTF_8), group.groupKey)
            "GROUP_MSG_E2EE:$groupId:${encrypted.iv}:${encrypted.data}"
        } else {
            "GROUP_MSG:$groupId:$text"
        }

        group.memberIds.forEach { memberId ->
            sendMessageWithFallback(memberId, payload)
        }

        val chatMessage = ChatMessage(
            senderId = "me",
            text = text,
            isMe = true,
            groupId = groupId
        )
        addMessage(groupId, chatMessage)
    }

    fun deleteGroup(groupId: String) {
        _groups.update { it - groupId }
        scope.launch {
            database?.groupDao()?.deleteGroupById(groupId)
        }
    }

    fun shareTrustWithGroup(groupId: String) {
        val currentContext = context ?: return
        val identity = IdentityManager.loadIdentity(currentContext) ?: return
        val trustStore = TrustStore.getInstance(currentContext)
        val trustedKeys = trustStore.getAllKeys().values.filter { it.status == TrustedKey.TrustStatus.TRUSTED }
        
        val networkPackage = TrustNetworkManager.exportTrustNetwork(identity, trustedKeys) ?: return
        val json = Gson().toJson(networkPackage)
        sendGroupMessage(groupId, "TRUST_SYNC:$json")
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

    private fun sendMessageWithFallback(peerId: String, text: String) {
        val manager = WebRtcService.getPeerConnectionManager()
        val multiplexer = manager?.getMultiplexer()
        if (multiplexer != null && multiplexer.isConnected(peerId)) {
            multiplexer.sendMessage(peerId, text)
            return
        }
        relaySessions.send(peerId, text)
    }

    private class SignalingRelaySessionManager {
        private val sessions = ConcurrentHashMap<String, RelaySession>()

        fun send(peerId: String, text: String): Boolean {
            val session = getOrCreate(peerId) ?: return false
            session.send(text)
            return true
        }

        fun handleIncoming(peerId: String, payload: String) {
            val session = getOrCreate(peerId) ?: return
            val plaintext = session.handleIncoming(payload) ?: return
            processMessage(peerId, plaintext, System.currentTimeMillis())
        }

        private fun getOrCreate(peerId: String): RelaySession? {
            val existing = sessions[peerId]
            if (existing != null) return existing
            val created = createSession(peerId) ?: return null
            val previous = sessions.putIfAbsent(peerId, created)
            return previous ?: created
        }

        private fun createSession(peerId: String): RelaySession? {
            val currentContext = context ?: return null
            val identity = IdentityManager.loadIdentity(currentContext) ?: return null
            val localPrivateKey = IdentityManager.getDecryptedPrivateKey(identity) ?: return null
            val trustStore = TrustStore.getInstance(currentContext)
            val trustedKey = trustStore.getKey(peerId) ?: return null
            val remotePublicKey = Ed25519PublicKeyParameters(
                Base64.decode(trustedKey.publicKey, Base64.NO_WRAP),
                0
            )
            val isInitiator = identity.publicKeyBase64 < peerId
            val session = PfsSession(isInitiator, localPrivateKey, remotePublicKey)
            return RelaySession(peerId, session)
        }
    }

    private class RelaySession(
        private val peerId: String,
        private val pfsSession: PfsSession
    ) {
        private val pending = ArrayDeque<String>()

        fun send(text: String) {
            if (!pfsSession.isReady()) {
                pending.addLast(text)
                ensureHandshake()
                return
            }
            val encrypted = pfsSession.encryptText(text) ?: return
            sendPayload("${PfsSession.PFS_MESSAGE_PREFIX}$encrypted")
        }

        fun handleIncoming(payload: String): String? {
            if (pfsSession.handleControl(payload, ::sendPayload)) {
                if (pfsSession.isReady()) {
                    flushPending()
                }
                return null
            }
            if (!payload.startsWith(PfsSession.PFS_MESSAGE_PREFIX)) {
                return null
            }
            val encrypted = payload.removePrefix(PfsSession.PFS_MESSAGE_PREFIX)
            return pfsSession.decryptText(encrypted)
        }

        private fun ensureHandshake() {
            pfsSession.ensureHello(::sendPayload)
        }

        private fun flushPending() {
            while (pending.isNotEmpty()) {
                send(pending.removeFirst())
            }
        }

        private fun sendPayload(payload: String) {
            WebRtcService.sendRelayPayload(listOf(peerId), payload)
        }
    }
}
