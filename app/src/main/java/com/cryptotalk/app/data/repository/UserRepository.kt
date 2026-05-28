package com.cryptotalk.app.data.repository

import com.cryptotalk.app.crypto.KeySerializer
import com.cryptotalk.app.crypto.KeystoreHelper
import com.cryptotalk.app.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

import android.content.Context

/**
 * UserRepository manages user profiles.
 * It handles fetching other users' profiles (like their Public Keys), blocking users,
 * and saving the user's FCM token for push notifications.
 */
class UserRepository(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val usersRef = firestore.collection(COLLECTION_USERS)
    private val lookupRef = firestore.collection(COLLECTION_LOOKUP)
    private val keystoreHelper = KeystoreHelper(context)

    private fun privateProfileRef(userId: String) = 
        usersRef.document(userId).collection("private").document("profile")

    suspend fun ensureKeyPairAndUploadPublicKey(userId: String, email: String, displayName: String? = null): Result<Unit> = runCatching {
        val (publicKey, _) = keystoreHelper.getOrCreateRsaKeyPair(userId)
        val publicKeyBase64 = KeySerializer.publicKeyToBase64(publicKey)
        
        val publicData = mutableMapOf(
            FIELD_PUBLIC_KEY to publicKeyBase64,
            FIELD_CREATED_AT to System.currentTimeMillis()
        )
        displayName?.let { publicData[FIELD_DISPLAY_NAME] = it }
        
        val privateData = mutableMapOf(
            FIELD_EMAIL to email
        )

        firestore.runBatch { batch ->
            batch.set(usersRef.document(userId), publicData, SetOptions.merge())
            batch.set(privateProfileRef(userId), privateData, SetOptions.merge())
            // Create a lookup entry where ID is the email (prevents listing all emails)
            batch.set(lookupRef.document(email.lowercase()), mapOf("uid" to userId))
        }.await()
    }

    suspend fun getPublicKey(userId: String): String? {
        val doc = usersRef.document(userId).get().await()
        return doc.getString(FIELD_PUBLIC_KEY)
    }

    suspend fun getUser(userId: String): User? {
        val doc = usersRef.document(userId).get().await()
        if (!doc.exists()) return null
        val publicKey = doc.getString(FIELD_PUBLIC_KEY) ?: return null
        return User(
            userId = doc.id,
            publicKey = publicKey,
            displayName = doc.getString(FIELD_DISPLAY_NAME),
            createdAt = doc.getLong(FIELD_CREATED_AT),
            isOnline = doc.getBoolean("isOnline") ?: false,
            lastSeen = doc.getLong("lastSeen") ?: 0L
        )
    }

    suspend fun getPrivateProfile(userId: String): com.cryptotalk.app.data.model.PrivateProfile? {
        val doc = privateProfileRef(userId).get().await()
        val email = doc.getString(FIELD_EMAIL) ?: return null
        return com.cryptotalk.app.data.model.PrivateProfile(
            email = email,
            deviceId = doc.getString("deviceId"),
            fcmToken = doc.getString(FIELD_FCM_TOKEN),
            blockedUsers = (doc.get(FIELD_BLOCKED_USERS) as? List<*>)?.map { it.toString() }.orEmpty()
        )
    }

    suspend fun getUserByEmail(email: String): User? {
        val lookup = lookupRef.document(email.lowercase()).get().await()
        val uid = lookup.getString("uid") ?: return null
        return getUser(uid)
    }

    suspend fun blockUser(currentUserId: String, targetUserId: String): Result<Unit> = runCatching {
        val doc = privateProfileRef(currentUserId).get().await()
        val blockedList = (doc.get(FIELD_BLOCKED_USERS) as? List<*>)?.map { it.toString() }.orEmpty().toMutableList()
        if (!blockedList.contains(targetUserId)) {
            blockedList.add(targetUserId)
            privateProfileRef(currentUserId).update(FIELD_BLOCKED_USERS, blockedList).await()
        }
    }

    suspend fun unblockUser(currentUserId: String, targetUserId: String): Result<Unit> = runCatching {
        val doc = privateProfileRef(currentUserId).get().await()
        val blockedList = (doc.get(FIELD_BLOCKED_USERS) as? List<*>)?.map { it.toString() }.orEmpty().toMutableList()
        if (blockedList.contains(targetUserId)) {
            blockedList.remove(targetUserId)
            privateProfileRef(currentUserId).update(FIELD_BLOCKED_USERS, blockedList).await()
        }
    }

    fun hasLocalKeyPair(userId: String): Boolean {
        return keystoreHelper.hasKeyPair(userId)
    }

    fun getPrivateKey(userId: String) = keystoreHelper.getPrivateKey(userId)
    
    fun exportPrivateKeyBytes(userId: String) = keystoreHelper.exportPrivateKeyBytes(userId)
    
    fun importRsaKeyPair(userId: String, publicKeyBytes: ByteArray, privateKeyBytes: ByteArray) = 
        keystoreHelper.importRsaKeyPair(userId, publicKeyBytes, privateKeyBytes)

    fun getPublicKeyForEncryption(userId: String) = keystoreHelper.getPublicKey(userId)

    fun getLocalPublicKeyBase64(userId: String): String? {
        val publicKey = keystoreHelper.getPublicKey(userId) ?: return null
        return KeySerializer.publicKeyToBase64(publicKey)
    }

    suspend fun backupEncryptedKey(userId: String, encryptedPayload: String): Result<Unit> = runCatching {
        privateProfileRef(userId).update("encryptedKeyBackup", encryptedPayload).await()
    }

    suspend fun getEncryptedKeyBackup(userId: String): String? {
        val doc = privateProfileRef(userId).get().await()
        return doc.getString("encryptedKeyBackup")
    }

    suspend fun updateDisplayName(userId: String, displayName: String): Result<Unit> = runCatching {
        usersRef.document(userId).update(FIELD_DISPLAY_NAME, displayName).await()
    }

    suspend fun saveFcmToken(userId: String, token: String): Result<Unit> = runCatching {
        privateProfileRef(userId).set(mapOf(FIELD_FCM_TOKEN to token), SetOptions.merge()).await()
    }

    suspend fun getFcmTokens(userIds: List<String>): List<String> {
        val tokens = mutableListOf<String>()
        for (uid in userIds) {
            val doc = privateProfileRef(uid).get().await()
            doc.getString(FIELD_FCM_TOKEN)?.let { tokens.add(it) }
        }
        return tokens
    }

    /**
     * PROMPT 6: Set online/offline status and update lastSeen timestamp.
     */
    suspend fun setOnlineStatus(userId: String, isOnline: Boolean): Result<Unit> = runCatching {
        val data = mapOf(
            "isOnline" to isOnline,
            "lastSeen" to System.currentTimeMillis()
        )
        usersRef.document(userId).update(data).await()
    }

    suspend fun updateLastSeen(userId: String) = runCatching {
        setOnlineStatus(userId, false)
    }

    suspend fun getLastSeen(userId: String): Long? {
        val doc = usersRef.document(userId).get().await()
        return doc.getLong("lastSeen")
    }

    /**
     * PROMPT 6: Real-time flow of the other user's online status and last seen timestamp.
     * Emits Pair(isOnline, lastSeenMillis)
     */
    fun getUserStatusFlow(userId: String): kotlinx.coroutines.flow.Flow<Pair<Boolean, Long?>> = kotlinx.coroutines.flow.callbackFlow {
        val listener = usersRef.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            val isOnline = snapshot?.getBoolean("isOnline") ?: false
            val lastSeen = snapshot?.getLong("lastSeen")
            trySend(isOnline to lastSeen)
        }
        awaitClose { listener.remove() }
    }

    fun getUserFlow(userId: String): kotlinx.coroutines.flow.Flow<User?> = kotlinx.coroutines.flow.callbackFlow {
        val listener = usersRef.document(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val publicKey = snapshot.getString(FIELD_PUBLIC_KEY)
                if (publicKey != null) {
                    val user = User(
                        userId = snapshot.id,
                        publicKey = publicKey,
                        displayName = snapshot.getString(FIELD_DISPLAY_NAME),
                        createdAt = snapshot.getLong(FIELD_CREATED_AT),
                        isOnline = snapshot.getBoolean("isOnline") ?: false,
                        lastSeen = snapshot.getLong("lastSeen") ?: 0L
                    )
                    trySend(user)
                } else {
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    fun getPrivateProfileFlow(userId: String): kotlinx.coroutines.flow.Flow<com.cryptotalk.app.data.model.PrivateProfile?> = kotlinx.coroutines.flow.callbackFlow {
        val listener = privateProfileRef(userId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val email = snapshot.getString(FIELD_EMAIL)
                if (email != null) {
                    val profile = com.cryptotalk.app.data.model.PrivateProfile(
                        email = email,
                        deviceId = snapshot.getString("deviceId"),
                        fcmToken = snapshot.getString(FIELD_FCM_TOKEN),
                        blockedUsers = (snapshot.get(FIELD_BLOCKED_USERS) as? List<*>)?.map { it.toString() }.orEmpty()
                    )
                    trySend(profile)
                } else {
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PUBLIC_KEY = "publicKey"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_BLOCKED_USERS = "blockedUsers"
        private const val FIELD_FCM_TOKEN = "fcmToken"
        private const val COLLECTION_LOOKUP = "user_lookup"
    }
}
