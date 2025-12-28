package com.sovworks.eds.android.network

import org.webrtc.DataChannel
import java.util.concurrent.ConcurrentHashMap

interface DataChannelListener {
    fun onMessageReceived(peerId: String, message: String)
    fun onBinaryReceived(peerId: String, data: ByteArray)
}

class DataChannelMultiplexer(
    private val peerConnectionManager: PeerConnectionManager
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
        chatChannels[peerId] = SecureDataChannel(
            channel = channel,
            isInitiator = isInitiator,
            onTextMessage = { message ->
                listeners.forEach { it.onMessageReceived(peerId, message) }
            },
            onBinaryMessage = {}
        )
    }

    private fun setupFileChannel(peerId: String, channel: DataChannel, isInitiator: Boolean) {
        fileChannels[peerId] = SecureDataChannel(
            channel = channel,
            isInitiator = isInitiator,
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

    fun sendFileData(peerId: String, data: ByteArray): Boolean {
        val channel = fileChannels[peerId] ?: return false

        // Simple Flow Control: Wait if buffer is too full
        if (channel.bufferedAmount() > 1024 * 1024) { // 1MB threshold
            // In a real app, we would use a callback or coroutine to wait
            return false
        }
        channel.sendBinary(data)
        return true
    }
}
