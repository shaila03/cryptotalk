package com.cryptotalk.app.data.repository

import android.content.Context
import com.cryptotalk.app.data.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * SettingsRepository acts as the main access point for reading and changing app settings.
 * It talks to the SettingsDataStore to actually save the data securely.
 */
class SettingsRepository(val context: android.content.Context) {

    private val dataStore = SettingsDataStore(context)

    val encryptionEnabled: Flow<Boolean> = dataStore.encryptionEnabled
    val biometricEnabled: Flow<Boolean> = dataStore.biometricEnabled
    val screenshotProtectionEnabled: Flow<Boolean> = dataStore.screenshotProtection
    val hideNotificationContent: Flow<Boolean> = dataStore.hideNotificationContent
    val readReceiptsEnabled: Flow<Boolean> = dataStore.readReceiptsEnabled
    val typingIndicatorEnabled: Flow<Boolean> = dataStore.typingIndicatorEnabled
    val disguiseMode: Flow<String> = dataStore.disguiseMode
    val disguiseUnlockCode: Flow<String> = dataStore.disguiseUnlockCode
    val chatWallpaper: Flow<String?> = dataStore.chatWallpaper
    val volumeExitEnabled: Flow<Boolean> = dataStore.volumeExitEnabled
    val performanceModeEnabled: Flow<Boolean> = dataStore.performanceModeEnabled
    
    // Decoy session is transient, keeping it as a StateFlow
    private val _isDecoySession = MutableStateFlow(false)
    val isDecoySession: StateFlow<Boolean> = _isDecoySession.asStateFlow()

    suspend fun isEncryptionEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(encryptionEnabled, true)
    
    suspend fun isBiometricEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(biometricEnabled, false)

    suspend fun isScreenshotProtectionEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(screenshotProtectionEnabled, false)

    suspend fun isHideNotificationContentEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(hideNotificationContent, false)

    suspend fun isReadReceiptsEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(readReceiptsEnabled, true)

    suspend fun isTypingIndicatorEnabled(): Boolean = 
        com.cryptotalk.app.util.FlowUtils.getValue(typingIndicatorEnabled, true)

    suspend fun setEncryptionEnabled(enabled: Boolean) = dataStore.setEncryptionEnabled(enabled)
    suspend fun setBiometricEnabled(enabled: Boolean) = dataStore.setBiometricEnabled(enabled)
    suspend fun setScreenshotProtectionEnabled(enabled: Boolean) = dataStore.setScreenshotProtection(enabled)
    suspend fun setHideNotificationContent(enabled: Boolean) = dataStore.setHideNotificationContent(enabled)
    suspend fun setReadReceiptsEnabled(enabled: Boolean) = dataStore.setReadReceiptsEnabled(enabled)
    suspend fun setTypingIndicatorEnabled(enabled: Boolean) = dataStore.setTypingIndicatorEnabled(enabled)
    suspend fun setVolumeExitEnabled(enabled: Boolean) = dataStore.setVolumeExitEnabled(enabled)
    suspend fun setPerformanceModeEnabled(enabled: Boolean) = dataStore.setPerformanceModeEnabled(enabled)

    val appPin: Flow<String?> = dataStore.appPin
    suspend fun setAppPin(pin: String?) = dataStore.setAppPin(pin)

    val decoyPin: Flow<String?> = dataStore.decoyPin
    suspend fun setDecoyPin(pin: String?) = dataStore.setDecoyPin(pin)

    val panicPin: Flow<String?> = dataStore.panicPin
    suspend fun setPanicPin(pin: String?) = dataStore.setPanicPin(pin)

    suspend fun getAppPin(): String? = appPin.first()
    suspend fun getDecoyPin(): String? = decoyPin.first()

    suspend fun setDisguiseMode(mode: String) = dataStore.setDisguiseMode(mode)
    suspend fun setDisguiseUnlockCode(code: String) = dataStore.setDisguiseUnlockCode(code)
    suspend fun setChatWallpaper(wallpaper: String?) = dataStore.setChatWallpaper(wallpaper)

    fun setDecoySession(isDecoy: Boolean) {
        _isDecoySession.value = isDecoy
    }

    fun getContactNameFlow(userId: String): Flow<String?> = dataStore.getContactName(userId)

    suspend fun getContactName(userId: String): String? = dataStore.getContactName(userId).first()

    suspend fun setContactName(userId: String, name: String?) = dataStore.setContactName(userId, name)

    suspend fun clearAllData() = dataStore.clearAll()
}
