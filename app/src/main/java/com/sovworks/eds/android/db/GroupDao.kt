package com.sovworks.eds.android.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM chat_groups")
    fun getAllGroups(): Flow<List<ChatGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ChatGroupEntity)

    @Delete
    suspend fun deleteGroup(group: ChatGroupEntity)
}
