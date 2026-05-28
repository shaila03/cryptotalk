package com.cryptotalk.app.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                Text(
                    "Privacy Policy",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "\nLast updated: March 2025\n",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "1. Introduction\n\nCrypto Talk is a secure messaging application designed to protect your privacy with end-to-end encryption. We are committed to being transparent about how we handle your information.\n\n" +
                    "2. Information We Collect\n\nWe collect:\n• Email address (for authentication)\n• Display name (set by you)\n• Encrypted messages (we cannot read them)\n• Device FCM token (for push notifications)\n• Last seen timestamp\n\n" +
                    "3. How We Use Your Information\n\nYour information is used exclusively to:\n• Authenticate you and manage your account\n• Deliver messages securely to your recipients\n• Send push notifications for new messages\n• Maintain your contact list\n\n" +
                    "4. End-to-End Encryption\n\nAll messages in Crypto Talk are encrypted using RSA + AES hybrid encryption. Your private key never leaves your device. Even we cannot read your messages.\n\n" +
                    "5. Data Storage\n\nYour data is stored using Google Firebase. Messages are stored encrypted. We do not sell or share your data with any third parties.\n\n" +
                    "6. Screenshot Protection\n\nTo protect your private conversations, screenshots are disabled in the app by default and cannot be turned off.\n\n" +
                    "7. Data Deletion\n\nYou can delete any conversation at any time from within the app. To permanently delete your account and all associated data, please contact us at cryptotalk.support@gmail.com.\n\n" +
                    "8. Third-Party Services\n\nWe use the following third-party services:\n• Google Firebase (authentication, database, push notifications)\n• Android Keystore (local key storage)\n\n" +
                    "9. Contact\n\nFor privacy concerns, contact us at:\ncryptotalk.support@gmail.com\n\n" +
                    "10. Changes to This Policy\n\nWe may update this policy from time to time. Changes will be reflected in the app with an updated date at the top of this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
