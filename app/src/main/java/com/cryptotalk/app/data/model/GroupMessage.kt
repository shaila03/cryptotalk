package com.cryptotalk.app.data.model

/**
 * Represents a single message sent within a group chat.
 * Contains the encrypted content and the specific keys needed for each group member to unlock it.
 */
data class GroupMessage(
    val messageId: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val encryptedContent: String = "",
    val iv: String = "",
    val memberKeys: Map<String, String> = emptyMap(), // userId -> AES key encrypted with public key
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "TEXT"
)
