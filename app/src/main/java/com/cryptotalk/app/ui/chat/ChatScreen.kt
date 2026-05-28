package com.cryptotalk.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptotalk.app.data.model.Message
import com.cryptotalk.app.data.model.MessageStatus
import com.cryptotalk.app.util.UrlUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    otherUserName: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var selfDestructEnabled by remember { mutableStateOf(false) }
    var destructAfterMs by remember { mutableStateOf(10_000L) }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scheduledAt = state.scheduledAt

    // Dialog states
    var showNicknameDialog by remember { mutableStateOf(false) }
    var newNickname by remember { mutableStateOf("") }
    var showRenameGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var showAddParticipantDialog by remember { mutableStateOf(false) }
    var emailToAdd by remember { mutableStateOf("") }
    var showParticipantsDialog by remember { mutableStateOf(false) }
    var showDisappearMenu by remember { mutableStateOf(false) }

    // Auto-scroll to bottom
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.setUserTyping(false) }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    if (!state.isSearching) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = (if (state.isGroup) state.groupName else state.otherUserName)
                                    ?.firstOrNull()?.uppercaseChar() ?: '?'
                                Text(
                                    initial.toString(),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    if (state.isGroup) (state.groupName ?: "Group Chat")
                                    else state.otherUserName.ifEmpty { "Chat" },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1
                                )
                                Text(
                                    if (state.isOtherTyping) {
                                        if (state.isGroup) "someone is typing..."
                                        else "${state.otherUserName} is typing..."
                                    } else if (state.isGroup) "${state.participants.size} participants"
                                    else state.otherUserStatus.ifEmpty { "🔒 Encrypted" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.isOtherTyping || state.otherUserStatus == "Online") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = state.searchText,
                            onValueChange = { viewModel.setSearchText(it) },
                            modifier = Modifier.fillMaxWidth().height(52.dp).padding(end = 8.dp),
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setSearching(false) }) {
                                    Icon(Icons.Default.Close, "Close search")
                                }
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isSearching) {
                        IconButton(onClick = { viewModel.setSearching(true) }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Disappearing Messages") },
                            onClick = { showMenu = false; showDisappearMenu = true },
                            leadingIcon = { Icon(Icons.Default.Timer, null, modifier = Modifier.size(18.dp)) }
                        )
                        if (!state.isGroup) {
                            DropdownMenuItem(
                                text = { Text(if (state.isBlocked) "Unblock User" else "Block User") },
                                onClick = {
                                    if (state.isBlocked) viewModel.unblockUser() else viewModel.blockUser()
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Set Nickname") },
                                onClick = {
                                    newNickname = state.otherUserName
                                    showMenu = false; showNicknameDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp)) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Group Participants") },
                                onClick = { showMenu = false; showParticipantsDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Rename Group") },
                                onClick = {
                                    newGroupName = state.groupName ?: ""
                                    showMenu = false; showRenameGroupDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Participants") },
                                onClick = { emailToAdd = ""; showMenu = false; showAddParticipantDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Leave Group", color = MaterialTheme.colorScheme.error) },
                                onClick = { viewModel.leaveGroup(); showMenu = false; onBack() }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Message List
                val filteredMessages = if (state.searchText.isBlank()) {
                    state.messages
                } else {
                    state.messages.filter { it.plaintext.contains(state.searchText, ignoreCase = true) }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        if (msg.selfDestruct && !msg.isFromMe && msg.destructAfterMs > 0) {
                            LaunchedEffect(msg.id) { viewModel.scheduleMessageDeletion(msg.id, msg.destructAfterMs) }
                        }
                        MessageBubble(
                            message = msg,
                            isGroup = state.isGroup,
                            currentUserId = state.currentUserId,
                            onCopy = { clipboardManager.setText(AnnotatedString(msg.plaintext)) },
                            onDelete = { viewModel.deleteMessage(it.id) },
                            onEdit = { viewModel.updateMessage(msg.id, it) },
                            onReply = { viewModel.setReplyingTo(it) },
                            onReaction = { message, emoji -> viewModel.toggleReaction(message.id, emoji) },
                            onToggleSensitive = { mid, sens -> viewModel.toggleMessageSensitive(mid, sens) },
                            onToggleStar = { viewModel.toggleStar(it) },
                            onForward = { viewModel.setForwardingMessage(it) }
                        )
                    }
                }

                // Error / Success / Typing / Reply Preview / Input Bar
                ChatFooter(
                    state = state,
                    inputText = inputText,
                    onInputTextChange = { 
                        inputText = it
                        if (it.isNotEmpty()) viewModel.setUserTyping(true)
                    },
                    selfDestructEnabled = selfDestructEnabled,
                    onSelfDestructToggle = { selfDestructEnabled = it },
                    destructAfterMs = destructAfterMs,
                    onDestructAfterMsChange = { destructAfterMs = it },
                    scheduledAt = scheduledAt,
                    onSetScheduledAt = { viewModel.setScheduledAt(it) },
                    otherUserName = state.otherUserName,
                    onPaste = { 
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        inputText += text
                    },
                    onSend = {
                        viewModel.sendMessage(inputText.trim(), selfDestruct = selfDestructEnabled, destructAfterMs = destructAfterMs)
                        inputText = ""
                        selfDestructEnabled = false
                    },
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    onClearError = { viewModel.clearError() }
                )
            }
        }

        // Dialogs
        
        if (state.forwardingMessage != null) {
            ForwardDialog(
                state = state,
                onDismiss = { viewModel.setForwardingMessage(null) },
                onForward = { viewModel.forwardMessage(state.forwardingMessage!!, it) }
            )
        }

        if (showParticipantsDialog) {
            ParticipantsDialog(
                state = state,
                onDismiss = { showParticipantsDialog = false },
                onRemoveUser = { viewModel.removeUser(it) },
                onMakeAdmin = { viewModel.makeUserAdmin(it) }
            )
        }

        if (showNicknameDialog) {
            AlertDialog(
                onDismissRequest = { showNicknameDialog = false },
                title = { Text("Set Nickname") },
                text = {
                    OutlinedTextField(
                        value = newNickname,
                        onValueChange = { newNickname = it },
                        label = { Text("Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.setNickname(newNickname.trim()); showNicknameDialog = false }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showNicknameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showRenameGroupDialog) {
            AlertDialog(
                onDismissRequest = { showRenameGroupDialog = false },
                title = { Text("Rename Group") },
                text = {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("New Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameGroup(newGroupName.trim()); showRenameGroupDialog = false }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameGroupDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showDisappearMenu) {
            DisappearingMessagesDialog(
                currentDuration = state.disappearDuration,
                onDismiss = { showDisappearMenu = false },
                onSelect = { viewModel.setDisappearingMessages(it); showDisappearMenu = false }
            )
        }

        if (showAddParticipantDialog) {
            AlertDialog(
                onDismissRequest = { showAddParticipantDialog = false },
                title = { Text("Add Participant") },
                text = {
                    OutlinedTextField(
                        value = emailToAdd,
                        onValueChange = { emailToAdd = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (emailToAdd.isNotBlank()) {
                            viewModel.addParticipants(listOf(emailToAdd.trim()))
                            showAddParticipantDialog = false
                        }
                    }) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddParticipantDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ChatFooter(
    state: ChatUiState,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    selfDestructEnabled: Boolean,
    onSelfDestructToggle: (Boolean) -> Unit,
    destructAfterMs: Long,
    onDestructAfterMsChange: (Long) -> Unit,
    scheduledAt: Long?,
    onSetScheduledAt: (Long?) -> Unit,
    otherUserName: String,
    onPaste: () -> Unit,
    onSend: () -> Unit,
    onCancelReply: () -> Unit,
    onClearError: () -> Unit
) {
    Column {
        // Error banner
        state.error?.let { err ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠ $err", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onClearError) { Text("Dismiss", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        // Typing dots
        AnimatedVisibility(visible = state.isOtherTyping) {
            TypingIndicatorDots()
        }

        // Reply preview
        state.replyingTo?.let { reply ->
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Replying to ${reply.senderName ?: "Message"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(reply.plaintext, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onCancelReply) { Icon(Icons.Default.Close, "Cancel") }
                }
            }
        }

        // Input row
        val context = LocalContext.current
        Surface(tonalElevation = 8.dp) {
            Column {
                if (scheduledAt != null) {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        val dateStr = remember(scheduledAt) { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(scheduledAt)) }
                        Text("Scheduled for $dateStr", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { onSetScheduledAt(null) }) { Text("Cancel", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = {
                        val calendar = Calendar.getInstance()
                        android.app.DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            calendar.set(year, month, dayOfMonth)
                            android.app.TimePickerDialog(context, { _, hourOfDay, minute ->
                                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                calendar.set(Calendar.MINUTE, minute)
                                calendar.set(Calendar.SECOND, 0)
                                if (calendar.timeInMillis > System.currentTimeMillis()) {
                                    onSetScheduledAt(calendar.timeInMillis)
                                }
                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }) {
                        Icon(Icons.Default.Schedule, "Schedule", tint = if (scheduledAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onPaste) { Icon(Icons.Default.ContentPaste, "Paste") }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconToggleButton(checked = selfDestructEnabled, onCheckedChange = onSelfDestructToggle) {
                        Icon(if (selfDestructEnabled) Icons.Default.Timer else Icons.Default.TimerOff, "Self-destruct", 
                            tint = if (selfDestructEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val canSend = inputText.isNotBlank()
                    IconButton(
                        onClick = onSend,
                        enabled = canSend,
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isGroup: Boolean,
    currentUserId: String,
    onCopy: () -> Unit,
    onDelete: (Message) -> Unit,
    onEdit: (String) -> Unit,
    onReply: (Message) -> Unit,
    onReaction: (Message, String) -> Unit,
    onToggleSensitive: (String, Boolean) -> Unit,
    onToggleStar: (String) -> Unit,
    onForward: (Message) -> Unit
) {
    val isFromMe = message.isFromMe
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    val timeLabel = remember(message.timestamp) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)) }

    if (showEditDialog) {
        EditMessageDialog(currentText = message.plaintext, onDismiss = { showEditDialog = false }, onConfirm = { onEdit(it) })
    }
    if (showOptionsDialog) {
        MessageOptionsDialog(
            isFromMe = isFromMe,
            isSensitive = message.isSensitive,
            isStarred = message.starredBy.contains(currentUserId),
            onDismiss = { showOptionsDialog = false },
            onCopy = { onCopy(); showOptionsDialog = false },
            onEdit = { showOptionsDialog = false; showEditDialog = true },
            onDelete = { onDelete(message); showOptionsDialog = false },
            onReply = { onReply(message); showOptionsDialog = false },
            onReaction = { onReaction(message, it); showOptionsDialog = false },
            onToggleSensitive = { onToggleSensitive(message.id, !message.isSensitive); showOptionsDialog = false },
            onToggleStar = { onToggleStar(message.id); showOptionsDialog = false },
            onForward = { onForward(message); showOptionsDialog = false }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount > 50) { change.consume(); onReply(message) }
                }
            }.padding(vertical = 2.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start, modifier = Modifier.widthIn(max = 300.dp)) {
            if (isGroup && !isFromMe && message.senderName != null) {
                Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp), fontWeight = FontWeight.Bold)
            }

            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (isFromMe) 20.dp else 4.dp, bottomEnd = if (isFromMe) 4.dp else 20.dp),
                color = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showOptionsDialog = true })
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
/*
                    if (message.replyToId != null) {
                        Surface(color = Color.Black.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Reply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(message.replyToText ?: "...", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
*/

                    var reveal by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.blur(if (message.isSensitive && !reveal) 12.dp else 0.dp).clickable(enabled = message.isSensitive && !reveal) { reveal = true }) {
                        SelectionContainer {
                            Text(message.plaintext, color = if (isFromMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    val url = remember(message.plaintext) { UrlUtils.extractUrl(message.plaintext) }
                    if (url != null) {
                        Spacer(Modifier.height(8.dp))
                        LinkPreviewCard(url)
                    }

                    Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (message.isScheduled) {
                            Icon(Icons.Default.Schedule, "Scheduled", modifier = Modifier.size(12.dp), tint = if (isFromMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(timeLabel + (if (message.editedAt != null) " · edited" else ""), style = MaterialTheme.typography.labelSmall, color = if (isFromMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        if (isFromMe && !message.isScheduled) { Spacer(Modifier.width(4.dp)); MessageStatusIcon(message.status) }
                        if (message.starredBy.contains(currentUserId)) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Star, "Starred", modifier = Modifier.size(12.dp), tint = if (isFromMe) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary)
                        }
                        if (message.expirationTime != null) { Spacer(Modifier.width(8.dp)); SelfDestructCountdown(message.expirationTime) }
                    }
                }
            }

            if (!message.reactions.isNullOrEmpty()) {
                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.reactions.values.distinct().take(3).forEach { emoji ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(emoji, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageOptionsDialog(
    isFromMe: Boolean,
    isSensitive: Boolean,
    isStarred: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onReaction: (String) -> Unit,
    onToggleSensitive: () -> Unit,
    onToggleStar: () -> Unit,
    onForward: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🔥").forEach { emoji ->
                        IconButton(onClick = { onReaction(emoji) }) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                    }
                }
                HorizontalDivider()
                OptionItem(Icons.AutoMirrored.Filled.Reply, "Reply", onReply)
                OptionItem(Icons.Default.ContentCopy, "Copy", onCopy)
                OptionItem(if (isStarred) Icons.Default.StarOutline else Icons.Default.Star, if (isStarred) "Unstar" else "Star", onToggleStar)
                OptionItem(Icons.AutoMirrored.Filled.Forward, "Forward", onForward)
                OptionItem(if (isSensitive) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (isSensitive) "Unmark Sensitive" else "Mark Sensitive", onToggleSensitive)
                if (isFromMe) {
                    OptionItem(Icons.Default.Edit, "Edit", onEdit)
                    OptionItem(Icons.Default.Delete, "Delete", onDelete, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun OptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurface) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
    }
}

@Composable
private fun TypingIndicatorDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        (0..2).forEach { i ->
            val alpha by infiniteTransition.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 200), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.padding(horizontal = 2.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)))
        }
        Spacer(Modifier.width(8.dp))
        Text("typing...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun MessageStatusIcon(status: MessageStatus) {
    val icon = if (status == MessageStatus.SENT) Icons.Default.Check else Icons.Default.DoneAll
    val tint = if (status == MessageStatus.READ) Color(0xFF34B7F1) else Color.White.copy(alpha = 0.6f)
    Icon(icon, null, modifier = Modifier.size(12.dp), tint = tint)
}

@Composable
private fun SelfDestructCountdown(expirationTime: Long) {
    var timeLeft by remember(expirationTime) { mutableStateOf((expirationTime - System.currentTimeMillis()) / 1000) }
    LaunchedEffect(expirationTime) {
        while (timeLeft > 0) {
            kotlinx.coroutines.delay(1000)
            timeLeft = (expirationTime - System.currentTimeMillis()) / 1000
        }
    }
    if (timeLeft > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, modifier = Modifier.size(12.dp), tint = Color.Red.copy(alpha = 0.8f))
            Spacer(Modifier.width(2.dp))
            Text("${timeLeft}s", style = MaterialTheme.typography.labelSmall, color = Color.Red.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun LinkPreviewCard(url: String) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(8.dp)) {
            Text("Link Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ParticipantsDialog(state: ChatUiState, onDismiss: () -> Unit, onRemoveUser: (String) -> Unit, onMakeAdmin: (String) -> Unit) {
    val isAdmin = state.adminIds.contains(state.currentUserId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Participants") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(state.participants) { p ->
                    ListItem(
                        headlineContent = { Text(p.name, fontWeight = if (p.isAdmin) FontWeight.Bold else FontWeight.Normal) },
                        trailingContent = {
                            if (isAdmin && p.userId != state.currentUserId) {
                                Row {
                                    if (!p.isAdmin) IconButton(onClick = { onMakeAdmin(p.userId) }) { Icon(Icons.Default.Security, "Admin") }
                                    IconButton(onClick = { onRemoveUser(p.userId) }) { Icon(Icons.Default.PersonRemove, "Remove", tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun EditMessageDialog(currentText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(currentText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Message") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DisappearingMessagesDialog(currentDuration: Long?, onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disappearing Messages") },
        text = {
            Column {
                listOf("Off" to null, "1 Minute" to 60000L, "1 Hour" to 3600000L, "24 Hours" to 86400000L).forEach { (label, dur) ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(dur) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = currentDuration == dur, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ForwardDialog(state: ChatUiState, onDismiss: () -> Unit, onForward: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward To") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                items(state.activeConversations) { conv ->
                    val title = if (conv.isGroup) conv.name ?: "Group" else "Chat"
                    ListItem(headlineContent = { Text(title) }, modifier = Modifier.clickable { onForward(conv.id); onDismiss() })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
