@file:OptIn(ExperimentalMaterial3Api::class)

package com.cryptotalk.app.ui.disguise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptotalk.app.disguise.DisguiseManager

@Composable
fun DisguiseSettingsScreen(
    viewModel: DisguiseSettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val currentDisguise by viewModel.disguiseMode.collectAsState()
    val unlockCode by viewModel.unlockCode.collectAsState()
    var showUnlockCodeDialog by remember { mutableStateOf(false) }
    var newUnlockCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    val disguiseOptions = listOf(
        DisguiseManager.DisguiseMode.NONE to "No Disguise (Real CryptoTalk Icon)",
        DisguiseManager.DisguiseMode.CALCULATOR to "Calculator App",
        DisguiseManager.DisguiseMode.WEATHER to "Weather App"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Disguise") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Disguise Mode hides CryptoTalk behind a fake app icon. " +
                                "Remember your unlock code. If you forget it, you cannot access CryptoTalk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Text(
                    "Choose Disguise",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(disguiseOptions) { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setDisguiseMode(mode, context)
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentDisguise == mode.name,
                        onClick = { viewModel.setDisguiseMode(mode, context) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Unlock Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This code is what you enter in the disguise screen to reveal CryptoTalk.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showUnlockCodeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Unlock Code (Current: ${unlockCode.take(1)}***)")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "How to use Disguise",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "1. Select a disguise icon above (e.g., Calculator).\n" +
                            "2. Your phone will now show that fake app instead of CryptoTalk.\n" +
                            "3. To open CryptoTalk, open the fake app and enter your 4-digit Unlock Code.\n" +
                            "4. In the Calculator, type the code and press '='. In the Weather app, tap the temperature 5 times rapidly.",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        if (showUnlockCodeDialog) {
            AlertDialog(
                onDismissRequest = { showUnlockCodeDialog = false },
                title = { Text("Set Unlock Code") },
                text = {
                    Column {
                        Text(
                            "Enter a 4-digit code you will type in the disguise screen to reveal CryptoTalk.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = newUnlockCode,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    newUnlockCode = it
                                }
                            },
                            label = { Text("4-digit code") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newUnlockCode.length == 4) {
                                viewModel.setUnlockCode(newUnlockCode)
                                showUnlockCodeDialog = false
                                newUnlockCode = ""
                            }
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showUnlockCodeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
