package com.sovworks.eds.android.network

import kotlinx.coroutines.flow.first
import android.content.Context
import android.util.Base64
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.trust.TrustStore
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.webrtc.DataChannel
import java.util.concurrent.ConcurrentHashMap

interface DataChannelListener {
    fun onMessageReceived(peerId: String, message: String)
    fun onBinaryReceived(peerId: String, data: ByteArray)
}

class DataChannelMultiplexer(
    private val context: Context
) {
    private val chatChannels = ConcurrentHashMap<String, SecureDataChannel>()
    private val fileChannels = ConcurrentHashMap<String, SecureDataChannel>()
    private val listeners = mutableListOf<DataChannelListener>()

    fun addListener(listener: DataChannelListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DataChannelListener) {
        listeners.remove(listener)
    }

    fun onDataChannelCreated(peerId: String, dataChannel: DataChannel, isInitiator: Boolean) {
        when (dataChannel.label()) {
            "chat" -> setupChatChannel(peerId, dataChannel, isInitiator)
            "file" -> setupFileChannel(peerId, dataChannel, isInitiator)
        }
    }

    private fun setupChatChannel(peerId: String, channel: DataChannel, isInitiator: Boolean) {
        val localIdentity = IdentityManager.loadIdentity(context) ?: return
        val localPrivateKey = IdentityManager.getDecryptedPrivateKey(localIdentity) ?: return
        
        val trustStore = TrustStore.getInstance(context)
        val trustedKey = trustStore.getKey(peerId) ?: return
        val remotePublicKey = Ed25519PublicKeyParameters(Base64.decode(trustedKey.getPublicKey(), Base64.NO_WRAP), 0)

        chatChannels[peerId] = SecureDataChannel(
            channel = channel,
            isInitiator = isInitiator,
            localIdentityKey = localPrivateKey,
            remoteIdentityKey = remotePublicKey,
            onTextMessage = { message ->
                listeners.forEach { it.onMessageReceived(peerId, message) }
            },
            onBinaryMessage = {}
        )
    }

    private fun setupFileChannel(peerId: String, channel: DataChannel, isInitiator: Boolean) {
        val localIdentity = IdentityManager.loadIdentity(context) ?: return
        val localPrivateKey = IdentityManager.getDecryptedPrivateKey(localIdentity) ?: return
        
        val trustStore = TrustStore.getInstance(context)
        val trustedKey = trustStore.getKey(peerId) ?: return
        val remotePublicKey = Ed25519PublicKeyParameters(Base64.decode(trustedKey.getPublicKey(), Base64.NO_WRAP), 0)

        fileChannels[peerId] = SecureDataChannel(
            channel = channel,
            isInitiator = isInitiator,
            localIdentityKey = localPrivateKey,
            remoteIdentityKey = remotePublicKey,
            onTextMessage = { message ->
                listeners.forEach { it.onMessageReceived(peerId, message) }
            },
            onBinaryMessage = { data ->
                listeners.forEach { it.onBinaryReceived(peerId, data) }
            }
        )
    }

    fun sendMessage(peerId: String, message: String) {
        val channel = chatChannels[peerId] ?: return
        channel.sendText(message)
    }

    suspend fun sendFileData(peerId: String, data: ByteArray): Boolean {
        val channel = fileChannels[peerId] ?: return false

        // Flow Control: Wait if buffer is too full
        if (channel.bufferedAmount() > BUFFER_THRESHOLD) {
            channel.bufferedAmountFlow.first { it <= BUFFER_THRESHOLD }
        }
        channel.sendBinary(data)
        return true
    }

    companion object {
        private const val BUFFER_THRESHOLD = 1024 * 1024L // 1MB
    }
}
