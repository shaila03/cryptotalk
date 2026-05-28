package com.cryptotalk.app.data.repository

import android.util.Log
import com.cryptotalk.app.crypto.MessageCrypto
import com.cryptotalk.app.data.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.security.PrivateKey
import kotlinx.coroutines.launch
import com.google.gson.Gson
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit
import com.cryptotalk.app.worker.ScheduledMessageWorker

/**
 * MessageRepository is the workhorse for sending and receiving messages.
 * It uses the MessageCrypto tool to lock/unlock messages, saves them to the secure local database,
 * and syncs them with the cloud database.
 */
class MessageRepository(
    private val settingsRepository: SettingsRepository
) {

    private val firestore = FirebaseFirestore.getInstance()
    private val messageCrypto = MessageCrypto()
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private fun messagesRef(conversationId: String) =
        firestore.collection("conversations")
            .document(conversationId)
            .collection(COLLECTION_MESSAGES)

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        participantPublicKeys: Map<String, String>, // userId -> publicKeyBase664
        plaintext: String,
        replyToId: String? = null,
        replyToText: String? = null,
        selfDestruct: Boolean = false,
        destructAfterMs: Long = 0L,
        scheduledAt: Long? = null
    ): Result<Unit> = runCatching {
        if (plaintext.length > MAX_MESSAGE_LENGTH) throw IllegalArgumentException("Message too long")
        
        if (scheduledAt != null && scheduledAt > System.currentTimeMillis()) {
            return scheduleMessage(
                conversationId, senderId, participantPublicKeys, plaintext, 
                replyToId, replyToText, selfDestruct, destructAfterMs, scheduledAt
            )
        }

        val useEncryption = settingsRepository.isEncryptionEnabled()
        
        val payloadMap = if (useEncryption && participantPublicKeys.isNotEmpty()) {
            val publicKeys = participantPublicKeys.mapValues {
                com.cryptotalk.app.crypto.KeySerializer.base64ToPublicKey(it.value)
            }
            
            // NEW: Pack ALL sensitive information into a single payload before encryption
            val securePayload = com.cryptotalk.app.data.model.SecureMessagePayload(
                plaintext = plaintext,
                replyToId = replyToId,
                replyToText = replyToText,
                selfDestruct = selfDestruct,
                destructAfterMs = destructAfterMs
            )
            val jsonPayload = Gson().toJson(securePayload)
            
            val payload = messageCrypto.encrypt(jsonPayload, publicKeys)
            mapOf(
                FIELD_ENCRYPTED_MESSAGE to payload.encryptedMessage,
                FIELD_PARTICIPANT_KEYS to payload.participantKeys,
                FIELD_IV to payload.iv,
                FIELD_SENDER_ID to senderId,
                FIELD_TIMESTAMP to System.currentTimeMillis(),
                FIELD_STATUS to com.cryptotalk.app.data.model.MessageStatus.SENT.name
            )
        } else {
            mapOf(
                FIELD_PLAINTEXT to plaintext,
                FIELD_SENDER_ID to senderId,
                FIELD_TIMESTAMP to System.currentTimeMillis(),
                FIELD_REPLY_TO_ID to replyToId,
                "replyToText" to replyToText,
                FIELD_STATUS to com.cryptotalk.app.data.model.MessageStatus.SENT.name,
                FIELD_SELF_DESTRUCT to selfDestruct,
                FIELD_DESTRUCT_AFTER_MS to destructAfterMs
            )
        }
        
        val mutablePayload = payloadMap.toMutableMap()
        com.cryptotalk.app.routing.RoutingManager.stripMetadata(mutablePayload)
        
        val docRef = messagesRef(conversationId).add(mutablePayload).await()
        
        // Cache locally in SecureDatabase
        val app = firestore.app.applicationContext as com.cryptotalk.app.CryptoTalkApplication
        app.secureDatabase?.messageDao()?.insertMessage(
            com.cryptotalk.app.data.local.entity.MessageEntity(
                id = docRef.id,
                conversationId = conversationId,
                senderId = senderId,
                encryptedPayload = Gson().toJson(payloadMap),
                timestamp = payloadMap[FIELD_TIMESTAMP] as Long,
                status = payloadMap[FIELD_STATUS] as String
            )
        )
    }

    private suspend fun scheduleMessage(
        conversationId: String,
        senderId: String,
        participantPublicKeys: Map<String, String>,
        plaintext: String,
        replyToId: String?,
        replyToText: String?,
        selfDestruct: Boolean,
        destructAfterMs: Long,
        scheduledAt: Long
    ): Result<Unit> = runCatching {
        val delayMs = scheduledAt - System.currentTimeMillis()
        if (delayMs <= 0) {
            sendMessage(
                conversationId, senderId, participantPublicKeys, plaintext, 
                replyToId, replyToText, selfDestruct, destructAfterMs, null
            ).getOrThrow()
            return@runCatching
        }

        val dataBuilder = Data.Builder()
            .putString(ScheduledMessageWorker.KEY_CONV_ID, conversationId)
            .putString(ScheduledMessageWorker.KEY_SENDER_ID, senderId)
            .putString(ScheduledMessageWorker.KEY_PLAINTEXT, plaintext)
            .putBoolean(ScheduledMessageWorker.KEY_SELF_DESTRUCT, selfDestruct)
            .putLong(ScheduledMessageWorker.KEY_DESTRUCT_MS, destructAfterMs)

        if (replyToId != null) dataBuilder.putString(ScheduledMessageWorker.KEY_REPLY_ID, replyToId)
        if (replyToText != null) dataBuilder.putString(ScheduledMessageWorker.KEY_REPLY_TEXT, replyToText)
        if (participantPublicKeys.isNotEmpty()) {
            dataBuilder.putString(ScheduledMessageWorker.KEY_KEYS, Gson().toJson(participantPublicKeys))
        }

        val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(dataBuilder.build())
            .addTag("SCHEDULED_MESSAGE_$conversationId")
            .build()

        val context = firestore.app.applicationContext
        WorkManager.getInstance(context).enqueueUniqueWork(
            "MSG_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    suspend fun sendSystemMessage(
        conversationId: String,
        text: String
    ): Result<Unit> = runCatching {

        val doc = mapOf(
            FIELD_PLAINTEXT to text,
            FIELD_SENDER_ID to "system",
            FIELD_TIMESTAMP to System.currentTimeMillis(),
            FIELD_STATUS to com.cryptotalk.app.data.model.MessageStatus.SENT.name
        )
        messagesRef(conversationId).add(doc).await()
    }

    suspend fun markMessageAsRead(conversationId: String, messageId: String): Result<Unit> = runCatching {
        val messageRef = messagesRef(conversationId).document(messageId)
        val conversation = firestore.collection("conversations").document(conversationId).get().await()
        val disappearDuration = conversation.getLong("disappearDuration")
        
        val updates = mutableMapOf<String, Any>(FIELD_STATUS to com.cryptotalk.app.data.model.MessageStatus.READ.name)
        if (disappearDuration != null && disappearDuration > 0) {
            val expirationTime = System.currentTimeMillis() + disappearDuration
            updates[FIELD_EXPIRATION_TIME] = expirationTime
        }
        messageRef.update(updates).await()
        
        // Update local cache
        val app = firestore.app.applicationContext as com.cryptotalk.app.CryptoTalkApplication
        // Local cache update is handled by separate sync or specific UI triggers.
        // For simplicity, we skip re-inserting the whole entity here.
    }

    suspend fun toggleMessageSensitive(conversationId: String, messageId: String, isSensitive: Boolean): Result<Unit> = runCatching {
        messagesRef(conversationId).document(messageId).update("isSensitive", isSensitive).await()
    }

    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> = runCatching {
        messagesRef(conversationId).document(messageId).delete().await()
        // Delete from local cache
        val app = firestore.app.applicationContext as com.cryptotalk.app.CryptoTalkApplication
        app.secureDatabase?.messageDao()?.deleteMessage(messageId)
    }

    suspend fun toggleReaction(conversationId: String, messageId: String, userId: String, emoji: String): Result<Unit> = runCatching {
        // PROMPT 5 FIX: Use dot-notation field path to avoid overwriting other users' reactions atomically
        val ref = messagesRef(conversationId).document(messageId)
        val doc = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        val reactions = (doc.get(FIELD_REACTIONS) as? Map<String, String>) ?: emptyMap()
        val fieldPath = "reactions.$userId"
        if (reactions[userId] == emoji) {
            // Toggle off: remove the reaction
            ref.update(fieldPath, com.google.firebase.firestore.FieldValue.delete()).await()
        } else {
            // Set or change reaction
            ref.update(fieldPath, emoji).await()
        }
    }

    suspend fun toggleStar(conversationId: String, messageId: String, userId: String): Result<Unit> = runCatching {
        val ref = messagesRef(conversationId).document(messageId)
        val doc = ref.get().await()
        @Suppress("UNCHECKED_CAST")
        val starredBy = doc.get("starredBy") as? List<String> ?: emptyList()
        if (starredBy.contains(userId)) {
            ref.update("starredBy", com.google.firebase.firestore.FieldValue.arrayRemove(userId)).await()
        } else {
            ref.update("starredBy", com.google.firebase.firestore.FieldValue.arrayUnion(userId)).await()
        }
    }

    suspend fun updateMessage(
        conversationId: String,
        messageId: String,
        senderId: String,
        participantPublicKeys: Map<String, String>,
        newPlaintext: String
    ): Result<Unit> = runCatching {
        if (newPlaintext.length > MAX_MESSAGE_LENGTH) throw IllegalArgumentException("Message too long")
        val useEncryption = settingsRepository.isEncryptionEnabled()
        if (useEncryption && participantPublicKeys.isEmpty()) {
            throw IllegalStateException("Cannot edit message: recipient encryption keys unavailable")
        }
        val ref = messagesRef(conversationId).document(messageId)
        
        val updateMap: MutableMap<String, Any> = if (useEncryption && participantPublicKeys.isNotEmpty()) {
            val publicKeys = participantPublicKeys.mapValues {
                com.cryptotalk.app.crypto.KeySerializer.base64ToPublicKey(it.value)
            }
            
            // Fetch current message to preserve settings (like selfDestruct) if needed, 
            // but usually edit just changes text.
            // For total encryption, we should ideally preserve the original metadata or allow editing it.
            // Here we'll treat it as a text update but keep it in the secure payload.
            val securePayload = com.cryptotalk.app.data.model.SecureMessagePayload(
                plaintext = newPlaintext
                // Note: We might want to preserve replyTo etc, but those are generally immutable on edit in most apps.
            )
            val jsonPayload = Gson().toJson(securePayload)
            
            val payload = messageCrypto.encrypt(jsonPayload, publicKeys)
            mutableMapOf(
                FIELD_ENCRYPTED_MESSAGE to payload.encryptedMessage,
                FIELD_PARTICIPANT_KEYS to payload.participantKeys,
                FIELD_IV to payload.iv
            )
        } else {
            mutableMapOf(FIELD_PLAINTEXT to newPlaintext)
        }
        // Always stamp isEdited and editedAt (serverTimestamp for accuracy)
        updateMap[FIELD_IS_EDITED] = true
        updateMap[FIELD_EDITED_AT] = com.google.firebase.firestore.FieldValue.serverTimestamp()
        ref.update(updateMap).await()
    }

    suspend fun markAllAsRead(conversationId: String, currentUserId: String): Result<Unit> = runCatching {
        if (!settingsRepository.isReadReceiptsEnabled()) return@runCatching

        // Find all messages sent by someone else that are NOT yet READ
        val unreadQuery = messagesRef(conversationId)
            .whereNotEqualTo(FIELD_SENDER_ID, currentUserId)
            .get()
            .await()

        val toUpdate = unreadQuery.documents.filter { 
            val status = it.getString(FIELD_STATUS)
            status == com.cryptotalk.app.data.model.MessageStatus.SENT.name || 
            status == com.cryptotalk.app.data.model.MessageStatus.DELIVERED.name
        }

        if (toUpdate.isEmpty()) return@runCatching

        val conversation = firestore.collection("conversations").document(conversationId).get().await()
        val disappearDuration = conversation.getLong("disappearDuration")
        val now = System.currentTimeMillis()

        firestore.runBatch { batch ->
            for (doc in toUpdate) {
                val updates = mutableMapOf<String, Any>(
                    FIELD_STATUS to com.cryptotalk.app.data.model.MessageStatus.READ.name,
                    "readAt" to now // Needed for disappearing messages timer
                )
                if (disappearDuration != null && disappearDuration > 0) {
                    updates[FIELD_EXPIRATION_TIME] = now + disappearDuration
                }
                batch.update(doc.reference, updates)
            }
        }.await()
    }

    suspend fun markAllAsDelivered(conversationId: String, currentUserId: String): Result<Unit> = runCatching {
        val unreadQuery = messagesRef(conversationId)
            .whereNotEqualTo(FIELD_SENDER_ID, currentUserId)
            .whereEqualTo(FIELD_STATUS, com.cryptotalk.app.data.model.MessageStatus.SENT.name)
            .get()
            .await()

        if (unreadQuery.isEmpty) return@runCatching

        firestore.runBatch { batch ->
            for (doc in unreadQuery.documents) {
                batch.update(doc.reference, FIELD_STATUS, com.cryptotalk.app.data.model.MessageStatus.DELIVERED.name)
            }
        }.await()
    }

    suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean) {
        if (!settingsRepository.isTypingIndicatorEnabled()) return
        val convRef = firestore.collection("conversations").document(conversationId)
        try {
            if (isTyping) {
                // Use dot-notation to update ONLY this user's entry in the typingUsers map
                convRef.update("typingUsers.$userId", System.currentTimeMillis()).await()
            } else {
                // Remove this user's entry from the map when they stop typing
                convRef.update("typingUsers.$userId", com.google.firebase.firestore.FieldValue.delete()).await()
            }
        } catch (e: Exception) {
            // Fallback: If the field doesn't exist, create it using merge
            if (isTyping) {
                convRef.set(
                    mapOf("typingUsers" to mapOf(userId to System.currentTimeMillis())),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }
    }

    fun typingStatusFlow(conversationId: String, otherUserId: String): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection("conversations")
            .document(conversationId)
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val typingUsers = snapshot?.get("typingUsers") as? Map<String, Long> ?: emptyMap()
                val lastTyped = typingUsers[otherUserId] ?: 0L
                // Consider the user as "typing" if their last update was within the last 3 seconds
                val isTyping = lastTyped > 0 && (System.currentTimeMillis() - lastTyped < 3000)
                trySend(isTyping)
            }
        awaitClose { listener.remove() }
    }

    fun messagesFlow(
        conversationId: String,
        currentUserId: String,
        privateKey: PrivateKey,
        blockedUsers: List<String> = emptyList()
    ): Flow<List<Message>> = callbackFlow {
        val isPerformanceMode = settingsRepository.performanceModeEnabled.first()
        val limitCount = if (isPerformanceMode) 30L else 200L

        val listener = messagesRef(conversationId)
            .orderBy(FIELD_TIMESTAMP)
            .limitToLast(limitCount)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val senderId = doc.getString(FIELD_SENDER_ID) ?: return@mapNotNull null

                    // Filter messages from blocked users
                    if (blockedUsers.contains(senderId)) return@mapNotNull null

                    val timestamp = doc.getLong(FIELD_TIMESTAMP) ?: 0L
                    val expirationTime = doc.getLong(FIELD_EXPIRATION_TIME)
                    
                    // Skip expired messages
                    if (expirationTime != null && expirationTime < System.currentTimeMillis()) {
                        // Proactively delete from DB to stay clean (launch in separate scope or just skip here)
                        // For efficiency we just skip, but a cleanup worker is better.
                        // However, we can at least try to delete it here if it's the current user's session.
                        repositoryScope.launch {
                            try { doc.reference.delete().await() } catch(e: Exception) {}
                        }
                        return@mapNotNull null
                    }

                    // Try plaintext first (unencrypted messages or fallback)
                    val plaintext = doc.getString(FIELD_PLAINTEXT)
                        ?: run {
                            // Attempt decryption
                            val encryptedMessage = doc.getString(FIELD_ENCRYPTED_MESSAGE)
                            val iv = doc.getString(FIELD_IV)

                            if (encryptedMessage == null || iv == null) {
                                // No plaintext and no encrypted data = skip
                                return@mapNotNull null
                            }

                            // Look up AES key for this user from the participantKeys map
                            @Suppress("UNCHECKED_CAST")
                            val participantKeys = doc.get(FIELD_PARTICIPANT_KEYS) as? Map<String, Any?>

                            // Try all possible key lookups for backward compatibility
                            val encryptedAESKey = participantKeys?.get(currentUserId)?.toString()
                                ?: doc.getString("encryptedAESKeySender").takeIf { senderId == currentUserId }
                                ?: doc.getString("encryptedAESKey")

                            if (encryptedAESKey != null) {
                                try {
                                    messageCrypto.decrypt(encryptedMessage, encryptedAESKey, iv, privateKey)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Decryption failed for msg ${doc.id}: ${e.message}")
                                    "🔒 Unable to decrypt message"
                                }
                            } else {
                                Log.w(TAG, "No encrypted AES key found for user $currentUserId in msg ${doc.id}. ParticipantKeys keys: ${participantKeys?.keys}")
                                "🔒 Message encrypted for other recipients"
                            }
                        }

                    var finalPlaintext = plaintext
                    var finalReplyToId = doc.getString(FIELD_REPLY_TO_ID)
                    var finalReplyToText = doc.getString("replyToText")
                    var finalSelfDestruct = doc.getLong(FIELD_EXPIRATION_TIME) != null
                    var finalDestructAfterMs = doc.getLong(FIELD_DESTRUCT_AFTER_MS) ?: 0L

                    // If it was decrypted, it might be a JSON payload (SecureMessagePayload)
                    if (plaintext.startsWith("{") && plaintext.endsWith("}")) {
                        try {
                            val securePayload = Gson().fromJson(plaintext, com.cryptotalk.app.data.model.SecureMessagePayload::class.java)
                            if (securePayload != null) {
                                finalPlaintext = securePayload.plaintext
                                finalReplyToId = securePayload.replyToId
                                finalReplyToText = securePayload.replyToText
                                finalSelfDestruct = securePayload.selfDestruct
                                finalDestructAfterMs = securePayload.destructAfterMs
                            }
                        } catch (e: Exception) {
                            // Not a JSON payload or failed to parse, use raw plaintext
                        }
                    }

                    val editedAt = doc.getLong(FIELD_EDITED_AT)
                    @Suppress("UNCHECKED_CAST")
                    val reactions = doc.get(FIELD_REACTIONS) as? Map<String, String>
                    val statusStr = doc.getString(FIELD_STATUS) ?: "SENT"
                    val status = try { com.cryptotalk.app.data.model.MessageStatus.valueOf(statusStr) } catch(e: Exception) { com.cryptotalk.app.data.model.MessageStatus.SENT }
                    val readAt = doc.getLong("readAt")
                    @Suppress("UNCHECKED_CAST")
                    val starredBy = doc.get("starredBy") as? List<String> ?: emptyList()

                    Message(
                        id = doc.id,
                        senderId = senderId,
                        plaintext = finalPlaintext,
                        timestamp = timestamp,
                        isFromMe = senderId == currentUserId,
                        editedAt = editedAt,
                        reactions = reactions,
                        replyToId = finalReplyToId,
                        replyToText = finalReplyToText,
                        status = status,
                        selfDestruct = finalSelfDestruct,
                        destructAfterMs = finalDestructAfterMs,
                        starredBy = starredBy,
                        isScheduled = doc.getLong("scheduledAt") != null,
                        scheduledAt = doc.getLong("scheduledAt")
                    )
                }?.sortedBy { it.timestamp } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val TAG = "MessageRepository"
        private const val COLLECTION_MESSAGES = "messages"
        private const val FIELD_ENCRYPTED_MESSAGE = "encryptedMessage"
        private const val FIELD_PARTICIPANT_KEYS = "participantKeys"
        private const val FIELD_IV = "iv"
        private const val FIELD_SENDER_ID = "senderId"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_PLAINTEXT = "plaintext"
        private const val FIELD_EDITED_AT = "editedAt"
        private const val FIELD_IS_EDITED = "isEdited"
        private const val FIELD_REACTIONS = "reactions"
        private const val FIELD_REPLY_TO_ID = "replyToId"
        private const val FIELD_STATUS = "status"
        private const val FIELD_EXPIRATION_TIME = "expirationTime"
        private const val FIELD_SELF_DESTRUCT = "selfDestruct"
        private const val FIELD_DESTRUCT_AFTER_MS = "destructAfterMs"
        const val MAX_MESSAGE_LENGTH = 4 * 1024
    }
}
