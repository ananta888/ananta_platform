package com.sovworks.eds.android.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import com.sovworks.eds.android.network.DataChannelListener
import com.sovworks.eds.android.network.PeerConnectionManager
import com.sovworks.eds.fs.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.*

class ChatViewModel(
    private val peerConnectionManager: PeerConnectionManager,
    private val targetPeerId: String
) : ViewModel(), DataChannelListener {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    val peerId: String get() = targetPeerId
    
    fun getTrustLevel(): Int {
        return com.sovworks.eds.android.trust.TrustStore.getInstance(peerConnectionManager.context)
            .getKey(targetPeerId)?.trustLevel ?: 0
    }

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

    fun sendFile(file: File) {
        val fileName = try {
            file.getName()
        } catch (e: Exception) {
            "file"
        }
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "me",
            text = "Sent file: $fileName",
            isMine = true,
            fileTransfer = FileTransferInfo(fileName, 0.0f, TransferStatus.IN_PROGRESS)
        )
        _messages.value += msg
        peerConnectionManager.sendFile(targetPeerId, file)
    }

    fun sendFileFromUri(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val (fileName, size) = resolveFileInfo(resolver, uri)
        val displayName = fileName ?: "file"
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            senderId = "me",
            text = "Sent file: $displayName",
            isMine = true,
            fileTransfer = FileTransferInfo(displayName, 0.0f, TransferStatus.IN_PROGRESS)
        )
        _messages.value += msg

        val input = resolver.openInputStream(uri) ?: return
        peerConnectionManager.sendFileStream(targetPeerId, displayName, size, input)
    }

    private fun resolveFileInfo(resolver: android.content.ContentResolver, uri: Uri): Pair<String?, Long?> {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                return name to size
            }
        }
        return null to null
    }
}
