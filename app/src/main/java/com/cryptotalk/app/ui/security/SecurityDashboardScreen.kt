package com.cryptotalk.app.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cryptotalk.app.R
import com.cryptotalk.app.ui.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDashboardScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToDisguise: () -> Unit
) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val screenshotProtection by viewModel.screenshotProtectionEnabled.collectAsState()
    val hideNotifications by viewModel.hideNotificationContent.collectAsState()
    val readReceipts by viewModel.readReceiptsEnabled.collectAsState()
    val typingIndicator by viewModel.typingIndicatorEnabled.collectAsState()
    val disguiseMode by viewModel.disguiseMode.collectAsState(initial = "NONE")
    val context = androidx.compose.ui.platform.LocalContext.current
    var showWipeDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(stringResource(R.string.security_dashboard)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SecurityStatusCard(
                title = stringResource(R.string.authentication),
                status = if (biometricEnabled) "Secured" else "Weak",
                icon = Icons.Default.Fingerprint,
                isGood = biometricEnabled,
                description = "Biometric lock prevents unauthorized physical access to your messages."
            )

            SecurityStatusCard(
                title = stringResource(R.string.encryption),
                status = "Military Grade",
                icon = Icons.Default.EnhancedEncryption,
                isGood = true,
                description = "Messages are encrypted using AES-256 for the content and RSA-2048 for key exchange."
            )

            SecurityStatusCard(
                title = stringResource(R.string.privacy_protection),
                status = "High",
                icon = Icons.Default.PhonelinkLock,
                isGood = true,
                description = "Screenshots are blocked to prevent data leakage outside the app."
            )

            SecurityStatusCard(
                title = stringResource(R.string.notification_privacy),
                status = if (hideNotifications) "Private" else "Standard",
                icon = Icons.Default.NotificationsOff,
                isGood = hideNotifications,
                description = "Hiding notification content ensures your messages stay private on the lock screen."
            )

            SecurityStatusCard(
                title = "App Disguise",
                status = if (disguiseMode != "NONE") "Active ($disguiseMode)" else "Inactive",
                icon = Icons.Default.Masks,
                isGood = disguiseMode != "NONE",
                description = "Hide CryptoTalk behind a fake icon like a Calculator or Notes app.",
                onClick = onNavigateToDisguise
            )
            SecurityStatusCard(
                title = "Read Receipts",
                status = if (readReceipts) "Enabled" else "Disabled",
                icon = Icons.Default.DoneAll,
                isGood = true,
                description = "Others can see when you have read their messages.",
                showSwitch = true,
                checked = readReceipts,
                onCheckedChange = { viewModel.setReadReceiptsEnabled(it) }
            )

            SecurityStatusCard(
                title = "Typing Indicator",
                status = if (typingIndicator) "Enabled" else "Disabled",
                icon = Icons.Default.Keyboard,
                isGood = true,
                description = "Others can see when you are typing.",
                showSwitch = true,
                checked = typingIndicator,
                onCheckedChange = { viewModel.setTypingIndicatorEnabled(it) }
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Overall Security Health",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val score = listOf(biometricEnabled, true, screenshotProtection, hideNotifications).count { it } + (if (readReceipts) 0 else 1) + (if (typingIndicator) 0 else 1)
                    LinearProgressIndicator(
                        progress = { score / 4f },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (score >= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        if (score == 4) "Your account is fully hardened." else "Update settings to improve security.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            val backupStatus by viewModel.backupStatus.collectAsState()
            if (backupStatus != null) {
                Text(
                    text = backupStatus!!,
                    color = if (backupStatus!!.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            var showBackupDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            Button(
                onClick = { 
                    viewModel.clearBackupStatus()
                    showBackupDialog = true 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Backup Encryption Key to Cloud")
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { showWipeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("Clear All Data (Secure Wipe)")
            }

            if (showBackupDialog) {
                var backupPin by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showBackupDialog = false },
                    title = { Text("Secure Cloud Backup") },
                    text = { 
                        Column {
                            Text("Securely backup your encryption key to the cloud. You will need this PIN to restore your messages if you reinstall the app.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = backupPin,
                                onValueChange = { if (it.length <= 6) backupPin = it },
                                label = { Text("Enter 6-digit PIN") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.backupEncryptionKey(backupPin)
                                showBackupDialog = false
                            },
                            enabled = backupPin.length == 6
                        ) { Text("Backup Now") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupDialog = false }) { Text("Cancel") }
                    }
                )
            }

            if (showWipeDialog) {
                AlertDialog(
                    onDismissRequest = { showWipeDialog = false },
                    title = { Text("Secure Wipe") },
                    text = { Text("This will permanently clear all local settings, nicknames, and session data. You will be signed out. This cannot be undone.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllData()
                                showWipeDialog = false
                                (context as? android.app.Activity)?.finishAffinity()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Wipe Everything") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
fun SecurityStatusCard(
    title: String,
    status: String,
    icon: ImageVector,
    isGood: Boolean,
    description: String,
    showSwitch: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (showSwitch) {
                        Switch(checked = checked, onCheckedChange = onCheckedChange)
                    } else {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
