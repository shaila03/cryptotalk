package com.cryptotalk.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.cryptotalk.app.MainActivity
import com.cryptotalk.app.CryptoTalkApplication
import com.cryptotalk.app.R

/**
 * NotificationHelper is responsible for actually displaying the popup notification on the phone.
 * It respects user privacy settings (e.g., hiding message content if requested).
 */
object NotificationHelper {

    private const val CHANNEL_ID = "cryptotalk_messages"
    private const val CHANNEL_NAME = "New Messages"
    private const val CHANNEL_DESC = "Notifications for new CryptoTalk messages"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH // Changed back to HIGH to ensure "popup"
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    suspend fun showMessageNotification(context: Context, senderName: String, messagePreview: String, notificationId: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val app = context.applicationContext as CryptoTalkApplication
        val hideContent = try { app.settingsRepository.isHideNotificationContentEnabled() } catch(e: Exception) { false }
        
        val displayTitle = if (hideContent) "New Message" else senderName
        val displayContent = if (hideContent) "Open app to read" else messagePreview

        val largeIcon = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_app_logo)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo) // Using app logo
            .setLargeIcon(largeIcon) // Added for popup icon requirement
            .setContentTitle(displayTitle)
            .setContentText(displayContent)
            .setStyle(if (hideContent) null else NotificationCompat.BigTextStyle().bigText(messagePreview))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Changed back to HIGH to ensure "popup"
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(if (hideContent) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
