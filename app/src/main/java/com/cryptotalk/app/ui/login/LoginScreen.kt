package com.cryptotalk.app.ui.login

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptotalk.app.R
import androidx.compose.ui.platform.LocalContext
import com.cryptotalk.app.navigation.NavRoutes
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import android.net.Uri

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onNavigate: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LaunchedEffect(state.navigateTo) {
        state.navigateTo?.let {
            onNavigate(it)
            viewModel.clearNavigation()
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(600),
        label = "alpha"
    )


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .alpha(contentAlpha)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))

            // App Logo
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "Crypto Talk",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                "Secure Messaging Reimagined",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Welcome Back",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = { viewModel.updateEmail(it) },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    state.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = { viewModel.signIn() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Sign In", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(onClick = { onNavigate(NavRoutes.REGISTER) }) {
                        Text("Don't have an account? Sign Up")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "App Makers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Shaila Mandkulkar, Karan Lingayat, Parth Kelaskar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                val context = LocalContext.current
                val primaryColor = MaterialTheme.colorScheme.primary
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable {
                            val customTabsIntent = CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .setDefaultColorSchemeParams(
                                    androidx.browser.customtabs.CustomTabColorSchemeParams.Builder()
                                        .setToolbarColor(primaryColor.toArgb())
                                        .build()
                                )
                                .build()
                            customTabsIntent.launchUrl(context, Uri.parse("https://cryptotalk.lovable.app"))
                        }
                )
            }
        }
    }

    if (state.showRestoreDialog) {
        var restorePin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { /* forced action */ },
            title = { Text("Restore Encryption Key") },
            text = {
                Column {
                    Text(
                        "An encrypted backup of your key was found on the server. Enter your 6-digit Recovery PIN to securely restore your chat history.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = restorePin,
                        onValueChange = { if (it.length <= 6) restorePin = it },
                        label = { Text("Recovery PIN") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restorePin.length > 0 && restorePin.length < 6) {
                        Text("PIN must be 6 digits", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreKey(restorePin) },
                    enabled = restorePin.length == 6 && !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Restore")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.skipRestore() }, enabled = !state.isLoading) {
                    Text("Skip & Create New Key", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }
}
