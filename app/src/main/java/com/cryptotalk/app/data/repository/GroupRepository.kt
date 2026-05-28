package com.cryptotalk.app.data.repository

import com.cryptotalk.app.crypto.MessageCrypto
import com.cryptotalk.app.data.model.Group
import com.cryptotalk.app.data.model.GroupMessage
import com.cryptotalk.app.crypto.KeySerializer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.security.PublicKey

/**
 * GroupRepository specifically handles group chats.
 * It manages creating groups, adding/removing members, and fetching the correct Public Keys for everyone in the group.
 */
class GroupRepository(
    private val userRepository: UserRepository
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val groupsRef = firestore.collection("conversations")
    private val messageCrypto = MessageCrypto()

    suspend fun createGroup(
        name: String,
        description: String,
        memberIds: List<String>,
        creatorId: String
    ): Result<String> = runCatching {
        val groupId = groupsRef.document().id
        val allMembers = (memberIds + creatorId).distinct()
        groupsRef.document(groupId).set(
            mapOf(
                "groupId" to groupId,
                "name" to name,
                "description" to description,
                "creatorId" to creatorId,
                "participants" to allMembers, // For existing query consistency
                "members" to allMembers,
                "admins" to listOf(creatorId),
                "createdAt" to System.currentTimeMillis(),
                "adminsOnlyMessage" to false,
                "isGroup" to true,
                "lastMessageAt" to System.currentTimeMillis()
            )
        ).await()
        groupId
    }

    fun getGroupFlow(groupId: String): Flow<Group?> = callbackFlow {
        val listener = groupsRef.document(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            trySend(snapshot?.toObject(Group::class.java))
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateGroupInfo(groupId: String, name: String, description: String): Result<Unit> = runCatching {
        groupsRef.document(groupId).update(
            mapOf("name" to name, "description" to description)
        ).await()
    }

    suspend fun addMember(groupId: String, userId: String): Result<Unit> = runCatching {
        val group = groupsRef.document(groupId).get().await().toObject(Group::class.java)
            ?: throw Exception("Group not found")
        val updatedMembers = (group.members + userId).distinct()
        groupsRef.document(groupId).update(
            mapOf("members" to updatedMembers, "participants" to updatedMembers)
        ).await()
    }

    suspend fun removeMember(groupId: String, userId: String): Result<Unit> = runCatching {
        val group = groupsRef.document(groupId).get().await().toObject(Group::class.java)
            ?: throw Exception("Group not found")
        val updatedMembers = group.members.filter { it != userId }
        val updatedAdmins = group.admins.filter { it != userId }
        groupsRef.document(groupId).update(
            mapOf(
                "members" to updatedMembers, 
                "participants" to updatedMembers,
                "admins" to updatedAdmins
            )
        ).await()
    }

    suspend fun promoteToAdmin(groupId: String, userId: String): Result<Unit> = runCatching {
        val group = groupsRef.document(groupId).get().await().toObject(Group::class.java)
            ?: throw Exception("Group not found")
        val updatedAdmins = (group.admins + userId).distinct()
        groupsRef.document(groupId).update("admins", updatedAdmins).await()
    }

    suspend fun demoteFromAdmin(groupId: String, userId: String): Result<Unit> = runCatching {
        val group = groupsRef.document(groupId).get().await().toObject(Group::class.java)
            ?: throw Exception("Group not found")
        val updatedAdmins = group.admins.filter { it != userId }
        groupsRef.document(groupId).update("admins", updatedAdmins).await()
    }

    suspend fun setAdminsOnlyMessage(groupId: String, enabled: Boolean): Result<Unit> = runCatching {
        groupsRef.document(groupId).update("adminsOnlyMessage", enabled).await()
    }

    suspend fun sendGroupMessage(
        groupId: String,
        senderId: String,
        senderName: String,
        plaintext: String
    ): Result<Unit> = runCatching {
        val group = groupsRef.document(groupId).get().await().toObject(Group::class.java)
            ?: throw Exception("Group not found")

        // Fetch all member public keys
        val publicKeys = mutableMapOf<String, PublicKey>()
        for (memberId in group.members) {
            val publicKeyStr = if (memberId == senderId) {
                userRepository.getLocalPublicKeyBase64(memberId) ?: userRepository.getPublicKey(memberId)
            } else {
                userRepository.getPublicKey(memberId)
            }
            
            if (publicKeyStr != null) {
                publicKeys[memberId] = KeySerializer.base64ToPublicKey(publicKeyStr)
            } else {
                android.util.Log.e("GroupRepository", "sendGroupMessage: Missing public key for member $memberId")
            }
        }

        // Final check for sender's own key
        if (!publicKeys.containsKey(senderId)) {
            val selfKeyStr = userRepository.getLocalPublicKeyBase64(senderId) ?: userRepository.getPublicKey(senderId)
            if (selfKeyStr != null) {
                publicKeys[senderId] = KeySerializer.base64ToPublicKey(selfKeyStr)
            } else {
                android.util.Log.e("GroupRepository", "sendGroupMessage: CRITICAL - Sender's own key is missing!")
            }
        }

        // Encrypt message for all members
        val payload = messageCrypto.encrypt(plaintext, publicKeys)

        val messageId = groupsRef.document(groupId).collection("messages").document().id
        val groupMessage = GroupMessage(
            messageId = messageId,
            groupId = groupId,
            senderId = senderId,
            senderName = senderName,
            encryptedContent = payload.encryptedMessage,
            iv = payload.iv,
            memberKeys = payload.participantKeys,
            timestamp = System.currentTimeMillis()
        )

        groupsRef.document(groupId).collection("messages").document(messageId).set(groupMessage).await()
        
        // Update group's last message timestamp for list sorting
        // Note: Adding a transient field or using another collection for recent chats might be better,
        // but for now we update the group doc.
        groupsRef.document(groupId).update("lastMessageAt", System.currentTimeMillis()).await()
    }

    fun getMessagesFlow(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        val listener = groupsRef.document(groupId).collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.toObjects(GroupMessage::class.java) ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }
}
