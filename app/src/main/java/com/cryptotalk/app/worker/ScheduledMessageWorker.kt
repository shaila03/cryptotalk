package com.cryptotalk.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotalk.app.data.repository.MessageRepository
import com.cryptotalk.app.data.repository.SettingsRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ScheduledMessageWorker handles sending a message at a later time (e.g., "Send this tomorrow at 9 AM").
 * It runs in the background and sends the message exactly when you asked it to.
 */
class ScheduledMessageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val conversationId = inputData.getString(KEY_CONV_ID) ?: return@withContext Result.failure()
        val senderId = inputData.getString(KEY_SENDER_ID) ?: return@withContext Result.failure()
        val plaintext = inputData.getString(KEY_PLAINTEXT) ?: return@withContext Result.failure()
        val participantKeysJson = inputData.getString(KEY_KEYS) ?: "{}"
        
        val replyToId = inputData.getString(KEY_REPLY_ID)
        val replyToText = inputData.getString(KEY_REPLY_TEXT)
        val selfDestruct = inputData.getBoolean(KEY_SELF_DESTRUCT, false)
        val destructAfterMs = inputData.getLong(KEY_DESTRUCT_MS, 0L)

        val keysType = object : TypeToken<Map<String, String>>() {}.type
        val participantKeys: Map<String, String> = Gson().fromJson(participantKeysJson, keysType)

        val settingsRepo = SettingsRepository(applicationContext)
        val messageRepo = MessageRepository(settingsRepo)

        try {
            messageRepo.sendMessage(
                conversationId = conversationId,
                senderId = senderId,
                participantPublicKeys = participantKeys,
                plaintext = plaintext,
                replyToId = replyToId,
                replyToText = replyToText,
                selfDestruct = selfDestruct,
                destructAfterMs = destructAfterMs,
                scheduledAt = null // Crucial: Set null, so it sends it for real instead of rescheduling!
            )
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // If it's a network issue or something retryable
            Result.retry()
        }
    }

    companion object {
        const val KEY_CONV_ID = "conv_id"
        const val KEY_SENDER_ID = "sender_id"
        const val KEY_PLAINTEXT = "plaintext"
        const val KEY_KEYS = "participant_keys"
        const val KEY_REPLY_ID = "reply_id"
        const val KEY_REPLY_TEXT = "reply_text"
        const val KEY_SELF_DESTRUCT = "self_destruct"
        const val KEY_DESTRUCT_MS = "destruct_ms"
    }
}
