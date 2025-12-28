package com.sovworks.eds.android.network

import android.content.Context
import com.sovworks.eds.fs.File
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.android.locations.EncFsLocation
import com.sovworks.eds.android.transfer.FileTransferManager
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class VaultFileReceiver(
    private val context: Context
) : DataChannelListener {
    
    private val activeTransfers = ConcurrentHashMap<String, FileTransferSession>()

    data class FileTransferSession(
        val transferId: String,
        val fileName: String,
        val file: File,
        val outputStream: OutputStream,
        var bytesReceived: Long,
        val totalBytes: Long?
    )

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("FILE_START:")) {
            val payload = message.substringAfter("FILE_START:")
            val parts = payload.split('|', limit = 2)
            val fileName = parts.getOrNull(0) ?: return
            val totalBytes = parts.getOrNull(1)?.toLongOrNull()
            startNewTransfer(peerId, fileName, totalBytes)
        } else if (message == "FILE_END") {
            finishTransfer(peerId)
        }
    }

    private fun startNewTransfer(peerId: String, fileName: String, totalBytes: Long?) {
        val lm = LocationsManager.getLocationsManager(context)
        // Finde die erste offene verschlüsselte Location
        val location = lm.getLoadedLocations(false).filterIsInstance<EncFsLocation>().firstOrNull { it.isOpen }

        if (location != null) {
            try {
                val parentDir = location.getCurrentPath().getDirectory()
                val newFile = parentDir.createFile(fileName)
                val outputStream = newFile.getOutputStream()
                val transferId = FileTransferManager.startIncomingTransfer(peerId, fileName, totalBytes)
                activeTransfers[peerId] = FileTransferSession(
                    transferId = transferId,
                    fileName = fileName,
                    file = newFile,
                    outputStream = outputStream,
                    bytesReceived = 0,
                    totalBytes = totalBytes
                )
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        activeTransfers[peerId]?.let { session ->
            if (FileTransferManager.isPaused(session.transferId)) {
                return
            }
            try {
                session.outputStream.write(data)
                session.bytesReceived += data.size
                FileTransferManager.updateProgress(session.transferId, data.size.toLong())
            } catch (e: Exception) {
                FileTransferManager.markFailed(session.transferId)
            }
        }
    }

    private fun finishTransfer(peerId: String) {
        activeTransfers.remove(peerId)?.let { session ->
            try {
                session.outputStream.close()
                FileTransferManager.markCompleted(session.transferId)
            } catch (e: Exception) {
                FileTransferManager.markFailed(session.transferId)
            }
        }
    }
}
