package com.sovworks.eds.android.transfer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.sovworks.eds.android.db.AppDatabase
import com.sovworks.eds.android.db.FileTransferEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

object FileTransferManager {
    private val lock = Any()
    private var context: Context? = null
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val transfers = ConcurrentHashMap<String, FileTransferEntry>()
    private val _state = MutableStateFlow<List<FileTransferEntry>>(emptyList())
    val state: StateFlow<List<FileTransferEntry>> = _state.asStateFlow()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        database = AppDatabase.getDatabase(this.context!!)
        loadTransfers()
    }

    private fun loadTransfers() {
        scope.launch {
            database?.fileTransferDao()?.getAllTransfers()?.collect { entities ->
                entities.forEach { entity ->
                    val entry = FileTransferEntry(
                        id = entity.id,
                        peerId = entity.peerId,
                        fileName = entity.fileName,
                        direction = TransferDirection.valueOf(entity.direction),
                        bytesTransferred = entity.bytesTransferred,
                        totalBytes = entity.totalBytes,
                        status = FileTransferStatus.valueOf(entity.status),
                        updatedAt = entity.updatedAt
                    )
                    // Reset transient status if needed
                    val finalEntry = if (entry.status == FileTransferStatus.IN_PROGRESS) {
                        entry.copy(status = FileTransferStatus.PAUSED)
                    } else {
                        entry
                    }
                    transfers[finalEntry.id] = finalEntry
                }
                publish()
            }
        }
    }

    private fun saveTransfer(entry: FileTransferEntry) {
        scope.launch {
            database?.fileTransferDao()?.insertTransfer(
                FileTransferEntity(
                    id = entry.id,
                    peerId = entry.peerId,
                    fileName = entry.fileName,
                    direction = entry.direction.name,
                    bytesTransferred = entry.bytesTransferred,
                    totalBytes = entry.totalBytes,
                    status = entry.status.name,
                    updatedAt = entry.updatedAt
                )
            )
        }
    }

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
            saveTransfer(entry)
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
            val updated = update(current)
            transfers[transferId] = updated
            saveTransfer(updated)
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
