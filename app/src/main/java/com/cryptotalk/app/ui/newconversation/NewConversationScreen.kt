package com.cryptotalk.app.ui.newconversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.fillMaxSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    viewModel: NewConversationViewModel,
    onBack: () -> Unit,
    onConversationStarted: (conversationId: String) -> Unit,
    onGroupStarted: (groupId: String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(state.navigateToConversationId, state.navigateToGroupId) {
        state.navigateToConversationId?.let { id ->
            onConversationStarted(id)
            viewModel.clearNavigation()
        }
        state.navigateToGroupId?.let { id ->
            onGroupStarted(id)
            viewModel.clearNavigation()
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = LinearOutSlowInEasing),
        label = "alpha"
    )

    val contentScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("New conversation", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.05f)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .alpha(contentAlpha)
                .scale(contentScale)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        0.5.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Create Group Chat", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = state.isGroupMode,
                            onCheckedChange = { viewModel.toggleGroupMode(it) }
                        )
                    }

                    Text(
                        text = if (state.isGroupMode) "Add people to your new group one by one." else "Enter the email of the person you'd like to chat with.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (state.isGroupMode || state.selectedUsers.isEmpty()) {
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = viewModel::updateEmail,
                            label = { Text("Recipient email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (state.isGroupMode && state.email.isNotEmpty()) {
                                    IconButton(onClick = viewModel::addUser) {
                                        Icon(Icons.Default.Add, contentDescription = "Add recipient")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = if (state.isGroupMode) ImeAction.Go else ImeAction.Done),
                            keyboardActions = KeyboardActions(onAny = { 
                                if (state.isGroupMode) viewModel.addUser() else viewModel.startConversation() 
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                    
                    if (state.selectedUsers.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("Selected participants:", style = MaterialTheme.typography.labelMedium)
                        state.selectedUsers.forEach { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(user.displayName ?: "User ${user.userId.take(4)}", style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = { viewModel.removeUser(user.userId) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }

                    if (state.isGroupMode) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = state.groupName,
                            onValueChange = viewModel::updateGroupName,
                            label = { Text("Group name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    state.error?.let { err ->
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = viewModel::startConversation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !state.isLoading && (state.selectedUsers.isNotEmpty() || (!state.isGroupMode && state.email.isNotEmpty())),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(if (state.isGroupMode) "Create group" else "Start chat")
                        }
                    }
                }
            }
        }
    }
}
