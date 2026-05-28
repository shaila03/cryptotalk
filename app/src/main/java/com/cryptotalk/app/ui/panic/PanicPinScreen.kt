package com.cryptotalk.app.ui.panic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun PanicPinScreen(
    navController: NavController,
    viewModel: PanicViewModel,
    isSetup: Boolean = false
) {
    val pin by viewModel.pin.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.setSetupMode(isSetup)
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.Red
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = if (isSetup) "Set Panic PIN" else "Panic Mode Entry",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Red
            )
            
            Text(
                text = if (isSetup) "Entering this PIN will immediately wipe all app data." else "Enter your PIN to continue.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // PIN Display Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { index ->
                    val isFilled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(2.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (isFilled) Color.Red else Color.Gray.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // Numeric Keypad
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("", "0", "C")
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { char ->
                            if (char.isEmpty()) {
                                Spacer(Modifier.size(64.dp))
                            } else {
                                OutlinedButton(
                                    onClick = { 
                                        if (char == "C") viewModel.onPinChange("")
                                        else viewModel.onPinChange(pin + char)
                                    },
                                    modifier = Modifier.size(64.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(text = char, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
            
            if (isSetup) {
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Cancel")
                }
            }
        }
    }
}
