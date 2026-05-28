package com.cryptotalk.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.data.repository.AuthRepository

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val encryptionEnabled: StateFlow<Boolean> = settingsRepository.encryptionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val biometricEnabled: StateFlow<Boolean> = settingsRepository.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val screenshotProtectionEnabled: StateFlow<Boolean> = settingsRepository.screenshotProtectionEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hideNotificationContent: StateFlow<Boolean> = settingsRepository.hideNotificationContent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val readReceiptsEnabled: StateFlow<Boolean> = settingsRepository.readReceiptsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val typingIndicatorEnabled: StateFlow<Boolean> = settingsRepository.typingIndicatorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val volumeExitEnabled: StateFlow<Boolean> = settingsRepository.volumeExitEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val performanceModeEnabled: StateFlow<Boolean> = settingsRepository.performanceModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val disguiseMode: StateFlow<String> = settingsRepository.disguiseMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NONE")

    val isDecoySession: StateFlow<Boolean> = settingsRepository.isDecoySession
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEncryptionEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    }

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setScreenshotProtectionEnabled(enabled) }
    }

    fun setHideNotificationContent(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideNotificationContent(enabled) }
    }

    fun triggerPanicMode() {
        com.cryptotalk.app.security.SecurityWipeManager(settingsRepository.context).triggerEmergencyWipe()
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setReadReceiptsEnabled(enabled) }
    }

    fun setTypingIndicatorEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTypingIndicatorEnabled(enabled) }
    }

    fun setVolumeExitEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVolumeExitEnabled(enabled) }
    }

    fun setPerformanceModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPerformanceModeEnabled(enabled) }
    }

    // PINs are now Flows in the repository too
    val appPin: StateFlow<String?> = settingsRepository.appPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        


    fun setAppPin(pin: String?) {
        viewModelScope.launch { settingsRepository.setAppPin(pin) }
    }



    fun clearAllData() {
        viewModelScope.launch { settingsRepository.clearAllData() }
    }

    private val _backupStatus = MutableStateFlow<String?>(null)
    val backupStatus: StateFlow<String?> = _backupStatus

    fun clearBackupStatus() {
        _backupStatus.value = null
    }

    fun backupEncryptionKey(pin: String) {
        viewModelScope.launch {
            _backupStatus.value = "Backing up..."
            val user = authRepository.currentUser
            if (user == null) {
                _backupStatus.value = "Error: Not logged in."
                return@launch
            }
            
            val privBytes = userRepository.exportPrivateKeyBytes(user.uid)
            if (privBytes == null) {
                _backupStatus.value = "Error: Private key not found locally."
                return@launch
            }
            
            try {
                val payload = com.cryptotalk.app.crypto.KeyRecoveryHelper.encryptPrivateKeyBackup(privBytes, pin)
                userRepository.backupEncryptedKey(user.uid, payload).onSuccess {
                    _backupStatus.value = "Success: Key backed up securely!"
                }.onFailure {
                    _backupStatus.value = "Error: Failed to upload backup."
                }
            } catch (e: Exception) {
                _backupStatus.value = "Error: Encryption failed."
            }
        }
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val userRepository: UserRepository,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, userRepository, authRepository) as T
        }
    }
}
