package com.cryptotalk.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cryptotalk.app.data.model.Message
import com.cryptotalk.app.data.repository.AuthRepository
import com.cryptotalk.app.data.repository.ConversationRepository
import com.cryptotalk.app.data.repository.MessageRepository
import com.cryptotalk.app.data.repository.SettingsRepository
import com.cryptotalk.app.data.repository.UserRepository
import com.cryptotalk.app.notification.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.launch as coroutineLaunch // Adding this just in case, but standard launch is needed

data class ParticipantUiInfo(
    val userId: String,
    val name: String,
    val isAdmin: Boolean,
    val addedByName: String?,
    val addedAt: Long
)

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val otherUserName: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val successMessage: String? = null,
    val isBlocked: Boolean = false,
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val replyingTo: Message? = null,
    val disappearDuration: Long? = null,
    val isOtherTyping: Boolean = false,
    val adminIds: List<String> = emptyList(),
    val participants: List<ParticipantUiInfo> = emptyList(),
    val otherUserStatus: String = "",
    val searchText: String = "",
    val isSearching: Boolean = false,
    val forwardingMessage: Message? = null,
    val activeConversations: List<com.cryptotalk.app.data.model.Conversation> = emptyList(),
    val chatWallpaper: String? = null,
    val currentUserId: String = "",
    val scheduledAt: Long? = null
)

