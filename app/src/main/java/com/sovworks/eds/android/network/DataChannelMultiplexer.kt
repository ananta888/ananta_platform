package com.sovworks.eds.android.network

import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

interface DataChannelListener {
    fun onMessageReceived(peerId: String, message: String)
    fun onBinaryReceived(peerId: String, data: ByteArray)
}

class DataChannelMultiplexer(
    private val peerConnectionManager: PeerConnectionManager
) {
    private val chatChannels = ConcurrentHashMap<String, DataChannel>()
    private val fileChannels = ConcurrentHashMap<String, DataChannel>()
    private val listeners = mutableListOf<DataChannelListener>()

    fun addListener(listener: DataChannelListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DataChannelListener) {
        listeners.remove(listener)
    }

    fun onDataChannelCreated(peerId: String, dataChannel: DataChannel) {
        when (dataChannel.label()) {
            "chat" -> setupChatChannel(peerId, dataChannel)
            "file" -> setupFileChannel(peerId, dataChannel)
        }
    }

    private fun setupChatChannel(peerId: String, channel: DataChannel) {
        chatChannels[peerId] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (!buffer.binary) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    val message = String(bytes)
                    listeners.forEach { it.onMessageReceived(peerId, message) }
                }
            }
        })
    }

    private fun setupFileChannel(peerId: String, channel: DataChannel) {
        fileChannels[peerId] = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                // Flow control logic could go here
            }
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary) {
                    val bytes = ByteArray(buffer.data.remaining())
                    buffer.data.get(bytes)
                    listeners.forEach { it.onBinaryReceived(peerId, bytes) }
                }
            }
        })
    }

    fun sendMessage(peerId: String, message: String) {
        val channel = chatChannels[peerId] ?: return
        val buffer = ByteBuffer.wrap(message.toByteArray())
        channel.send(DataChannel.Buffer(buffer, false))
    }

    fun sendFileData(peerId: String, data: ByteArray) {
        val channel = fileChannels[peerId] ?: return
        
        // Simple Flow Control: Wait if buffer is too full
        if (channel.bufferedAmount() > 1024 * 1024) { // 1MB threshold
             // In a real app, we would use a callback or coroutine to wait
             return
        }

        val buffer = ByteBuffer.wrap(data)
        channel.send(DataChannel.Buffer(buffer, true))
    }
}
