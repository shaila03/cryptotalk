package com.cryptotalk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cryptotalk.app.data.model.MessageStatus

/**
 * MessageEntity represents a single row (a message) in the secure local database.
 * The actual message content is stored in the 'encryptedPayload' column so it's safe even at rest.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val encryptedPayload: String, // Base64 encoded EncryptedPayload JSON
    val timestamp: Long,
    val status: String,
    val expirationTime: Long? = null
)
