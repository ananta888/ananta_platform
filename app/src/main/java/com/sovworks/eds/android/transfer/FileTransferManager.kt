package com.sovworks.eds.android.transfer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FileTransferManager {
    private val lock = Any()
    private var context: Context? = null
    private val gson = Gson()
    private const val PREFS_NAME = "file_transfers"
    private const val KEY_TRANSFERS = "transfers_list"

    private val transfers = ConcurrentHashMap<String, FileTransferEntry>()
    private val _state = MutableStateFlow<List<FileTransferEntry>>(emptyList())
    val state: StateFlow<List<FileTransferEntry>> = _state.asStateFlow()

    fun initialize(context: Context) {
        this.context = context.applicationContext
        loadTransfers()
    }

    private fun loadTransfers() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val json = prefs.getString(KEY_TRANSFERS, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<FileTransferEntry>>() {}.type
                val list: List<FileTransferEntry> = gson.fromJson(json, type)
                list.forEach { 
                    // Reset transient status if needed
                    val entry = if (it.status == FileTransferStatus.IN_PROGRESS) {
                        it.copy(status = FileTransferStatus.PAUSED)
                    } else {
                        it
                    }
                    transfers[entry.id] = entry 
                }
                publish()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun saveTransfers() {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return
        val json = gson.toJson(transfers.values.toList())
        prefs.edit().putString(KEY_TRANSFERS, json).apply()
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
            saveTransfers()
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
            saveTransfers()
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
