package com.cryptotalk.app.ui.newconversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.ConversationRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewConversationUiState(
    val email: String = "",
    val groupName: String = "",
    val selectedUsers: List<com.cryptotalk.app.data.model.User> = emptyList(),
    val isGroupMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToConversationId: String? = null,
    val navigateToGroupId: String? = null
)

class NewConversationViewModel(
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val groupRepository: com.cryptotalk.app.data.repository.GroupRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewConversationUiState())
    val state: StateFlow<NewConversationUiState> = _state.asStateFlow()

    fun updateEmail(email: String) {
        _state.value = _state.value.copy(email = email.trim(), error = null)
    }

    fun updateGroupName(name: String) {
        _state.value = _state.value.copy(groupName = name, error = null)
    }

    fun toggleGroupMode(isGroup: Boolean) {
        val currentUsers = _state.value.selectedUsers
        val newUsers = if (!isGroup && currentUsers.size > 1) listOf(currentUsers.first()) else currentUsers
        _state.value = _state.value.copy(isGroupMode = isGroup, selectedUsers = newUsers, error = null)
    }

    fun addUser() {
        val email = _state.value.email.trim()
        if (email.isBlank()) return
        val currentUserId = authRepository.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val user = userRepository.getUserByEmail(email)
                if (user == null) {
                    // BUG 3 FIX: Distinguish "not found" from network/permission errors
                    _state.value = _state.value.copy(isLoading = false, error = "No user found with that email.")
                    return@launch
                }
                if (user.userId == currentUserId) {
                    _state.value = _state.value.copy(isLoading = false, error = "Cannot add yourself")
                    return@launch
                }
                if (_state.value.selectedUsers.any { it.userId == user.userId }) {
                    _state.value = _state.value.copy(isLoading = false, error = "User already added")
                    return@launch
                }
                val newList = _state.value.selectedUsers + user
                _state.value = _state.value.copy(selectedUsers = newList, email = "", isLoading = false)
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                _state.value = _state.value.copy(isLoading = false, error = "Search failed (${e.code.name}). Check your connection.")
                android.util.Log.e("NewConversationVM", "Add user failed: ${e.code.name}", e)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = "An unexpected error occurred.")
                android.util.Log.e("NewConversationVM", "Unexpected search error", e)
            }
        }
    }

    fun removeUser(userId: String) {
        val newList = _state.value.selectedUsers.filter { it.userId != userId }
        _state.value = _state.value.copy(selectedUsers = newList)
    }

    fun startConversation() {
        val currentUserId = authRepository.currentUser?.uid ?: return
        val state = _state.value
        val typedEmail = state.email.trim()
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                if (!state.isGroupMode) {
                    // Direct chat logic
                    val targetUser = if (state.selectedUsers.isNotEmpty()) {
                        state.selectedUsers[0]
                    } else if (typedEmail.isNotEmpty()) {
                        val user = userRepository.getUserByEmail(typedEmail)
                        if (user == null) {
                            _state.value = _state.value.copy(isLoading = false, error = "User not found")
                            return@launch
                        }
                        if (user.userId == currentUserId) {
                            _state.value = _state.value.copy(isLoading = false, error = "Cannot chat with yourself")
                            return@launch
                        }
                        user
                    } else {
                        _state.value = _state.value.copy(isLoading = false, error = "Enter a recipient email")
                        return@launch
                    }

                    conversationRepository.getOrCreateConversation(currentUserId, targetUser.userId)
                        .onSuccess { conv ->
                            // BUG 3 FIX: Navigate only AFTER confirming conversation doc exists
                            _state.value = _state.value.copy(isLoading = false, navigateToConversationId = conv.id)
                        }
                        .onFailure {
                            // PROMPT 6 FIX: Explicit error messaging with Firestore code
                            val errorMsg = if (it is com.google.firebase.firestore.FirebaseFirestoreException)
                                "Search failed (${it.code.name}). Check your connection."
                            else
                                it.message ?: "Failed to start conversation."
                            _state.value = _state.value.copy(isLoading = false, error = errorMsg)
                            android.util.Log.e("NewConversationVM", "Conversation failed", it)
                        }
                } else {
                    // Group chat logic
                    val finalParticipants = state.selectedUsers.toMutableList()
                    if (typedEmail.isNotEmpty()) {
                        val user = userRepository.getUserByEmail(typedEmail)
                        if (user != null && user.userId != currentUserId && !finalParticipants.any { it.userId == user.userId }) {
                            finalParticipants.add(user)
                        }
                    }

                    if (finalParticipants.isEmpty()) {
                        _state.value = _state.value.copy(isLoading = false, error = "Add participants")
                        return@launch
                    }

                    val name = state.groupName.trim()
                    if (name.isBlank()) {
                        _state.value = _state.value.copy(isLoading = false, error = "Enter a group name")
                        return@launch
                    }
                    
                    val participantIds = finalParticipants.map { it.userId }
                    groupRepository.createGroup(name, "", participantIds, currentUserId)
                        .onSuccess { id ->
                            _state.value = _state.value.copy(isLoading = false, navigateToGroupId = id)
                        }
                        .onFailure {
                            _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Failed to create group")
                        }
                }
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                // PROMPT 6 FIX: Specific error for Firestore permission/network issues
                _state.value = _state.value.copy(isLoading = false, error = "Search failed (${e.code.name}). Check your connection.")
                android.util.Log.e("NewConversationVM", "Firestore failure in search", e)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "An unexpected error occurred")
                android.util.Log.e("NewConversationVM", "General error in search", e)
            }
        }
    }

    fun clearNavigation() {
        _state.value = _state.value.copy(navigateToConversationId = null, navigateToGroupId = null)
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val conversationRepository: ConversationRepository,
        private val userRepository: UserRepository,
        private val groupRepository: com.cryptotalk.app.data.repository.GroupRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NewConversationViewModel(authRepository, conversationRepository, userRepository, groupRepository) as T
        }
    }
}
