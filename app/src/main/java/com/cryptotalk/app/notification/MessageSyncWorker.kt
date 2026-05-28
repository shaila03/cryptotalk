package com.cryptotalk.app.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotalk.app.CryptoTalkApplication
import com.cryptotalk.app.data.model.Message
import com.cryptotalk.app.data.repository.ConversationRepository
import com.cryptotalk.app.data.repository.MessageRepository
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.crypto.KeySerializer
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * MessageSyncWorker is a background job that occasionally checks for new messages
 * even when the app is closed. This acts as a backup in case Push Notifications fail.
 */
class MessageSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CryptoTalkApplication
        val auth = app.authRepository
        val settings = app.settingsRepository
        val conversationRepo = app.conversationRepository
        val messageRepo = app.messageRepository

        val currentUserId = auth.currentUser?.uid ?: return Result.success()
        val privateKey = app.userRepository.getPrivateKey(currentUserId) ?: return Result.success()

        val lastSyncTime = applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            .getLong("last_sync_${currentUserId}", System.currentTimeMillis() - 60000) // Default to 1 min ago if first time

        Log.d("MessageSyncWorker", "Checking for new messages since $lastSyncTime")

        val conversations = conversationRepo.getActiveConversations(currentUserId)
        
        var newMessageCount = 0

        for (conv in conversations) {
            if (conv.lastMessageAt > lastSyncTime) {
                // Potential new messages
                try {
                    val messagesSnapshot = FirebaseFirestore.getInstance()
                        .collection("conversations")
                        .document(conv.id)
                        .collection("messages")
                        .whereGreaterThan("timestamp", lastSyncTime)
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(5)
                        .get()
                        .await()

                    for (doc in messagesSnapshot.documents) {
                        val senderId = doc.getString("senderId") ?: continue
                        if (senderId == currentUserId) continue // Ignore own messages

                        val timestamp = doc.getLong("timestamp") ?: 0L
                        val senderName = doc.getString("senderName") ?: "Someone"
                        
                        // We show notification
                        // In a real app, we'd decrypt the message preview if possible, 
                        // but for background sync, showing "New Message" is safer and easier.
                        NotificationHelper.showMessageNotification(
                            applicationContext,
                            senderName,
                            "You have a new message",
                            doc.id.hashCode()
                        )
                        newMessageCount++
                    }
                } catch (e: Exception) {
                    Log.e("MessageSyncWorker", "Error syncing conv ${conv.id}", e)
                }
            }
        }

        applicationContext.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_${currentUserId}", System.currentTimeMillis())
            .apply()

        return Result.success()
    }
}
