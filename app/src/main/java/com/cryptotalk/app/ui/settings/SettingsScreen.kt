package com.cryptotalk.app.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cryptotalk.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userEmail: String,
    userName: String = "",
    onBack: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onPrivacyPolicy: () -> Unit,
    onSignOut: () -> Unit
) {
    val encryptionEnabled by viewModel.encryptionEnabled.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
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
            Column(
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
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
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Account",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Signed in as",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            if (userName.isNotBlank()) {
                                Text(
                                    userName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                userEmail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onSignOut,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "End-to-end encryption",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = encryptionEnabled,
                            onCheckedChange = { viewModel.setEncryptionEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Text(
                        text = if (encryptionEnabled) "Messages are encrypted before sending." else "Messages are sent as plain text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            var showPinDialog by remember { mutableStateOf(false) }
            var pinType by remember { mutableStateOf("Main") } // "Main" or "Panic"
            var tempPin by remember { mutableStateOf("") }

            if (showPinDialog) {
                AlertDialog(
                    onDismissRequest = { showPinDialog = false },
                    title = { Text("Set $pinType PIN") },
                    text = {
                        Column {
                            Text("Enter a 4-digit PIN for app lock.",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = tempPin,
                                onValueChange = { if (it.length <= 4) tempPin = it },
                                label = { Text("PIN") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword
                                )
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (tempPin.length == 4) {
                                viewModel.setAppPin(tempPin)
                                showPinDialog = false
                            }
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
                    }
                )
            }

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
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Security PINs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val currentAppPin by viewModel.appPin.collectAsState()
                    SettingsItem(
                        title = "Master App PIN",
                        subtitle = if (currentAppPin != null) "PIN is set (Tap to change)" else "Not set",
                        icon = Icons.Default.Lock,
                        onClick = {
                            pinType = "Main"
                            tempPin = ""
                            showPinDialog = true
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val biometricEnabled by viewModel.biometricEnabled.collectAsState()
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
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "App Lock",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                    Text(
                        text = "Require fingerprint or face ID to open the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hideNotificationContent by viewModel.hideNotificationContent.collectAsState()

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
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.privacy_security),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    SettingsItem(
                        title = stringResource(R.string.security_dashboard),
                        subtitle = "View your security health & encryption status",
                        icon = Icons.Default.Shield,
                        onClick = onNavigateToSecurity
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsItem(
                        title = "Emergency Panic Mode",
                        subtitle = "Immediately wipe all data and sign out",
                        icon = Icons.Default.Shield,
                        onClick = { viewModel.triggerPanicMode() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsItem(
                        title = "Privacy Notifications",
                        subtitle = "Hide message content in system notifications",
                        icon = Icons.Default.NotificationsNone,
                        onClick = { viewModel.setHideNotificationContent(!hideNotificationContent) },
                        trailing = {
                            Switch(
                                checked = hideNotificationContent,
                                onCheckedChange = { viewModel.setHideNotificationContent(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val volumeExitEnabled by viewModel.volumeExitEnabled.collectAsState()
            val performanceModeEnabled by viewModel.performanceModeEnabled.collectAsState()

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
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Advanced & Performance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Triple-Click Volume Exit", style = MaterialTheme.typography.bodyLarge)
                            Text("Click volume buttons 3 times fast to close app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = volumeExitEnabled,
                            onCheckedChange = { viewModel.setVolumeExitEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Performance Mode", style = MaterialTheme.typography.bodyLarge)
                            Text("Reduce lag in heavy chats (restricts history loading)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = performanceModeEnabled,
                            onCheckedChange = { viewModel.setPerformanceModeEnabled(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val typingEnabled by viewModel.typingIndicatorEnabled.collectAsState()
            val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsState()

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
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Stealth Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Typing Indicators", style = MaterialTheme.typography.bodyLarge)
                            Text("Show when you are typing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = typingEnabled,
                            onCheckedChange = { viewModel.setTypingIndicatorEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Read Receipts", style = MaterialTheme.typography.bodyLarge)
                            Text("Allow others to see when you've read messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = readReceiptsEnabled,
                            onCheckedChange = { viewModel.setReadReceiptsEnabled(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onPrivacyPolicy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Privacy Policy")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        0.5.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
                        RoundedCornerShape(20.dp)
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                SettingsItem(
                    title = "Logout",
                    subtitle = "Sign out of your account",
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = onSignOut
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Makers",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Shaila Mandkulkar, Karan Lingayat, Parth Kelaskar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
