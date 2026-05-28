package com.cryptotalk.app.data.repository

import com.cryptotalk.app.data.model.Conversation
import com.cryptotalk.app.data.model.ParticipantInfo
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * ConversationRepository manages all chat conversations (both one-on-one and groups).
 * It talks to Firestore (the cloud database) to fetch the list of your chats and keeps track of unread messages.
 */
class ConversationRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val conversationsRef = firestore.collection(COLLECTION_CONVERSATIONS)

    fun conversationId(userId1: String, userId2: String): String {
        val sorted = listOf(userId1, userId2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    suspend fun getConversation(conversationId: String): Conversation? {
        val doc = conversationsRef.document(conversationId).get().await()
        if (!doc.exists()) return null
        return documentToConversation(doc)
    }

    private fun documentToConversation(doc: com.google.firebase.firestore.DocumentSnapshot): Conversation {
        val participants = (doc.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty()
        val adminIds = (doc.get(FIELD_ADMIN_IDS) as? List<*>)?.map { it.toString() }
            ?: listOfNotNull(doc.getString(FIELD_GROUP_ADMIN))
        
        @Suppress("UNCHECKED_CAST")
        val metadataMap = doc.get(FIELD_PARTICIPANT_METADATA) as? Map<String, Map<String, Any>>
        val participantMetadata: Map<String, ParticipantInfo> = metadataMap?.map { (userId, info) ->
            userId to ParticipantInfo(
                addedBy = info["addedBy"] as? String ?: "",
                addedAt = info["addedAt"] as? Long ?: 0L
            )
        }?.toMap() ?: emptyMap()

        @Suppress("UNCHECKED_CAST")
        val unreadCounts = (doc.get(FIELD_UNREAD_COUNTS) as? Map<String, Long>)
            ?.entries?.associate { it.key to it.value.toInt() }.orEmpty()

        return Conversation(
            id = doc.id,
            participants = participants,
            createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
            lastMessageAt = doc.getLong(FIELD_LAST_MESSAGE_AT) ?: 0L,
            isGroup = doc.getBoolean(FIELD_IS_GROUP) ?: false,
            name = doc.getString(FIELD_NAME),
            groupAdmin = doc.getString(FIELD_GROUP_ADMIN),
            adminIds = adminIds,
            participantMetadata = participantMetadata,
            unreadCounts = unreadCounts,
            disappearDuration = doc.getLong(FIELD_DISAPPEAR_DURATION),
            isSensitive = doc.getBoolean(FIELD_IS_SENSITIVE) ?: false
        )
    }

    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Result<Conversation> = runCatching {
        val id = conversationId(currentUserId, otherUserId)
        val doc = conversationsRef.document(id).get().await()
        val now = System.currentTimeMillis()
        if (doc.exists()) {
            val participantsList = (doc.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty()
            val createdAtVal = doc.getLong(FIELD_CREATED_AT) ?: now
            Conversation(
                id = doc.id,
                participants = participantsList,
                createdAt = createdAtVal,
                lastMessageAt = doc.getLong(FIELD_LAST_MESSAGE_AT) ?: createdAtVal,
                isGroup = doc.getBoolean(FIELD_IS_GROUP) ?: false,
                name = doc.getString(FIELD_NAME),
                groupAdmin = doc.getString(FIELD_GROUP_ADMIN),
                isSensitive = doc.getBoolean(FIELD_IS_SENSITIVE) ?: false
            )
        } else {
            conversationsRef.document(id).set(
                mapOf(
                    FIELD_PARTICIPANTS to listOf(currentUserId, otherUserId),
                    FIELD_CREATED_AT to now,
                    FIELD_LAST_MESSAGE_AT to now,
                    FIELD_IS_GROUP to false
                )
            ).await()
            Conversation(
                id = id,
                participants = listOf(currentUserId, otherUserId),
                createdAt = now,
                lastMessageAt = now,
                isGroup = false
            )
        }
    }

    suspend fun createGroup(name: String, adminId: String, participantIds: List<String>): Result<String> = runCatching {
        val participants = (participantIds + adminId).distinct()
        val now = System.currentTimeMillis()
        val doc = conversationsRef.document()
        
        val metadata = participants.associateWith {
            mapOf("addedBy" to adminId, "addedAt" to now)
        }

        doc.set(
            mapOf(
                FIELD_PARTICIPANTS to participants,
                FIELD_CREATED_AT to now,
                FIELD_LAST_MESSAGE_AT to now,
                FIELD_IS_GROUP to true,
                FIELD_NAME to name,
                FIELD_GROUP_ADMIN to adminId,
                FIELD_ADMIN_IDS to listOf(adminId),
                FIELD_PARTICIPANT_METADATA to metadata
            )
        ).await()
        doc.id
    }

    fun conversationFlow(conversationId: String): Flow<Conversation?> = callbackFlow {
        val listener = conversationsRef.document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(documentToConversation(snapshot))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    fun conversationsFlow(currentUserId: String): Flow<List<Conversation>> = callbackFlow {
        val listener = conversationsRef
            .whereArrayContains(FIELD_PARTICIPANTS, currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.map { doc ->
                    documentToConversation(doc)
                }?.sortedByDescending { it.lastMessageAt } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateLastMessageAt(conversationId: String): Result<Unit> = runCatching {
        conversationsRef.document(conversationId).update(FIELD_LAST_MESSAGE_AT, System.currentTimeMillis()).await()
    }

    suspend fun leaveGroup(conversationId: String, userId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        val doc = docRef.get().await()
        if (doc.exists()) {
            val participants = (doc.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty().toMutableList()
            participants.remove(userId)
            if (participants.isEmpty()) {
                docRef.delete().await()
            } else {
                docRef.update(FIELD_PARTICIPANTS, participants).await()
            }
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        conversationsRef.document(conversationId).delete().await()
        // Delete from local cache
        val app = firestore.app.applicationContext as com.cryptotalk.app.CryptoTalkApplication
        app.secureDatabase?.messageDao()?.deleteMessagesForConversation(conversationId)
    }

    suspend fun renameGroup(conversationId: String, newName: String): Result<Unit> = runCatching {
        conversationsRef.document(conversationId).update(FIELD_NAME, newName).await()
    }

    suspend fun incrementUnreadCounts(conversationId: String, senderId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val participants = (snapshot.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty()
            @Suppress("UNCHECKED_CAST")
            val unreadCounts = (snapshot.get(FIELD_UNREAD_COUNTS) as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
            
            for (userId in participants) {
                if (userId != senderId) {
                    val currentCount = unreadCounts[userId] ?: 0L
                    unreadCounts[userId] = currentCount + 1
                }
            }
            transaction.update(docRef, FIELD_UNREAD_COUNTS, unreadCounts)
            transaction.update(docRef, FIELD_LAST_MESSAGE_AT, System.currentTimeMillis())
        }.await()
    }

    suspend fun resetUnreadCount(conversationId: String, userId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            @Suppress("UNCHECKED_CAST")
            val unreadCounts = (snapshot.get(FIELD_UNREAD_COUNTS) as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
            unreadCounts[userId] = 0L
            transaction.update(docRef, FIELD_UNREAD_COUNTS, unreadCounts)
        }.await()
    }

    suspend fun addParticipantsToGroup(conversationId: String, newParticipantIds: List<String>, actorId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (!snapshot.exists()) throw Exception("Conversation not found")
            
            val existingParticipants = (snapshot.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty()
            val updatedParticipants = (existingParticipants + newParticipantIds).distinct()
            
            @Suppress("UNCHECKED_CAST")
            val metadata = (snapshot.get(FIELD_PARTICIPANT_METADATA) as? Map<String, Map<String, Any>>)?.toMutableMap() ?: mutableMapOf()
            val now = System.currentTimeMillis()
            newParticipantIds.forEach { id ->
                if (!metadata.containsKey(id)) {
                    metadata[id] = mapOf("addedBy" to actorId, "addedAt" to now)
                }
            }
            
            transaction.update(docRef, FIELD_PARTICIPANTS, updatedParticipants)
            transaction.update(docRef, FIELD_PARTICIPANT_METADATA, metadata)
            transaction.update(docRef, FIELD_LAST_MESSAGE_AT, now)
        }.await()
    }

    suspend fun removeParticipant(conversationId: String, userId: String, actorId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val adminIds = (snapshot.get(FIELD_ADMIN_IDS) as? List<*>)?.map { it.toString() }
                ?: listOfNotNull(snapshot.getString(FIELD_GROUP_ADMIN))
            
            if (!adminIds.contains(actorId)) throw Exception("Only admins can remove participants")
            
            val participants = (snapshot.get(FIELD_PARTICIPANTS) as? List<*>)?.map { it.toString() }.orEmpty().toMutableList()
            participants.remove(userId)
            
            @Suppress("UNCHECKED_CAST")
            val metadata = (snapshot.get(FIELD_PARTICIPANT_METADATA) as? Map<String, Map<String, Any>>)?.toMutableMap() ?: mutableMapOf()
            metadata.remove(userId)
            
            val newAdmins = adminIds.toMutableList()
            newAdmins.remove(userId)

            transaction.update(docRef, FIELD_PARTICIPANTS, participants)
            transaction.update(docRef, FIELD_PARTICIPANT_METADATA, metadata)
            transaction.update(docRef, FIELD_ADMIN_IDS, newAdmins)
            transaction.update(docRef, FIELD_LAST_MESSAGE_AT, System.currentTimeMillis())
        }.await()
    }

    suspend fun makeAdmin(conversationId: String, userId: String, actorId: String): Result<Unit> = runCatching {
        val docRef = conversationsRef.document(conversationId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val adminIds = (snapshot.get(FIELD_ADMIN_IDS) as? List<*>)?.map { it.toString() }
                ?: listOfNotNull(snapshot.getString(FIELD_GROUP_ADMIN))
            
            if (!adminIds.contains(actorId)) throw Exception("Only admins can promote others")
            
            val newAdmins = (adminIds + userId).distinct()
            transaction.update(docRef, FIELD_ADMIN_IDS, newAdmins)
            transaction.update(docRef, FIELD_LAST_MESSAGE_AT, System.currentTimeMillis())
        }.await()
    }

    suspend fun getActiveConversations(currentUserId: String): List<Conversation> = runCatching {
        conversationsRef
            .whereArrayContains(FIELD_PARTICIPANTS, currentUserId)
            .get()
            .await()
            .documents.map { doc ->
                documentToConversation(doc)
            }.sortedByDescending { it.lastMessageAt }
    }.getOrDefault(emptyList())

    suspend fun setDisappearDuration(conversationId: String, duration: Long?): Result<Unit> = runCatching {
        conversationsRef.document(conversationId).update(FIELD_DISAPPEAR_DURATION, duration).await()
    }

    suspend fun setSensitive(conversationId: String, isSensitive: Boolean): Result<Unit> = runCatching {
        conversationsRef.document(conversationId).update(FIELD_IS_SENSITIVE, isSensitive).await()
    }

    companion object {
        private const val COLLECTION_CONVERSATIONS = "conversations"
        private const val FIELD_PARTICIPANTS = "participants"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_LAST_MESSAGE_AT = "lastMessageAt"
        private const val FIELD_IS_GROUP = "isGroup"
        private const val FIELD_NAME = "name"
        private const val FIELD_GROUP_ADMIN = "groupAdmin"
        private const val FIELD_ADMIN_IDS = "adminIds"
        private const val FIELD_PARTICIPANT_METADATA = "participantMetadata"
        private const val FIELD_UNREAD_COUNTS = "unreadCounts"
        private const val FIELD_DISAPPEAR_DURATION = "disappearDuration"
        private const val FIELD_IS_SENSITIVE = "isSensitive"
    }
}
