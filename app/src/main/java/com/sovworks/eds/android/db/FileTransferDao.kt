package com.sovworks.eds.android.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileTransferDao {
    @Query("SELECT * FROM file_transfers ORDER BY updatedAt DESC")
    fun getAllTransfers(): Flow<List<FileTransferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: FileTransferEntity)

    @Update
    suspend fun updateTransfer(transfer: FileTransferEntity)

    @Query("SELECT * FROM file_transfers WHERE id = :id")
    suspend fun getTransferById(id: String): FileTransferEntity?
}
