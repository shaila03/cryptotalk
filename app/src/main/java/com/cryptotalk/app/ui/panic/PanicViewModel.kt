package com.cryptotalk.app.ui.panic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.security.SecurityWipeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PanicViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _pin = MutableStateFlow("")
    val pin: StateFlow<String> = _pin.asStateFlow()

    private val _isSetupMode = MutableStateFlow(false)
    val isSetupMode: StateFlow<Boolean> = _isSetupMode.asStateFlow()

    fun setSetupMode(enabled: Boolean) {
        _isSetupMode.value = enabled
    }

    fun onPinChange(newPin: String) {
        if (newPin.length <= 4) {
            _pin.value = newPin
            if (newPin.length == 4) {
                verifyOrSavePin()
            }
        }
    }

    private fun verifyOrSavePin() {
        viewModelScope.launch {
            val enteredPin = _pin.value
            if (_isSetupMode.value) {
                settingsRepository.setPanicPin(enteredPin)
                _pin.value = "" // Reset for UI
                // In a real app, we'd navigate back here
            } else {
                val savedPanicPin = settingsRepository.panicPin.first()
                if (enteredPin == savedPanicPin) {
                    // TRIGGER PANIC!
                    SecurityWipeManager(settingsRepository.context).triggerEmergencyWipe()
                } else {
                    _pin.value = "" // Reset on wrong PIN
                }
            }
        }
    }
}
