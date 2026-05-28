package com.cryptotalk.app.data.model

/**
 * Represents a user's public profile, which other people can see (like their name and Public Key).
 */
data class User(
    val userId: String,
    val publicKey: String,
    val displayName: String? = null,
    val createdAt: Long? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L
)

/**
 * Represents a user's private settings, which only they can see (like their email and blocked users list).
 */
data class PrivateProfile(
    val email: String,
    val deviceId: String? = null,
    val fcmToken: String? = null,
    val blockedUsers: List<String> = emptyList()
)
