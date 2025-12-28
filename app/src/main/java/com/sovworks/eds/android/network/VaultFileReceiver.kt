package com.sovworks.eds.android.network

import android.content.Context
import com.sovworks.eds.fs.File
import com.sovworks.eds.locations.LocationsManager
import com.sovworks.eds.android.locations.EncFsLocation
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

class VaultFileReceiver(
    private val context: Context
) : DataChannelListener {
    
    private val activeTransfers = ConcurrentHashMap<String, FileTransferSession>()

    data class FileTransferSession(
        val fileName: String,
        val file: File,
        val outputStream: OutputStream
    )

    override fun onMessageReceived(peerId: String, message: String) {
        if (message.startsWith("FILE_START:")) {
            val fileName = message.substringAfter("FILE_START:")
            startNewTransfer(peerId, fileName)
        } else if (message == "FILE_END") {
            finishTransfer(peerId)
        }
    }

    private fun startNewTransfer(peerId: String, fileName: String) {
        val lm = LocationsManager.getLocationsManager(context)
        // Finde die erste offene verschlüsselte Location
        val location = lm.getLoadedLocations(false).filterIsInstance<EncFsLocation>().firstOrNull { it.isOpen }
        
        if (location != null) {
            try {
                val parentDir = location.getCurrentPath().getDirectory()
                val newFile = parentDir.createFile(fileName)
                val outputStream = newFile.getOutputStream()
                activeTransfers[peerId] = FileTransferSession(fileName, newFile, outputStream)
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    override fun onBinaryReceived(peerId: String, data: ByteArray) {
        activeTransfers[peerId]?.let { session ->
            try {
                session.outputStream.write(data)
            } catch (e: Exception) {
                // Error handling
            }
        }
    }

    private fun finishTransfer(peerId: String) {
        activeTransfers.remove(peerId)?.let { session ->
            try {
                session.outputStream.close()
            } catch (e: Exception) {
                // Error handling
            }
        }
    }
}
