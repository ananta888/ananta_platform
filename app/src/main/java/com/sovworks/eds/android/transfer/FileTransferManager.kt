package com.sovworks.eds.android.transfer

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FileTransferManager {
    private val lock = Any()
    private val transfers = ConcurrentHashMap<String, FileTransferEntry>()
    private val _state = MutableStateFlow<List<FileTransferEntry>>(emptyList())
    val state: StateFlow<List<FileTransferEntry>> = _state.asStateFlow()

    fun startIncomingTransfer(peerId: String, fileName: String, totalBytes: Long?): String {
        return startTransfer(peerId, fileName, totalBytes, TransferDirection.INCOMING)
    }

    fun startOutgoingTransfer(peerId: String, fileName: String, totalBytes: Long?): String {
        return startTransfer(peerId, fileName, totalBytes, TransferDirection.OUTGOING)
    }

    fun updateProgress(transferId: String, bytesDelta: Long) {
        updateTransfer(transferId) { entry ->
            entry.copy(
                bytesTransferred = entry.bytesTransferred + bytesDelta,
                status = if (entry.status == FileTransferStatus.PENDING) {
                    FileTransferStatus.IN_PROGRESS
                } else {
                    entry.status
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun markCompleted(transferId: String) {
        updateTransfer(transferId) { entry ->
            entry.copy(status = FileTransferStatus.COMPLETED, updatedAt = System.currentTimeMillis())
        }
    }

    fun markFailed(transferId: String) {
        updateTransfer(transferId) { entry ->
            entry.copy(status = FileTransferStatus.FAILED, updatedAt = System.currentTimeMillis())
        }
    }

    fun pauseTransfer(transferId: String) {
        updateTransfer(transferId) { entry ->
            entry.copy(status = FileTransferStatus.PAUSED, updatedAt = System.currentTimeMillis())
        }
    }

    fun resumeTransfer(transferId: String) {
        updateTransfer(transferId) { entry ->
            entry.copy(status = FileTransferStatus.IN_PROGRESS, updatedAt = System.currentTimeMillis())
        }
    }

    fun isPaused(transferId: String): Boolean {
        return transfers[transferId]?.status == FileTransferStatus.PAUSED
    }

    private fun startTransfer(
        peerId: String,
        fileName: String,
        totalBytes: Long?,
        direction: TransferDirection
    ): String {
        val id = UUID.randomUUID().toString()
        val entry = FileTransferEntry(
            id = id,
            peerId = peerId,
            fileName = fileName,
            direction = direction,
            bytesTransferred = 0,
            totalBytes = totalBytes,
            status = FileTransferStatus.PENDING,
            updatedAt = System.currentTimeMillis()
        )
        synchronized(lock) {
            transfers[id] = entry
            publish()
        }
        return id
    }

    private fun updateTransfer(
        transferId: String,
        update: (FileTransferEntry) -> FileTransferEntry
    ) {
        synchronized(lock) {
            val current = transfers[transferId] ?: return
            transfers[transferId] = update(current)
            publish()
        }
    }

    private fun publish() {
        _state.value = transfers.values.sortedByDescending { it.updatedAt }
    }
}

data class FileTransferEntry(
    val id: String,
    val peerId: String,
    val fileName: String,
    val direction: TransferDirection,
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val status: FileTransferStatus,
    val updatedAt: Long
) {
    val progress: Float? = totalBytes?.takeIf { it > 0 }?.let {
        (bytesTransferred.toDouble() / it.toDouble()).toFloat()
    }
}

enum class TransferDirection {
    INCOMING,
    OUTGOING
}

enum class FileTransferStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    FAILED
}
