package com.cryptotalk.app.ui.lock

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockScreen(
    correctPin: String?,
    decoyPin: String?,
    onAuthenticated: (isDecoy: Boolean) -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val shakeOffset by animateDpAsState(
        targetValue = if (isError) 10.dp else 0.dp,
        animationSpec = repeatable(
            iterations = 3,
            animation = tween(durationMillis = 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        finishedListener = { isError = false },
        label = "shake"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .offset(x = shakeOffset)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .blur(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "CryptoTalk Locked",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Text(
                "Enter PIN to unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(Modifier.height(48.dp))

            // PIN Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    val filled = index < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) Color.White else Color.White.copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(64.dp))

            // Num Pad
            val numbers = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "back")
            )

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                numbers.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { item ->
                            if (item.isEmpty()) {
                                Box(modifier = Modifier.size(72.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .clickable {
                                            if (item == "back") {
                                                if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            } else if (enteredPin.length < 4) {
                                                enteredPin += item
                                                if (enteredPin.length == 4) {
                                                    // Verify
                                                    when (enteredPin) {
                                                        correctPin -> onAuthenticated(false)
                                                        decoyPin -> onAuthenticated(true)
                                                        else -> {
                                                            isError = true
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item == "back") {
                                        Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = Color.White)
                                    } else {
                                        Text(
                                            item,
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Light
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
