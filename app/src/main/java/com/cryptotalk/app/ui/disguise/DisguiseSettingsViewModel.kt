package com.cryptotalk.app.ui.disguise

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.disguise.DisguiseManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DisguiseSettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val disguiseMode: StateFlow<String> = settingsRepository.disguiseMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "NONE")

    val unlockCode: StateFlow<String> = settingsRepository.disguiseUnlockCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0000")

    fun setDisguiseMode(mode: DisguiseManager.DisguiseMode, context: Context) {
        viewModelScope.launch {
            settingsRepository.setDisguiseMode(mode.name)
            DisguiseManager(context).applyDisguise(mode)
        }
    }

    fun setUnlockCode(code: String) {
        viewModelScope.launch {
            settingsRepository.setDisguiseUnlockCode(code)
        }
    }

    class Factory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DisguiseSettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return DisguiseSettingsViewModel(settingsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