class ChatViewModel(
    private val conversationId: String,
    private val authRepository: AuthRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
    private val appContext: Context
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(currentUserId = authRepository.currentUser?.uid ?: ""))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val currentUserId = authRepository.currentUser?.uid ?: ""

    init {
        observeConversation()
        resetUnreadCount()
        
        viewModelScope.launch {
            settingsRepository.chatWallpaper.collect { wallpaper ->
                _state.value = _state.value.copy(chatWallpaper = wallpaper)
            }
        }

        val privateKey = userRepository.getPrivateKey(currentUserId)
        if (privateKey == null) {
            _state.value = _state.value.copy(isLoading = false, error = "Cannot decrypt messages")
        } else {
            viewModelScope.launch {
                var isInitialLoad = true
                var previousMessageIds = setOf<String>()
                
                // Reactive message flow: restarts if blockedUsers changes
                @OptIn(ExperimentalCoroutinesApi::class)
                userRepository.getPrivateProfileFlow(currentUserId)
                    .flatMapLatest { profile ->
                        val blockedUsers = profile?.blockedUsers ?: emptyList()
                        messageRepository.messagesFlow(conversationId, currentUserId, privateKey, blockedUsers)
                    }
                    .catch { e ->
                        _state.value = _state.value.copy(error = e.message ?: "Load failed", isLoading = false)
                    }
                    .collect { list ->
                        if (!isInitialLoad) {
                            val newMessages = list.filter { it.id !in previousMessageIds && !it.isFromMe }
                            newMessages.forEach { msg ->
                                val senderTitle = if (_state.value.isGroup) {
                                    val sender = _state.value.participants.find { it.userId == msg.senderId }?.name ?: "Group Member"
                                    "$sender in ${_state.value.groupName ?: "Group"}"
                                } else {
                                    _state.value.otherUserName.ifEmpty { "New message" }
                                }
                                val preview = if (msg.plaintext.startsWith("[")) "New encrypted message" else msg.plaintext
                                NotificationHelper.showMessageNotification(
                                    context = appContext,
                                    senderName = senderTitle,
                                    messagePreview = preview,
                                    notificationId = msg.id.hashCode()
                                )
                            }
                        }
                        isInitialLoad = false
                        previousMessageIds = list.map { it.id }.toSet()
                        
                        val listWithNames = list.map { msg ->
                            val name = _state.value.participants.find { it.userId == msg.senderId }?.name
                            msg.copy(senderName = name)
                        }
                        _state.value = _state.value.copy(messages = listWithNames, isLoading = false)
                
                        // Track self-destruct timers for messages with expirationTime
                        listWithNames.filter { it.expirationTime != null }.forEach { msg ->
                            observeSelfDestruct(msg)
                        }

                        // SIMULATED NOTIFICATION (Bug Fix #9)
                        val lastMsg = listWithNames.lastOrNull()
                        if (lastMsg != null && lastMsg.senderId != currentUserId && lastMsg.status != com.cryptotalk.app.data.model.MessageStatus.READ) {
                            com.cryptotalk.app.notification.NotificationHelper.showMessageNotification(
                                appContext,
                                lastMsg.senderName ?: "Contact",
                                lastMsg.plaintext,
                                lastMsg.timestamp.toInt()
                            )
                        }
                        
                        viewModelScope.launch {
                            messageRepository.markAllAsDelivered(conversationId, currentUserId)
                            messageRepository.markAllAsRead(conversationId, currentUserId)
                        }
                    }
            }
            
            // Typing Status Observation (if not a group)
            viewModelScope.launch {
                conversationRepository.conversationFlow(conversationId).collect { conv ->
                    if (conv != null && !conv.isGroup) {
                        val otherId = conv.otherUserId(currentUserId)
                        messageRepository.typingStatusFlow(conversationId, otherId).collect { isTyping ->
                            _state.value = _state.value.copy(isOtherTyping = isTyping)
                        }
                    }
                }
            }
        }
    }

    private fun observeConversation() {
        viewModelScope.launch {
            conversationRepository.conversationFlow(conversationId).collect { conv ->
                if (conv == null) {
                    _state.value = _state.value.copy(error = "Conversation not found", isLoading = false)
                    return@collect
                }
                
                val participantUiInfos = conv.participants.map { uid ->
                    val user = userRepository.getUser(uid)
                    val metadata = conv.participantMetadata[uid]
                    val addedByTitle = if (metadata != null) {
                        if (metadata.addedBy == currentUserId) "You"
                        else userRepository.getUser(metadata.addedBy)?.displayName ?: "User ${metadata.addedBy.takeLast(4).uppercase()}"
                    } else null
                    
                    ParticipantUiInfo(
                        userId = uid,
                        name = (if (uid == currentUserId) "You" else user?.displayName ?: "User ${uid.takeLast(4).uppercase()}"),
                        isAdmin = conv.adminIds.contains(uid),
                        addedByName = addedByTitle,
                        addedAt = metadata?.addedAt ?: conv.createdAt
                    )
                }
                
                // Track missing names reactively
                participantUiInfos.forEach { p ->
                    if (p.name.startsWith("User ") && p.userId != currentUserId) {
                        viewModelScope.launch {
                            userRepository.getUserFlow(p.userId).collect { user ->
                                if (user?.displayName != null) {
                                    // Refresh conversation if a name is found
                                    observeConversation()
                                }
                            }
                        }
                    }
                }

                val updatedMessages = _state.value.messages.map { msg ->
                    val name = participantUiInfos.find { it.userId == msg.senderId }?.name
                    msg.copy(senderName = name)
                }

                _state.value = _state.value.copy(
                    isGroup = conv.isGroup,
                    groupName = conv.name,
                    disappearDuration = conv.disappearDuration,
                    adminIds = conv.adminIds,
                    participants = participantUiInfos,
                    messages = updatedMessages
                )

                if (!conv.isGroup) {
                    val otherId = conv.otherUserId(currentUserId)
                    val customName = settingsRepository.getContactName(otherId)
                    val user = try { userRepository.getUser(otherId) } catch(e: Exception) { null }
                    _state.value = _state.value.copy(
                        otherUserName = customName ?: user?.displayName ?: "User ${otherId.takeLast(4).uppercase()}"
                    )
                    // PROMPT 6: Observe the current user's block list for reactive UI state
                    viewModelScope.launch {
                        userRepository.getPrivateProfileFlow(currentUserId).collect { currentProfile ->
                            _state.value = _state.value.copy(
                                isBlocked = currentProfile?.blockedUsers?.contains(otherId) == true
                            )
                        }
                    }
                    // PROMPT 6: Observe the other user's online/last-seen status in real time
                    viewModelScope.launch {
                        userRepository.getUserStatusFlow(otherId).collect { (isOnline, lastSeenMs) ->
                            val statusText = when {
                                isOnline -> "Online"
                                lastSeenMs == null -> ""
                                else -> {
                                    val diffMs = System.currentTimeMillis() - lastSeenMs
                                    val diffSec = diffMs / 1000
                                    val diffMin = diffMs / 60_000
                                    val diffHour = diffMs / 3_600_000
                                    when {
                                        diffSec < 60 -> "Last seen just now"
                                        diffMin < 60 -> "Last seen ${diffMin} min ago"
                                        else -> {
                                            val sdf = java.text.SimpleDateFormat(
                                                if (diffHour < 24) "'Last seen today at' HH:mm" else "'Last seen' dd MMM",
                                                java.util.Locale.getDefault()
                                            )
                                            sdf.format(java.util.Date(lastSeenMs))
                                        }
                                    }
                                }
                            }
                            _state.value = _state.value.copy(otherUserStatus = statusText)
                        }
                    }
                }
            }
        }
    }

    private suspend fun getParticipantPublicKeys(): Map<String, String>? {
        val conv = conversationRepository.getConversation(conversationId) ?: run {
            android.util.Log.e("ChatViewModel", "getParticipantPublicKeys: Conversation not found: $conversationId")
            return null
        }
        val keys = mutableMapOf<String, String>()
        for (userId in conv.participants) {
            val key = if (userId == currentUserId) {
                // For the sender themselves, we MUST prefer the local Keystore key so self-decryption works
                userRepository.getLocalPublicKeyBase64(userId)
                    ?: userRepository.getPublicKey(userId)
            } else {
                userRepository.getPublicKey(userId)
            }
            
            if (key == null) {
                android.util.Log.e("ChatViewModel", "getParticipantPublicKeys: Missing key for participant $userId")
                return null
            }
            keys[userId] = key
        }
        
        // Final sanity check for current user's key
        if (!keys.containsKey(currentUserId)) {
            val selfKey = userRepository.getLocalPublicKeyBase64(currentUserId)
                ?: userRepository.getPublicKey(currentUserId)
            if (selfKey == null) {
                android.util.Log.e("ChatViewModel", "getParticipantPublicKeys: Sender's own key is missing!")
                return null
            }
            keys[currentUserId] = selfKey
        }
        
        android.util.Log.d("ChatViewModel", "getParticipantPublicKeys: Successfully retrieved ${keys.size} keys")
        return keys
    }

    fun sendMessage(plaintext: String, selfDestruct: Boolean = false, destructAfterMs: Long = 10_000L) {
        if (plaintext.isBlank()) return
        setUserTyping(false) // Stop typing on send
        viewModelScope.launch {
            val participantKeys = if (settingsRepository.isEncryptionEnabled()) {
                getParticipantPublicKeys() ?: run {
                    // BUG 2 FIX: Specific, actionable error message for missing keys
                    _state.value = _state.value.copy(error = "Message not sent: could not retrieve recipient's security key. Please try again.")
                    return@launch
                }
            } else {
                emptyMap()
            }
            
            val scheduledAt = _state.value.scheduledAt
            
            messageRepository.sendMessage(
                conversationId, currentUserId, participantKeys, plaintext.trim(),
                _state.value.replyingTo?.id,
                _state.value.replyingTo?.plaintext,
                selfDestruct = selfDestruct,
                destructAfterMs = destructAfterMs,
                scheduledAt = scheduledAt
            )
                .onSuccess {
                    if (scheduledAt == null) {
                        conversationRepository.incrementUnreadCounts(conversationId, currentUserId)
                    } else {
                        _state.value = _state.value.copy(successMessage = "Message scheduled successfully")
                    }
                    _state.value = _state.value.copy(replyingTo = null, scheduledAt = null)
                }
                .onFailure {
                    _state.value = _state.value.copy(error = it.message ?: "Send failed")
                }
        }
    }

    fun setScheduledAt(timestamp: Long?) {
        _state.value = _state.value.copy(scheduledAt = timestamp)
    }

    /**
     * Schedules a self-destruct deletion of a message after [delayMs] milliseconds.
     * Called from ChatScreen when the message is displayed to a non-sender.
     */
    fun scheduleMessageDeletion(messageId: String, delayMs: Long) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            messageRepository.deleteMessage(conversationId, messageId)
        }
    }

    private var typingJob: kotlinx.coroutines.Job? = null
    /**
     * Updates the current user's typing status in Firestore.
     * When [isTyping] is true, it starts a 3-second timer that resets on every call.
     * This ensures the "typing..." indicator disappears automatically if the user stops.
     */
    fun setUserTyping(isTyping: Boolean) {
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            if (!settingsRepository.isTypingIndicatorEnabled()) return@launch
            messageRepository.setTypingStatus(conversationId, currentUserId, isTyping)
            if (isTyping) {
                kotlinx.coroutines.delay(3000)
                messageRepository.setTypingStatus(conversationId, currentUserId, false)
            }
        }
    }

    fun forwardMessage(message: Message, targetConversationId: String) {
        viewModelScope.launch {
            val targetConv = conversationRepository.getConversation(targetConversationId) ?: return@launch
            val targetParticipantKeys = mutableMapOf<String, String>()
            for (uid in targetConv.participants) {
                val key = if (uid == currentUserId) {
                    userRepository.getLocalPublicKeyBase64(uid) ?: userRepository.getPublicKey(uid)
                } else {
                    userRepository.getPublicKey(uid)
                }
                if (key != null) targetParticipantKeys[uid] = key
            }

            messageRepository.sendMessage(
                targetConversationId,
                currentUserId,
                targetParticipantKeys,
                message.plaintext,
                selfDestruct = false
            ).onSuccess {
                conversationRepository.incrementUnreadCounts(targetConversationId, currentUserId)
                _state.value = _state.value.copy(forwardingMessage = null, successMessage = "Message forwarded")
            }.onFailure {
                _state.value = _state.value.copy(error = "Forward failed: ${it.message}")
            }
        }
    }

    fun setForwardingMessage(message: Message?) {
        _state.value = _state.value.copy(forwardingMessage = message)
        if (message != null) {
            viewModelScope.launch {
                val convs = conversationRepository.getActiveConversations(currentUserId)
                _state.value = _state.value.copy(activeConversations = convs)
            }
        }
    }

    fun setChatWallpaper(wallpaper: String?) {
        viewModelScope.launch {
            settingsRepository.setChatWallpaper(wallpaper)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Explicitly clear typing status when the user leaves the chat
        typingJob?.cancel()
        viewModelScope.launch {
            messageRepository.setTypingStatus(conversationId, currentUserId, false)
        }
    }

    fun blockUser() {
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(conversationId) ?: return@launch
            if (conv.isGroup) return@launch // Cannot block a group
            val otherId = conv.otherUserId(currentUserId)
            userRepository.blockUser(currentUserId, otherId)
                .onFailure { _state.value = _state.value.copy(error = "Block failed") }
        }
    }

    fun unblockUser() {
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(conversationId) ?: return@launch
            if (conv.isGroup) return@launch
            val otherId = conv.otherUserId(currentUserId)
            userRepository.unblockUser(currentUserId, otherId)
                .onFailure { _state.value = _state.value.copy(error = "Unblock failed") }
        }
    }

    fun leaveGroup() {
        viewModelScope.launch {
            conversationRepository.leaveGroup(conversationId, currentUserId)
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to leave group")
                }
        }
    }

    private val activeDestructTimers = mutableSetOf<String>()

    private fun observeSelfDestruct(message: com.cryptotalk.app.data.model.Message) {
        val expirationTime = message.expirationTime ?: return
        if (activeDestructTimers.contains(message.id)) return
        
        activeDestructTimers.add(message.id)
        viewModelScope.launch {
            val delayMs = expirationTime - System.currentTimeMillis()
            if (delayMs > 0) {
                kotlinx.coroutines.delay(delayMs)
            }
            messageRepository.deleteMessage(conversationId, message.id)
            activeDestructTimers.remove(message.id)
        }
    }

    fun renameGroup(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            conversationRepository.renameGroup(conversationId, newName)
                .onSuccess {
                    _state.value = _state.value.copy(groupName = newName)
                }
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to rename group")
                }
        }
    }

    fun setNickname(nickname: String) {
        viewModelScope.launch {
            val conv = conversationRepository.getConversation(conversationId) ?: return@launch
            if (conv.isGroup) return@launch
            val otherId = conv.otherUserId(currentUserId)
            settingsRepository.setContactName(otherId, nickname.takeIf { it.isNotBlank() })
            _state.value = _state.value.copy(otherUserName = nickname.ifBlank { 
                userRepository.getUser(otherId)?.let { it.displayName ?: "User ${otherId.take(4)}" } ?: "User"
            })
        }
    }

    private fun resetUnreadCount() {
        viewModelScope.launch {
            conversationRepository.resetUnreadCount(conversationId, currentUserId)
        }
    }

    fun addParticipants(emails: List<String>) {
        if (emails.isEmpty()) return
        viewModelScope.launch {
            val userIds = mutableListOf<String>()
            val names = mutableListOf<String>()
            for (email in emails) {
                val user = userRepository.getUserByEmail(email)
                if (user != null) {
                    userIds.add(user.userId)
                    names.add(user.displayName ?: "User ${user.userId.take(4)}")
                } else {
                    _state.value = _state.value.copy(error = "User not found: $email")
                    return@launch
                }
            }
            conversationRepository.addParticipantsToGroup(conversationId, userIds, currentUserId)
                .onSuccess {
                    val actorName = userRepository.getUser(currentUserId)?.displayName ?: "Admin"
                    names.forEach { name ->
                        messageRepository.sendSystemMessage(conversationId, "$name was added by $actorName")
                    }
                    _state.value = _state.value.copy(error = "Participants added.")
                }
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to add participants")
                }
        }
    }

    fun removeUser(userId: String) {
        viewModelScope.launch {
            val targetName = userRepository.getUser(userId)?.displayName ?: "User"
            conversationRepository.removeParticipant(conversationId, userId, currentUserId)
                .onSuccess {
                    val actorName = userRepository.getUser(currentUserId)?.displayName ?: "Admin"
                    messageRepository.sendSystemMessage(conversationId, "$targetName was removed by $actorName")
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Failed to remove user") }
        }
    }

    fun makeUserAdmin(userId: String) {
        viewModelScope.launch {
            val targetName = userRepository.getUser(userId)?.displayName ?: "User"
            conversationRepository.makeAdmin(conversationId, userId, currentUserId)
                .onSuccess {
                    val actorName = userRepository.getUser(currentUserId)?.displayName ?: "Admin"
                    messageRepository.sendSystemMessage(conversationId, "$targetName is now an admin (promoted by $actorName)")
                }
                .onFailure { _state.value = _state.value.copy(error = it.message ?: "Failed to promote user") }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun setSearchText(text: String) {
        _state.value = _state.value.copy(searchText = text)
    }

    fun setSearching(isSearching: Boolean) {
        _state.value = _state.value.copy(isSearching = isSearching, searchText = if (!isSearching) "" else _state.value.searchText)
    }

    fun clearSuccessMessage() {
        _state.value = _state.value.copy(successMessage = null)
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessage(conversationId, messageId)
                .onSuccess {
                    conversationRepository.updateLastMessageAt(conversationId)
                }
                .onFailure {
                    _state.value = _state.value.copy(error = it.message ?: "Delete failed")
                }
        }
    }

    fun updateMessage(messageId: String, newPlaintext: String) {
        if (newPlaintext.isBlank()) return
        viewModelScope.launch {
            val participantKeys = if (settingsRepository.isEncryptionEnabled()) {
                getParticipantPublicKeys() ?: run {
                    _state.value = _state.value.copy(error = "Message not sent: could not retrieve recipient's security key. Please try again.")
                    return@launch
                }
            } else {
                emptyMap()
            }
            messageRepository.updateMessage(conversationId, messageId, currentUserId, participantKeys, newPlaintext.trim())
                .onSuccess {
                    conversationRepository.updateLastMessageAt(conversationId)
                    // PROMPT 2 FIX: Show success Snackbar on edit
                    _state.value = _state.value.copy(successMessage = "Message updated.")
                }
                .onFailure {
                    _state.value = _state.value.copy(error = "Edit failed. Please try again.")
                }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            messageRepository.toggleReaction(conversationId, messageId, currentUserId, emoji)
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to update reaction")
                }
        }
    }

    fun toggleStar(messageId: String) {
        viewModelScope.launch {
            messageRepository.toggleStar(conversationId, messageId, currentUserId)
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to update star")
                }
        }
    }

    fun setReplyingTo(message: Message?) {
        _state.value = _state.value.copy(replyingTo = message)
    }

    fun toggleMessageSensitive(messageId: String, isSensitive: Boolean) {
        viewModelScope.launch {
            messageRepository.toggleMessageSensitive(conversationId, messageId, isSensitive)
                .onFailure { _state.value = _state.value.copy(error = "Mark sensitive failed") }
        }
    }

    fun setDisappearingMessages(duration: Long?) {
        viewModelScope.launch {
            conversationRepository.setDisappearDuration(conversationId, duration)
                .onSuccess {
                    _state.value = _state.value.copy(disappearDuration = duration)
                }
                .onFailure {
                    _state.value = _state.value.copy(error = "Failed to update disappearing messages")
                }
        }
    }

    class Factory(
        private val conversationId: String,
        private val authRepository: AuthRepository,
        private val conversationRepository: ConversationRepository,
        private val messageRepository: MessageRepository,
        private val userRepository: UserRepository,
        private val settingsRepository: SettingsRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                conversationId,
                authRepository,
                conversationRepository,
                messageRepository,
                userRepository,
                settingsRepository,
                appContext
            ) as T
        }
    }
}
