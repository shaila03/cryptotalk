package com.cryptotalk.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptotalk.app.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    viewModel: GroupInfoViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var emailToAdd by remember { mutableStateOf("") }
    
    // Group Edit State
    var showEditGroupDialog by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var editedDescription by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.isAdmin) {
                        IconButton(onClick = {
                            editedName = state.group?.name ?: ""
                            editedDescription = state.group?.description ?: ""
                            showEditGroupDialog = true
                        }) {
                            Icon(Icons.Default.Edit, "Edit Group")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                FloatingActionButton(onClick = { showAddMemberDialog = true }) {
                    Icon(Icons.Default.Add, "Add Member")
                }
            }
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Group Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (state.group?.name?.firstOrNull() ?: '?').toString(),
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.group?.name ?: "Unknown Group",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.group?.description ?: "No description",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Settings Section (Admins Only)
                if (state.isAdmin) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Only admins can send messages", Modifier.weight(1f))
                                    Switch(
                                        checked = state.group?.adminsOnlyMessage ?: false,
                                        onCheckedChange = { viewModel.setAdminsOnlyMessage(it) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Member List
                item {
                    Text(
                        text = "Members (${state.members.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(state.members) { user ->
                    MemberItem(
                        user = user,
                        isCurrentAdmin = state.group?.admins?.contains(user.userId) == true,
                        isMe = user.userId == state.currentUserId,
                        canManage = state.isAdmin && user.userId != state.currentUserId,
                        onRemove = { viewModel.removeMember(user.userId) },
                        onPromote = { viewModel.promoteToAdmin(user.userId) },
                        onDemote = { viewModel.demoteFromAdmin(user.userId) }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Member") },
            text = {
                OutlinedTextField(
                    value = emailToAdd,
                    onValueChange = { emailToAdd = it },
                    label = { Text("User Email") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addMember(emailToAdd)
                    showAddMemberDialog = false
                    emailToAdd = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showEditGroupDialog) {
        AlertDialog(
            onDismissRequest = { showEditGroupDialog = false },
            title = { Text("Edit Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateGroupInfo(editedName, editedDescription)
                    showEditGroupDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditGroupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MemberItem(
    user: User,
    isCurrentAdmin: Boolean,
    isMe: Boolean,
    canManage: Boolean,
    onRemove: () -> Unit,
    onPromote: () -> Unit,
    onDemote: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (user.displayName?.firstOrNull() ?: '?').toString(),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "You" else user.displayName ?: "Unknown",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isCurrentAdmin) {
                    Text(
                        text = "Admin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (canManage) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Manage")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (isCurrentAdmin) {
                        DropdownMenuItem(
                            text = { Text("Demote from Admin") },
                            onClick = { onDemote(); showMenu = false }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Promote to Admin") },
                            onClick = { onPromote(); showMenu = false }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Group", color = MaterialTheme.colorScheme.error) },
                        onClick = { onRemove(); showMenu = false }
                    )
                }
            }
        }
    }
}
