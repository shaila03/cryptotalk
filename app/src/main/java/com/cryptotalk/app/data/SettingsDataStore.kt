package com.cryptotalk.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * SettingsDataStore handles saving and loading user preferences (like turning on/off biometric login).
 * It uses Android's DataStore to save these settings efficiently.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
        val HIDE_NOTIFICATION_CONTENT = booleanPreferencesKey("hide_notification_content")
        val READ_RECEIPTS_ENABLED = booleanPreferencesKey("read_receipts_enabled")
        val TYPING_INDICATOR_ENABLED = booleanPreferencesKey("typing_indicator_enabled")
        val APP_PIN = stringPreferencesKey("app_pin")
        val DECOY_PIN = stringPreferencesKey("decoy_pin")
        val PANIC_PIN = stringPreferencesKey("panic_pin")
        val DISGUISE_MODE = stringPreferencesKey("disguise_mode")
        val DISGUISE_UNLOCK_CODE = stringPreferencesKey("disguise_unlock_code")
        val CHAT_WALLPAPER = stringPreferencesKey("chat_wallpaper")
        val VOLUME_EXIT_ENABLED = booleanPreferencesKey("volume_exit_enabled")
        val PERFORMANCE_MODE_ENABLED = booleanPreferencesKey("performance_mode_enabled")
    }

    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { it[ENCRYPTION_ENABLED] ?: true }
    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[BIOMETRIC_ENABLED] ?: false }
    val screenshotProtection: Flow<Boolean> = context.dataStore.data.map { it[SCREENSHOT_PROTECTION] ?: false }
    val hideNotificationContent: Flow<Boolean> = context.dataStore.data.map { it[HIDE_NOTIFICATION_CONTENT] ?: false }
    val readReceiptsEnabled: Flow<Boolean> = context.dataStore.data.map { it[READ_RECEIPTS_ENABLED] ?: true }
    val typingIndicatorEnabled: Flow<Boolean> = context.dataStore.data.map { it[TYPING_INDICATOR_ENABLED] ?: true }
    val appPin: Flow<String?> = context.dataStore.data.map { it[APP_PIN] }
    val decoyPin: Flow<String?> = context.dataStore.data.map { it[DECOY_PIN] }
    val panicPin: Flow<String?> = context.dataStore.data.map { it[PANIC_PIN] }
    val disguiseMode: Flow<String> = context.dataStore.data.map { it[DISGUISE_MODE] ?: "NONE" }
    val disguiseUnlockCode: Flow<String> = context.dataStore.data.map { it[DISGUISE_UNLOCK_CODE] ?: "0000" }
    val chatWallpaper: Flow<String?> = context.dataStore.data.map { it[CHAT_WALLPAPER] }
    val volumeExitEnabled: Flow<Boolean> = context.dataStore.data.map { it[VOLUME_EXIT_ENABLED] ?: false }
    val performanceModeEnabled: Flow<Boolean> = context.dataStore.data.map { it[PERFORMANCE_MODE_ENABLED] ?: false }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ENCRYPTION_ENABLED] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setScreenshotProtection(enabled: Boolean) {
        context.dataStore.edit { it[SCREENSHOT_PROTECTION] = enabled }
    }

    suspend fun setHideNotificationContent(enabled: Boolean) {
        context.dataStore.edit { it[HIDE_NOTIFICATION_CONTENT] = enabled }
    }

    suspend fun setReadReceiptsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[READ_RECEIPTS_ENABLED] = enabled }
    }

    suspend fun setTypingIndicatorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TYPING_INDICATOR_ENABLED] = enabled }
    }

    suspend fun setAppPin(pin: String?) {
        context.dataStore.edit { 
            if (pin == null) it.remove(APP_PIN) else it[APP_PIN] = pin 
        }
    }

    suspend fun setDecoyPin(pin: String?) {
        context.dataStore.edit { 
            if (pin == null) it.remove(DECOY_PIN) else it[DECOY_PIN] = pin 
        }
    }

    suspend fun setPanicPin(pin: String?) {
        context.dataStore.edit { 
            if (pin == null) it.remove(PANIC_PIN) else it[PANIC_PIN] = pin 
        }
    }

    suspend fun setDisguiseMode(mode: String) {
        context.dataStore.edit { it[DISGUISE_MODE] = mode }
    }

    suspend fun setDisguiseUnlockCode(code: String) {
        context.dataStore.edit { it[DISGUISE_UNLOCK_CODE] = code }
    }

    suspend fun setChatWallpaper(wallpaper: String?) {
        context.dataStore.edit {
            if (wallpaper == null) it.remove(CHAT_WALLPAPER) else it[CHAT_WALLPAPER] = wallpaper
        }
    }

    suspend fun setVolumeExitEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VOLUME_EXIT_ENABLED] = enabled }
    }

    suspend fun setPerformanceModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PERFORMANCE_MODE_ENABLED] = enabled }
    }

    fun getContactName(userId: String): Flow<String?> = context.dataStore.data.map { 
        it[stringPreferencesKey("contact_name_$userId")] 
    }

    suspend fun setContactName(userId: String, name: String?) {
        context.dataStore.edit { 
            val key = stringPreferencesKey("contact_name_$userId")
            if (name == null) it.remove(key) else it[key] = name 
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
