package com.sovworks.eds.android.network

import com.sovworks.eds.android.transfer.FileTransferManager
import com.sovworks.eds.fs.File
import kotlinx.coroutines.*
import java.io.InputStream
import java.security.MessageDigest

class VaultFileSender(
    private val multiplexer: DataChannelMultiplexer
) {
    private val senderScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sendFile(peerId: String, file: File) {
        senderScope.launch {
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

    suspend fun sendStream(peerId: String, fileName: String, totalBytes: Long?, input: InputStream) {
        val transferId = FileTransferManager.startOutgoingTransfer(peerId, fileName, totalBytes)
        try {
            sendStart(peerId, fileName, totalBytes)
            val digest = MessageDigest.getInstance("SHA-256")
            streamFile(peerId, transferId, input, digest)
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            multiplexer.sendMessage(peerId, "$FILE_END_PREFIX$checksum")
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

    private suspend fun streamFile(peerId: String, transferId: String, input: InputStream, digest: MessageDigest) {
        val buffer = ByteArray(CHUNK_SIZE)
        while (true) {
            val read = withContext(Dispatchers.IO) { input.read(buffer) }
            if (read <= 0) {
                break
            }
            while (FileTransferManager.isPaused(transferId)) {
                delay(PAUSE_POLL_MS)
            }
            val payload = if (read == buffer.size) buffer else buffer.copyOf(read)
            digest.update(payload)
            val sent = multiplexer.sendFileData(peerId, payload)
            if (!sent) {
                throw Exception("Data channel lost during transfer")
            }
            FileTransferManager.updateProgress(transferId, read.toLong())
        }
    }

    companion object {
        private const val CHUNK_SIZE = 64 * 1024
        private const val SEND_RETRY_MS = 50L
        private const val PAUSE_POLL_MS = 200L
        private const val FILE_START_PREFIX = "FILE_START:"
        private const val FILE_END_PREFIX = "FILE_END:"
    }
}
