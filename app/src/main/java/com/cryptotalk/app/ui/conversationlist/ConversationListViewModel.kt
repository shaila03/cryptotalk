package com.cryptotalk.app.ui.conversationlist

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.model.Conversation
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.ConversationRepository
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ConversationItem(
    val conversation: Conversation,
    val displayName: String,
    val unreadCount: Int = 0
)

sealed class ConversationListUiEvent {
    object KeysRegeneratedWarning : ConversationListUiEvent()
}

data class ConversationListUiState(
    val items: List<ConversationItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ConversationListViewModel(
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    private val lockPrefs: SharedPreferences =
        appContext.getSharedPreferences("locked_chats", Context.MODE_PRIVATE)

    private val _lockedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedChatIds: StateFlow<Set<String>> = _lockedChatIds.asStateFlow()

    val isDecoySession = settingsRepository.isDecoySession

    private fun loadLockedIds(): Set<String> {
        return lockPrefs.getStringSet("locked", emptySet()) ?: emptySet()
    }

    fun isLocked(conversationId: String) = _lockedChatIds.value.contains(conversationId)

    fun lockChat(conversationId: String) {
        val updated = _lockedChatIds.value.toMutableSet().apply { add(conversationId) }
        lockPrefs.edit().putStringSet("locked", updated).apply()
        _lockedChatIds.value = updated
    }

    fun unlockChat(conversationId: String) {
        val updated = _lockedChatIds.value.toMutableSet().apply { remove(conversationId) }
        lockPrefs.edit().putStringSet("locked", updated).apply()
        _lockedChatIds.value = updated
    }

    private val _state = MutableStateFlow(ConversationListUiState())
    val state: StateFlow<ConversationListUiState> = _state.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ConversationListUiEvent>(extraBufferCapacity = 1)
    val uiEvent: SharedFlow<ConversationListUiEvent> = _uiEvent.asSharedFlow()

    private var collectionJob: kotlinx.coroutines.Job? = null

    init {
        val userId = authRepository.currentUser?.uid
        if (userId == null) {
            _state.value = _state.value.copy(isLoading = false)
        } else {
            viewModelScope.launch {
                // Short delay to ensure Firebase is ready
                kotlinx.coroutines.delay(200)
                initLoad()
            }
        }
    }

    fun getCurrentUserId(): String? = authRepository.currentUser?.uid

    fun renameContact(conversationId: String, newName: String) {
        val userId = authRepository.currentUser?.uid ?: return
        val conv = _state.value.items.find { it.conversation.id == conversationId }?.conversation ?: return
        
        if (conv.isGroup) {
            viewModelScope.launch {
                conversationRepository.renameGroup(conversationId, newName)
                    .onSuccess { initLoad() }
            }
        } else {
            val otherId = conv.otherUserId(userId)
            viewModelScope.launch {
                settingsRepository.setContactName(otherId, newName)
                // trigger reload
                _state.value = _state.value.copy(isLoading = true)
                initLoad()
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(conversationId)
        }
    }

    private fun initLoad() {
        val userId = authRepository.currentUser?.uid ?: return
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            // BUG FIX #1: Detect if keys were wiped (e.g., after reinstall) and warn the user
            if (!userRepository.hasLocalKeyPair(userId)) {
                try {
                    userRepository.ensureKeyPairAndUploadPublicKey(userId, "")
                    _uiEvent.tryEmit(ConversationListUiEvent.KeysRegeneratedWarning)
                } catch (e: Exception) {
                    // Non-fatal; continue loading
                }
            }

            // Fetch private profile for blocked list
            val privateProfile = try { userRepository.getPrivateProfile(userId) } catch (e: Exception) { null }
            if (privateProfile == null) {
                // Initial registration might not have profile yet
                _state.value = _state.value.copy(isLoading = false)
            }
            val blockedUsers = privateProfile?.blockedUsers ?: emptyList()
            val isDecoy = settingsRepository.isDecoySession.value


            conversationRepository.conversationsFlow(userId)
                .catch { e -> 
                    _state.value = _state.value.copy(error = e.message ?: "Load failed", isLoading = false) 
                }
                .collect { convList ->
                    val items = mutableListOf<ConversationItem>()
                    for (conv in convList) {
                        // Decoy Filter: Hide items marked as sensitive if in decoy session
                        if (isDecoy && conv.isSensitive) continue

                        if (conv.isGroup) {
                            items.add(ConversationItem(conv, conv.name ?: "Group Chat", conv.unreadCounts[userId] ?: 0))
                        } else {
                            val otherId = conv.otherUserId(userId)
                            // Filter out if blocked
                            if (blockedUsers.contains(otherId)) continue
                            
                            val user = try { userRepository.getUser(otherId) } catch(e: Exception) { null }
                            val customName = settingsRepository.getContactName(otherId)
                            val displayName = customName ?: user?.displayName ?: "User ${otherId.takeLast(4).uppercase()}"
                            items.add(ConversationItem(conv, displayName, conv.unreadCounts[userId] ?: 0))
                        }
                    }
                    _state.value = _state.value.copy(items = items, isLoading = false)
                }
        }
    }

    fun toggleSensitive(conversationId: String, isSensitive: Boolean) {
        viewModelScope.launch {
            conversationRepository.setSensitive(conversationId, isSensitive)
        }
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val conversationRepository: ConversationRepository,
        private val userRepository: UserRepository,
        private val settingsRepository: SettingsRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConversationListViewModel(authRepository, conversationRepository, userRepository, settingsRepository, appContext) as T
        }
    }
}
