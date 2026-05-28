package com.cryptotalk.app.data.model

/**
 * Represents a group chat's basic details like its name, description, and who the members/admins are.
 */
data class Group(
    val groupId: String = "",
    val name: String = "",
    val description: String = "",
    val groupIcon: String? = null,
    val creatorId: String = "",
    val members: List<String> = emptyList(),
    val admins: List<String> = emptyList(),
    val adminsOnlyMessage: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
