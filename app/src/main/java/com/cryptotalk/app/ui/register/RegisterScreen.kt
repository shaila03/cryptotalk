package com.cryptotalk.app.ui.register

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.ic_app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.height(32.dp))

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
                            "Join Crypto Talk",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "Start your secure journey today.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(32.dp))

                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = { viewModel.updateDisplayName(it) },
                            label = { Text("Full Name") },
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

                        if (state.password.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val strengthColor = when {
                                state.passwordStrength <= 0.25f -> Color.Red
                                state.passwordStrength <= 0.5f -> Color(0xFFFFA500) // Orange
                                state.passwordStrength <= 0.75f -> Color.Yellow
                                else -> Color.Green
                            }
                            val strengthText = when {
                                state.passwordStrength <= 0.25f -> "Weak"
                                state.passwordStrength <= 0.5f -> "Fair"
                                state.passwordStrength <= 0.75f -> "Good"
                                else -> "Strong"
                            }
                            
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LinearProgressIndicator(
                                    progress = { state.passwordStrength },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = strengthColor,
                                    trackColor = strengthColor.copy(alpha = 0.2f)
                                )
                                Text(
                                    text = "Password Strength: $strengthText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = strengthColor,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = { viewModel.updateConfirmPassword(it) },
                            label = { Text("Confirm Password") },
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
                            onClick = { viewModel.signUp() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !state.isLoading
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Text("Sign Up", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(48.dp))
                
                Text(
                    "Your safety is our priority",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "All messages are end-to-end encrypted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
