package com.cryptotalk.app.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.navigation.NavRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplashViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination.asStateFlow()

    fun checkAuth() {
        viewModelScope.launch {
            val user = authRepository.currentUser
            if (user == null) {
                _destination.value = NavRoutes.LOGIN
                return@launch
            }
            if (!userRepository.hasLocalKeyPair(user.uid)) {
                val email = user.email ?: ""
                val displayName = user.displayName ?: email
                userRepository.ensureKeyPairAndUploadPublicKey(user.uid, email, displayName)
                    .onFailure {
                        _destination.value = NavRoutes.LOGIN
                        return@launch
                    }
            }
            _destination.value = NavRoutes.CONVERSATION_LIST
        }
    }
}
