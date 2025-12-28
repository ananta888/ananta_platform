package com.sovworks.eds.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_messages")
data class OfflineMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientId: String,
    val senderId: String,
    val encryptedPayload: String, // Die bereits E2E verschlüsselte Nachricht
    val timestamp: Long,
    val signature: String? = null // Optionale Signatur des Senders zur Verifizierung
)
