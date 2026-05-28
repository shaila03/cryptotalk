package com.cryptotalk.app.data.model

/**
 * Encrypted message as stored in Firestore. No plaintext.
 */
enum class MessageStatus {
    SENT, DELIVERED, READ
}

/**
 * The internal structure of the encrypted message.
 * This JSON is what actually gets locked.
 */
data class SecureMessagePayload(
    val plaintext: String,
    val replyToId: String? = null,
    val replyToText: String? = null,
    val selfDestruct: Boolean = false,
    val destructAfterMs: Long = 0L,
    val isSensitive: Boolean = false,
    val isSystemMessage: Boolean = false
)

data class EncryptedMessageDoc(
    val encryptedMessage: String,
    val participantKeys: Map<String, String>, // userId -> encryptedAESKey
    val iv: String,
    val senderId: String,
    val timestamp: Long,
    val reactions: Map<String, String>? = null, // userId -> emoji
    val status: String = "SENT", // SENT, DELIVERED, READ
    val readAt: Long? = null, // Set when recipient reads
    val expirationTime: Long? = null, // Timestamp when message should be deleted
    val starredBy: List<String> = emptyList(),
    val scheduledAt: Long? = null // For scheduled messages
)

/**
 * Message with decrypted plaintext for UI display (held only in memory).
 */
data class Message(
    val id: String,
    val senderId: String,
    val plaintext: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val editedAt: Long? = null,
    val reactions: Map<String, String>? = null,
    val replyToId: String? = null,
    val replyToText: String? = null, // For UI convenience
    val senderName: String? = null, // Transient name for UI display
    val status: MessageStatus = MessageStatus.SENT,
    val readAt: Long? = null,
    val expirationTime: Long? = null,
    val isSensitive: Boolean = false,
    val selfDestruct: Boolean = false,
    val destructAfterMs: Long = 0L,
    val starredBy: List<String> = emptyList(),
    val isScheduled: Boolean = false,
    val scheduledAt: Long? = null
)

