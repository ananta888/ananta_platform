package com.sovworks.eds.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sovworks.eds.android.transfer.TransferDirection
import com.sovworks.eds.android.transfer.FileTransferStatus

@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val fileName: String,
    val direction: String, // Store as String for simplicity or use Converters
    val bytesTransferred: Long,
    val totalBytes: Long?,
    val status: String,
    val updatedAt: Long
)
