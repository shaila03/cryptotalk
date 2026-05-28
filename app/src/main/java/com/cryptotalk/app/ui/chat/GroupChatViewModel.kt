package com.cryptotalk.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.model.Group
import com.cryptotalk.app.data.model.GroupMessage
import com.cryptotalk.app.data.model.Message
import com.cryptotalk.app.data.model.MessageStatus
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.GroupRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.crypto.MessageCrypto
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GroupChatUiState(
    val group: Group? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUserId: String = "",
    val isAdmin: Boolean = false
)

class GroupChatViewModel(
    private val groupId: String,
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GroupChatUiState(currentUserId = authRepository.currentUser?.uid ?: ""))
    val state: StateFlow<GroupChatUiState> = _state.asStateFlow()

    private val messageCrypto = MessageCrypto()
    private val currentUserId = authRepository.currentUser?.uid ?: ""

    init {
        observeGroup()
        observeMessages()
    }

    private fun observeGroup() {
        viewModelScope.launch {
            groupRepository.getGroupFlow(groupId).collect { group ->
                if (group != null) {
                    _state.value = _state.value.copy(
                        group = group,
                        isAdmin = group.admins.contains(currentUserId),
                        isLoading = false
                    )
                } else {
                    _state.value = _state.value.copy(error = "Group not found", isLoading = false)
                }
            }
        }
    }

    private fun observeMessages() {
        val privateKey = userRepository.getPrivateKey(currentUserId)
        if (privateKey == null) {
            _state.value = _state.value.copy(error = "Security key missing. Cannot decrypt messages.")
            return
        }

        viewModelScope.launch {
            groupRepository.getMessagesFlow(groupId).collect { groupMessages ->
                val decryptedMessages = groupMessages.map { gm ->
                    val encryptedKey = gm.memberKeys[currentUserId]
                    val plaintext = if (encryptedKey != null) {
                        try {
                            messageCrypto.decrypt(gm.encryptedContent, encryptedKey, gm.iv, privateKey)
                        } catch (e: Exception) {
                            "[Decryption Failed]"
                        }
                    } else {
                        "[You are not a member of this group version]"
                    }

                    Message(
                        id = gm.messageId,
                        senderId = gm.senderId,
                        plaintext = plaintext,
                        timestamp = gm.timestamp,
                        isFromMe = gm.senderId == currentUserId,
                        senderName = gm.senderName,
                        status = MessageStatus.READ // Simplification for groups
                    )
                }
                _state.value = _state.value.copy(messages = decryptedMessages)
            }
        }
    }

    fun sendMessage(plaintext: String) {
        if (plaintext.isBlank()) return
        val group = _state.value.group ?: return
        
        // Admin-only check
        if (group.adminsOnlyMessage && !group.admins.contains(currentUserId)) {
            _state.value = _state.value.copy(error = "Only admins can send messages in this group.")
            return
        }

        viewModelScope.launch {
            val senderName = userRepository.getUser(currentUserId)?.displayName ?: "User"
            groupRepository.sendGroupMessage(groupId, currentUserId, senderName, plaintext)
                .onFailure {
                    _state.value = _state.value.copy(error = it.message ?: "Failed to send message")
                }
        }
    }

    fun setAdminsOnlyMode(enabled: Boolean) {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            groupRepository.setAdminsOnlyMessage(groupId, enabled)
                .onFailure { _state.value = _state.value.copy(error = "Failed to update permissions") }
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
            return GroupChatViewModel(groupId, authRepository, groupRepository, userRepository) as T
        }
    }
}
