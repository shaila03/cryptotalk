package com.cryptotalk.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.model.Group
import com.cryptotalk.app.data.model.User
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.GroupRepository
import com.cryptotalk.app.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GroupInfoUiState(
    val group: Group? = null,
    val members: List<User> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String = "",
    val isAdmin: Boolean = false
)

class GroupInfoViewModel(
    private val groupId: String,
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GroupInfoUiState(currentUserId = authRepository.currentUser?.uid ?: ""))
    val state: StateFlow<GroupInfoUiState> = _state.asStateFlow()

    private val currentUserId = authRepository.currentUser?.uid ?: ""

    init {
        observeGroup()
    }

    private fun observeGroup() {
        viewModelScope.launch {
            groupRepository.getGroupFlow(groupId).collect { group ->
                if (group != null) {
                    _state.value = _state.value.copy(
                        group = group,
                        isAdmin = group.admins.contains(currentUserId)
                    )
                    loadMembers(group.members)
                } else {
                    _state.value = _state.value.copy(error = "Group not found", isLoading = false)
                }
            }
        }
    }

    private suspend fun loadMembers(userIds: List<String>) {
        val users = mutableListOf<User>()
        for (id in userIds) {
            userRepository.getUser(id)?.let { users.add(it) }
        }
        _state.value = _state.value.copy(members = users, isLoading = false)
    }

    fun addMember(email: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            val user = userRepository.getUserByEmail(email)
            if (user != null) {
                groupRepository.addMember(groupId, user.userId)
                    .onFailure { _state.value = _state.value.copy(error = it.message) }
            } else {
                _state.value = _state.value.copy(error = "User not found")
            }
        }
    }

    fun removeMember(userId: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.removeMember(groupId, userId)
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun promoteToAdmin(userId: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.promoteToAdmin(groupId, userId)
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun demoteFromAdmin(userId: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.demoteFromAdmin(groupId, userId)
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun updateGroupInfo(name: String, description: String) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.updateGroupInfo(groupId, name, description)
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }
    
    fun setAdminsOnlyMessage(enabled: Boolean) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.setAdminsOnlyMessage(groupId, enabled)
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    class Factory(
        private val groupId: String,
        private val authRepository: AuthRepository,
        private val groupRepository: GroupRepository,
        private val userRepository: UserRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupInfoViewModel(groupId, authRepository, groupRepository, userRepository) as T
        }
    }
}
