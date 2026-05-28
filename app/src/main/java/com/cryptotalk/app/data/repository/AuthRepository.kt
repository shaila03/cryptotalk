package com.cryptotalk.app.data.repository

import android.app.Activity
import android.util.Log
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.security.SecurityWipeManager
import com.cryptotalk.app.util.DeviceIdHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository manages user login, registration, and session state.
 * It also handles emergency data wipes if the panic pin is entered, and ensures
 * that keys are restored or regenerated securely on login.
 */
class AuthRepository(private val context: android.content.Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPrefs = context.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /**
     * Result wrapper that carries whether the RSA key was regenerated (key-loss scenario)
     * or if there's an encrypted backup waiting to be restored with a PIN.
     */
    data class SignInResult(val user: FirebaseUser, val keyWasReset: Boolean = false, val needsKeyRestore: Boolean = false)

    suspend fun signIn(email: String, password: String): Result<SignInResult> = runCatching {
        // Security: Self-destruct PIN detection
        if (password == SECRET_WIPE_CODE) {
            SecurityWipeManager(context).triggerEmergencyWipe()
            throw Exception("Emergency Wipe Triggered")
        }
        
        try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user!!
            resetFailedAttempts()
            checkDeviceBinding(user.uid)
            // Verify if local Keystore is intact. If keys are missing (e.g. app data cleared),
            // check if there's a backup. If so, prompt PIN restore. If not, regenerate.
            val userRepo = UserRepository(context)
            var keyWasReset = false
            var needsKeyRestore = false
            if (!userRepo.hasLocalKeyPair(user.uid)) {
                try {
                    val backup = userRepo.getEncryptedKeyBackup(user.uid)
                    if (backup != null) {
                        needsKeyRestore = true
                        keyWasReset = true
                    } else {
                        userRepo.ensureKeyPairAndUploadPublicKey(user.uid, user.email ?: "")
                        keyWasReset = true
                    }
                } catch (e: Exception) {
                    Log.w("AuthRepository", "Key check failed: ${e.message}")
                }
            }
            
            // Register for Push Notifications
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                userRepo.saveFcmToken(user.uid, token)
            } catch (e: Exception) {
                Log.w("AuthRepository", "FCM token save failed: ${e.message}")
            }
            SignInResult(user, keyWasReset, needsKeyRestore)
        } catch (e: Exception) {
            incrementFailedAttempts()
            throw e
        }
    }

    suspend fun signUp(email: String, password: String): Result<SignInResult> = runCatching {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user!!
        val userRepo = UserRepository(context)

        // PROMPT 6 FIX: Ensure the user's public profile and lookup entry are created on signup.
        // This is critical for search and for other users to see this user.
        userRepo.ensureKeyPairAndUploadPublicKey(user.uid, email).getOrThrow()
        
        bindDevice(user.uid)
        
        // Register for Push Notifications
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            userRepo.saveFcmToken(user.uid, token)
        } catch (e: Exception) {
            Log.w("AuthRepository", "FCM token save failed: ${e.message}")
        }
        SignInResult(user, keyWasReset = false, needsKeyRestore = false)
    }

    private suspend fun checkDeviceBinding(userId: String) {
        val currentDeviceId = DeviceIdHelper.getDeviceId(context)
        val doc = firestore.collection("users").document(userId).collection("private").document("profile").get().await()
        val boundDeviceId = doc.getString("deviceId")
        
        if (boundDeviceId != null && boundDeviceId != currentDeviceId) {
            throw Exception("Device binding error: This account is bound to another device.")
        } else if (boundDeviceId == null) {
            bindDevice(userId)
        }
    }

    private suspend fun bindDevice(userId: String) {
        val currentDeviceId = DeviceIdHelper.getDeviceId(context)
        firestore.collection("users").document(userId).collection("private").document("profile").set(
            mapOf("deviceId" to currentDeviceId), SetOptions.merge()
        ).await()
    }

    private fun incrementFailedAttempts() {
        val attempts = sharedPrefs.getInt("failed_attempts", 0) + 1
        sharedPrefs.edit().putInt("failed_attempts", attempts).apply()
        if (attempts >= 10) {
            SecurityWipeManager(context).triggerEmergencyWipe()
        }
    }

    private fun resetFailedAttempts() {
        sharedPrefs.edit().putInt("failed_attempts", 0).apply()
    }

    fun signOut() {
        // Mark user offline on sign-out
        val uid = auth.currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update(mapOf("isOnline" to false, "lastSeen" to System.currentTimeMillis()))
        }
        auth.signOut()
    }

    companion object {
        private const val SECRET_WIPE_CODE = "999999" // Example wipe code
    }
}
