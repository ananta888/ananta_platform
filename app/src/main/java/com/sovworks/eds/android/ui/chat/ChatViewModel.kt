package com.sovworks.eds.android.ui.chat

import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.network.DataChannelListener
import com.sovworks.eds.android.network.PeerConnectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class ChatViewModel(
    private val peerConnectionManager: PeerConnectionManager,
    private val targetPeerId: String
) : ViewModel(), DataChannelListener {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    init {
        peerConnectionManager.getMultiplexer().addListener(this)
    }

    override fun onCleared() {
        super.onCleared()
        peerConnectionManager.getMultiplexer().removeListener(this)
    }

    fun sendMessage(text: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "me",
            text = text,
            isMine = true
        )
        _messages.value += msg
        peerConnectionManager.getMultiplexer().sendMessage(targetPeerId, text)
    }

    override fun onMessageReceived(peerId: String, message: String) {
        if (peerId == targetPeerId) {
            receiveMessage(peerId, message)
        }
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        // Handled by File Receiver
    }

    fun receiveMessage(senderId: String, text: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            text = text,
            isMine = false
        )
        _messages.value += msg
    }

    fun sendFile(fileName: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "me",
            text = "Sent file: $fileName",
            isMine = true,
            fileTransfer = FileTransferInfo(fileName, 0.5f, TransferStatus.IN_PROGRESS)
        )
        _messages.value += msg
    }
}
