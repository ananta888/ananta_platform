package com.sovworks.eds.android.network

import com.sovworks.eds.android.transfer.FileTransferManager
import com.sovworks.eds.fs.File
import java.io.InputStream
import java.util.concurrent.Executors

class VaultFileSender(
    private val multiplexer: DataChannelMultiplexer
) {
    private val executor = Executors.newCachedThreadPool()

    fun sendFile(peerId: String, file: File) {
        executor.execute {
            val fileName = try {
                file.getName()
            } catch (e: Exception) {
                "file"
            }
            val totalBytes = try {
                file.getSize()
            } catch (e: Exception) {
                null
            }
            try {
                file.getInputStream().use { input ->
                    sendStream(peerId, fileName, totalBytes, input)
                }
            } catch (e: Exception) {
                val transferId = FileTransferManager.startOutgoingTransfer(peerId, fileName, totalBytes)
                FileTransferManager.markFailed(transferId)
            }
        }
    }

    fun sendStream(peerId: String, fileName: String, totalBytes: Long?, input: InputStream) {
        val transferId = FileTransferManager.startOutgoingTransfer(peerId, fileName, totalBytes)
        try {
            sendStart(peerId, fileName, totalBytes)
            streamFile(peerId, transferId, input)
            multiplexer.sendMessage(peerId, FILE_END)
            FileTransferManager.markCompleted(transferId)
        } catch (e: Exception) {
            FileTransferManager.markFailed(transferId)
        }
    }

    private fun sendStart(peerId: String, fileName: String, totalBytes: Long?) {
        val payload = if (totalBytes != null && totalBytes >= 0) {
            "$FILE_START_PREFIX$fileName|$totalBytes"
        } else {
            "$FILE_START_PREFIX$fileName"
        }
        multiplexer.sendMessage(peerId, payload)
    }

    private fun streamFile(peerId: String, transferId: String, input: InputStream) {
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            var read = input.read(buffer)
            if (read <= 0) {
                break
            }
            while (FileTransferManager.isPaused(transferId)) {
                Thread.sleep(PAUSE_POLL_MS)
            }
            var sent = false
            val payload = if (read == buffer.size) buffer else buffer.copyOf(read)
            while (!sent) {
                sent = multiplexer.sendFileData(peerId, payload)
                if (!sent) {
                    Thread.sleep(SEND_RETRY_MS)
                }
            }
            FileTransferManager.updateProgress(transferId, read.toLong())
        }
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024
        private const val SEND_RETRY_MS = 50L
        private const val PAUSE_POLL_MS = 200L
        private const val FILE_START_PREFIX = "FILE_START:"
        private const val FILE_END = "FILE_END"
    }
}
