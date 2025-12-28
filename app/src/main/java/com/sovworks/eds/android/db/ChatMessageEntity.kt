package com.sovworks.eds.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: String, // peerId or groupId
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isMe: Boolean,
    val groupId: String? = null
)
