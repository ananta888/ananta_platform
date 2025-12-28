package com.sovworks.eds.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_groups")
data class ChatGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val memberIds: String, // Komma-separierte Liste von Peer-IDs
    val groupKeyBase64: String?
)
