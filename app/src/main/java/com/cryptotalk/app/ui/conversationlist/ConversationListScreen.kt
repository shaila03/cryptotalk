package com.cryptotalk.app.ui.conversationlist

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShieldMoon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cryptotalk.app.navigation.NavRoutes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    viewModel: ConversationListViewModel,
    onOpenChat: (String, Boolean) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val lockedChatIds by viewModel.lockedChatIds.collectAsState()
    val isDecoy by viewModel.isDecoySession.collectAsState(initial = false)
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // BUG FIX #1: Collect key-reset warning event and show Snackbar
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is ConversationListUiEvent.KeysRegeneratedWarning -> {
                    snackbarHostState.showSnackbar(
                        message = "Security keys were reset. Previous messages cannot be decrypted.",
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
    }

    // Hoisted dialog states
    var renameConversationId by remember { mutableStateOf<String?>(null) }
    var renameInitialName by remember { mutableStateOf("") }
    var renameCurrentInput by remember { mutableStateOf("") }
    
    var deleteConversationId by remember { mutableStateOf<String?>(null) }
    // Check if biometric is needed on mount
    fun tryOpenChat(convId: String, isGroup: Boolean) {
        if (viewModel.isLocked(convId)) {
            val activity = context as? FragmentActivity ?: return
            val biometricPrompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onOpenChat(convId, isGroup)
                    }
                }
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Locked Chat")
                .setSubtitle("Authenticate to open this chat")
                .setNegativeButtonText("Cancel")
                .build()
            biometricPrompt.authenticate(info)
        } else {
            onOpenChat(convId, isGroup)
        }
    }

    // Prevent back button from causing auto-logout — just minimize the app
    BackHandler {
        (context as? android.app.Activity)?.moveTaskToBack(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Gradient icon box
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Crypto Talk",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                )
                            )
                            Text(
                                "End-to-end encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New conversation", modifier = Modifier.size(26.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        modifier = Modifier.background(
            Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                )
            )
        )
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.items.isEmpty()) {
            // Empty state
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No conversations yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap + to start a new conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                itemsIndexed(state.items, key = { _, item -> item.conversation.id }) { index, item ->
                    var visible by remember { mutableStateOf(false) }
                    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
                    val alpha by animateFloatAsState(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = tween(400, delayMillis = index * 40, easing = LinearOutSlowInEasing),
                        label = "alpha"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (visible) 1f else 0.92f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "scale"
                    )

                    var expandedMenu by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(alpha)
                            .scale(scale)
                    ) {
                        val isLocked = lockedChatIds.contains(item.conversation.id)
                        ConversationCard(
                            displayName = item.displayName,
                            isGroup = item.conversation.isGroup,
                            unreadCount = item.unreadCount,
                            isLocked = isLocked,
                            onTap = { tryOpenChat(item.conversation.id, item.conversation.isGroup) },
                            onLongPress = { expandedMenu = true }
                        )
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = { 
                                    expandedMenu = false
                                    renameConversationId = item.conversation.id
                                    renameInitialName = item.displayName
                                    renameCurrentInput = item.displayName
                                }
                            )
                            val isLocked = lockedChatIds.contains(item.conversation.id)
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (isLocked) "Unlock Chat" else "Lock Chat")
                                    }
                                },
                                onClick = {
                                    if (isLocked) viewModel.unlockChat(item.conversation.id)
                                    else viewModel.lockChat(item.conversation.id)
                                }
                            )
                            if (!isDecoy) {
                                val isSensitive = item.conversation.isSensitive
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isSensitive) Icons.Default.ShieldMoon else Icons.Default.Shield,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (isSensitive) "Unmark Sensitive" else "Mark Sensitive")
                                        }
                                    },
                                    onClick = {
                                        expandedMenu = false
                                        viewModel.toggleSensitive(item.conversation.id, !isSensitive)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    expandedMenu = false
                                    deleteConversationId = item.conversation.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---
    if (renameConversationId != null) {
        AlertDialog(
            onDismissRequest = { renameConversationId = null },
            title = { Text("Rename Contact") },
            text = {
                OutlinedTextField(
                    value = renameCurrentInput,
                    onValueChange = { renameCurrentInput = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameContact(renameConversationId!!, renameCurrentInput)
                    renameConversationId = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameConversationId = null }) { Text("Cancel") }
            }
        )
    }

    if (deleteConversationId != null) {
        AlertDialog(
            onDismissRequest = { deleteConversationId = null },
            title = { Text("Delete Conversation") },
            text = { Text("This will permanently delete this conversation and all its messages.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteConversation(deleteConversationId!!)
                        deleteConversationId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConversationId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ConversationCard(
    displayName: String,
    isGroup: Boolean,
    unreadCount: Int = 0,
    isLocked: Boolean = false,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        )
                    )
                )
                .border(
                    0.5.dp,
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGroup) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGroup) Icons.Default.Group else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isGroup) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isLocked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = if (isGroup) "Group Chat" else "Private Chat",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
