package com.cryptotalk.app.notification

import android.util.Log
import com.cryptotalk.app.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * CryptoMessagingService listens for Push Notifications from Firebase (like when a new message arrives).
 * It runs in the background and wakes up the app to show a notification.
 */
class CryptoMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        val senderName = message.data["senderName"] ?: "New Message"
        val text = message.data["text"] ?: "You have a new message"
        
        CoroutineScope(Dispatchers.Main).launch {
            NotificationHelper.showMessageNotification(
                applicationContext,
                senderName,
                text,
                System.currentTimeMillis().toInt()
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure we use the common repository
                UserRepository(applicationContext).saveFcmToken(userId, token)
            } catch (e: Exception) {
                // Log failure locally
            }
        }
    }
}
