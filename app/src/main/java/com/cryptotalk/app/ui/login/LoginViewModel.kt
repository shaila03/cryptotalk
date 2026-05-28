package com.cryptotalk.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.navigation.NavRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateTo: String? = null,
    val showRestoreDialog: Boolean = false,
    val uid: String? = null,
    val emailVal: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun updateEmail(email: String) {
        _state.value = _state.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }

    fun signIn() {
        val email = _state.value.email.trim()
        val password = _state.value.password.trim()
        
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter email and password")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authRepository.signIn(email, password)
                .onSuccess { signInResult ->
                    if (signInResult.needsKeyRestore) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            showRestoreDialog = true,
                            uid = signInResult.user.uid,
                            emailVal = signInResult.user.email
                        )
                    } else {
                        userRepository.ensureKeyPairAndUploadPublicKey(
                            signInResult.user.uid,
                            signInResult.user.email ?: ""
                        ).onSuccess {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                navigateTo = if (signInResult.keyWasReset) NavRoutes.CONVERSATION_LIST + "?keyReset=true" else NavRoutes.CONVERSATION_LIST
                            )
                        }.onFailure {
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = "Security setup failed. Please try again."
                            )
                        }
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Login failed"
                    )
                }
        }
    }

    fun restoreKey(pin: String) {
        val uid = _state.value.uid ?: return
        val email = _state.value.emailVal ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val backup = userRepository.getEncryptedKeyBackup(uid)
            if (backup != null) {
                try {
                    val privBytes = com.cryptotalk.app.crypto.KeyRecoveryHelper.decryptPrivateKeyBackup(backup, pin)
                    // The public key bytes need to be reconstructed.
                    // We can just fetch the public key from Firestore.
                    val pubBase64 = userRepository.getPublicKey(uid)
                    if (pubBase64 != null) {
                        val pubBytes = android.util.Base64.decode(pubBase64, android.util.Base64.NO_WRAP)
                        userRepository.importRsaKeyPair(uid, pubBytes, privBytes)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            showRestoreDialog = false,
                            navigateTo = NavRoutes.CONVERSATION_LIST
                        )
                        return@launch
                    }
                } catch (e: Exception) {
                    _state.value = _state.value.copy(isLoading = false, error = "Incorrect Recovery PIN or Corrupted Backup.")
                    return@launch
                }
            }
            _state.value = _state.value.copy(isLoading = false, error = "Backup not found.")
        }
    }

    fun skipRestore() {
        val uid = _state.value.uid ?: return
        val email = _state.value.emailVal ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, showRestoreDialog = false)
            userRepository.ensureKeyPairAndUploadPublicKey(uid, email)
            _state.value = _state.value.copy(
                isLoading = false,
                navigateTo = NavRoutes.CONVERSATION_LIST + "?keyReset=true"
            )
        }
    }

    fun clearNavigation() {
        _state.value = _state.value.copy(navigateTo = null)
    }
}
