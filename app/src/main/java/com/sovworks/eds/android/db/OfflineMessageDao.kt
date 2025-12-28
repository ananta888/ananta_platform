package com.sovworks.eds.android.db

import androidx.room.*

@Dao
interface OfflineMessageDao {
    @Insert
    suspend fun insert(message: OfflineMessageEntity)

    @Query("SELECT * FROM offline_messages WHERE recipientId = :recipientId ORDER BY timestamp ASC")
    suspend fun getMessagesForRecipient(recipientId: String): List<OfflineMessageEntity>

    @Delete
    suspend fun delete(message: OfflineMessageEntity)

    @Query("DELETE FROM offline_messages WHERE recipientId = :recipientId")
    suspend fun deleteAllForRecipient(recipientId: String)
}
