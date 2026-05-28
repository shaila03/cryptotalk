package com.cryptotalk.app

import android.app.Application
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.ConversationRepository
import com.cryptotalk.app.data.repository.MessageRepository
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.data.repository.UserRepository

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.cryptotalk.app.notification.MessageSyncWorker
import java.util.concurrent.TimeUnit

/**
 * Main Application class for CryptoTalk.
 * This class initializes global repositories and manages the background sync worker.
 */
class CryptoTalkApplication : Application() {

    // Repositories are data managers that handle different parts of the app (Auth, Users, Messages, etc.)
    // We use 'lazy' so they are only created when they are actually needed.
    val authRepository: AuthRepository by lazy { AuthRepository(this) }
    val userRepository: UserRepository by lazy { UserRepository(this) }
    val conversationRepository: ConversationRepository by lazy { ConversationRepository() }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val messageRepository: MessageRepository by lazy { MessageRepository(settingsRepository) }
    val groupRepository: com.cryptotalk.app.data.repository.GroupRepository by lazy {
        com.cryptotalk.app.data.repository.GroupRepository(userRepository)
    }
    
    // This is our encrypted database where messages are stored safely.
    // It is null until the user logs in and provides the decryption key.
    var secureDatabase: com.cryptotalk.app.data.local.SecureDatabase? = null
        private set

    /**
     * Sets up the secure database using the user's passphrase.
     */
    fun initializeSecureDatabase(passphrase: ByteArray) {
        secureDatabase = com.cryptotalk.app.data.local.SecureDatabase.getInstance(this, passphrase)
    }

    override fun onCreate() {
        super.onCreate()
        // Start the background worker that checks for new messages.
        scheduleMessageSync()
    }

    /**
     * Sets up a background task to sync messages every 15 minutes.
     * This ensures the app stays up-to-date even when not actively open.
     */
    private fun scheduleMessageSync() {
        // Only sync if there is an internet connection.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create a request to run the MessageSyncWorker every 15 minutes.
        val syncRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        // Register the task with Android's WorkManager.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "message_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}

