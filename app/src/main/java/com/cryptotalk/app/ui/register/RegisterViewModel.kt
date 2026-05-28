package com.cryptotalk.app.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.navigation.NavRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: Float = 0f, // 0.0 to 1.0
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateTo: String? = null
)

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun updateDisplayName(name: String) {
        _state.value = _state.value.copy(displayName = name, error = null)
    }

    fun updateEmail(email: String) {
        _state.value = _state.value.copy(email = email, error = null)
    }

    fun updatePassword(password: String) {
        val strength = calculatePasswordStrength(password)
        _state.value = _state.value.copy(password = password, passwordStrength = strength, error = null)
    }

    private fun calculatePasswordStrength(password: String): Float {
        if (password.isEmpty()) return 0f
        var points = 0f
        if (password.length >= 8) points += 0.25f
        if (password.any { it.isDigit() }) points += 0.25f
        if (password.any { it.isUpperCase() }) points += 0.25f
        if (password.any { !it.isLetterOrDigit() }) points += 0.25f
        return points
    }

    fun updateConfirmPassword(password: String) {
        _state.value = _state.value.copy(confirmPassword = password, error = null)
    }

    fun signUp() {
        val s = _state.value
        val email = s.email.trim()
        val displayName = s.displayName.trim()
        val password = s.password.trim()
        val confirm = s.confirmPassword.trim()

        if (email.isBlank() || password.isBlank() || displayName.isBlank()) {
            _state.value = _state.value.copy(error = "Please fill all fields")
            return
        }

        if (password != confirm) {
            _state.value = _state.value.copy(error = "Passwords do not match")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            authRepository.signUp(email, password)
                .onSuccess { signInResult ->
                    // Ensure RSA key pair is generated and public key uploaded to Firestore
                    userRepository.ensureKeyPairAndUploadPublicKey(
                        signInResult.user.uid,
                        signInResult.user.email ?: "",
                        displayName
                    ).onSuccess {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            navigateTo = NavRoutes.CONVERSATION_LIST
                        )
                    }.onFailure {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Security setup failed. Please try again."
                        )
                    }
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = it.message ?: "Registration failed"
                    )
                }
        }
    }

    fun clearNavigation() {
        _state.value = _state.value.copy(navigateTo = null)
    }
}
