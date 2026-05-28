package com.cryptotalk.app.data.model

data class ParticipantInfo(
    val addedBy: String,
    val addedAt: Long
)

/**
 * Represents a chat conversation between two users or a group.
 * It keeps track of who is in the chat, when the last message was, and unread message counts.
 */
data class Conversation(
    val id: String,
    val participants: List<String>,
    val createdAt: Long,
    val lastMessageAt: Long,
    val isGroup: Boolean = false,
    val name: String? = null,
    val groupAdmin: String? = null, // Legacy field, keeping for compatibility
    val adminIds: List<String> = emptyList(), // Support for multiple admins
    val participantMetadata: Map<String, ParticipantInfo> = emptyMap(), // userId -> info
    val unreadCounts: Map<String, Int> = emptyMap(), // userId -> count
    val disappearDuration: Long? = null, // In milliseconds
    val isSensitive: Boolean = false,
    val typingUsers: Map<String, Long> = emptyMap() // userId -> timestamp
) {
    fun otherUserId(currentUserId: String): String =
        participants.firstOrNull { it != currentUserId } ?: participants.firstOrNull() ?: currentUserId

    fun getDisplayName(currentUserId: String, users: Map<String, User>): String {
        return if (isGroup) {
            name ?: "Group Chat"
        } else {
            val otherId = otherUserId(currentUserId)
            users[otherId]?.displayName ?: "User ${otherId.take(4)}"
        }
    }
}
