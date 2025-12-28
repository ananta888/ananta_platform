package com.sovworks.eds.android.ui.chat

data class ChatMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean,
    val fileTransfer: FileTransferInfo? = null
)

data class FileTransferInfo(
    val fileName: String,
    val progress: Float, // 0.0 bis 1.0
    val status: TransferStatus
)

enum class TransferStatus {
    PENDING, IN_PROGRESS, COMPLETED, FAILED
}
