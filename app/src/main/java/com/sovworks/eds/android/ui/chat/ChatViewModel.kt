package com.sovworks.eds.android.ui.chat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun sendMessage(text: String) {
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "me",
            text = text,
            isMine = true
        )
        _messages.value += msg
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
