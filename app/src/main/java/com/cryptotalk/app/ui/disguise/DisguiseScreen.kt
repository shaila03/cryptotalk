@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.cryptotalk.app.ui.disguise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cryptotalk.app.disguise.DisguiseManager

@Composable
fun DisguiseScreen(
    disguiseMode: DisguiseManager.DisguiseMode,
    unlockCode: String,
    onUnlocked: () -> Unit
) {
    when (disguiseMode) {
        DisguiseManager.DisguiseMode.CALCULATOR ->
            CalculatorDisguiseScreen(unlockCode = unlockCode, onUnlocked = onUnlocked)
        DisguiseManager.DisguiseMode.WEATHER ->
            WeatherDisguiseScreen(unlockCode = unlockCode, onUnlocked = onUnlocked)
        else -> onUnlocked()
    }
}

@Composable
fun CalculatorDisguiseScreen(
    unlockCode: String,
    onUnlocked: () -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var operator by remember { mutableStateOf<String?>(null) }
    var shouldResetDisplay by remember { mutableStateOf(false) }

    fun calculate() {
        val val1 = operand1 ?: return
        val val2 = display.toDoubleOrNull() ?: return
        val resultValue = when (operator) {
            "+" -> val1 + val2
            "−" -> val1 - val2
            "×" -> val1 * val2
            "÷" -> if (val2 != 0.0) val1 / val2 else Double.NaN
            else -> val2
        }
        display = if (resultValue.isNaN()) "Error" else {
            if (resultValue % 1.0 == 0.0) resultValue.toInt().toString()
            else resultValue.toString()
        }
        operand1 = null
        operator = null
    }

    val buttons = listOf(
        listOf("C", "+/-", "%", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "=")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
        }

        // Buttons
        buttons.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { label ->
                    val isOperator = label in listOf("÷", "×", "−", "+", "=")
                    val isSpecial = label in listOf("C", "+/-", "%")
                    val bgColor = when {
                        isOperator -> Color(0xFFFF9F0A)
                        isSpecial  -> Color(0xFF636366)
                        else       -> Color(0xFF48484A)
                    }
                    val buttonModifier = if (label == "0") {
                        Modifier.weight(2f).aspectRatio(2f).padding(4.dp)
                    } else {
                        Modifier.weight(1f).aspectRatio(1f).padding(4.dp)
                    }

                    Box(
                        modifier = buttonModifier
                            .clip(CircleShape)
                            .background(bgColor)
                            .clickable {
                                when {
                                    label == "C" -> {
                                        display = "0"
                                        operand1 = null
                                        operator = null
                                    }
                                    label == "=" -> {
                                        if (display.trim() == unlockCode.trim()) {
                                            onUnlocked()
                                        } else if (operator != null) {
                                            calculate()
                                        }
                                    }
                                    label in listOf("+", "−", "×", "÷") -> {
                                        if (operator != null) calculate()
                                        operand1 = display.toDoubleOrNull()
                                        operator = label
                                        shouldResetDisplay = true
                                    }
                                    label == "+/-" -> {
                                        display = if (display.startsWith("-")) display.substring(1) else "-$display"
                                    }
                                    label == "%" -> {
                                        val v = display.toDoubleOrNull() ?: 0.0
                                        display = (v / 100.0).toString()
                                    }
                                    else -> { // Numbers and .
                                        if (display == "0" || shouldResetDisplay || display == "Error") {
                                            display = label
                                            shouldResetDisplay = false
                                        } else {
                                            display += label
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}


@Composable
fun WeatherDisguiseScreen(
    unlockCode: String,
    onUnlocked: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val RAPID_TAP_THRESHOLD_MS = 500L
    val TAPS_REQUIRED = 5

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1565C0), Color(0xFF42A5F5))
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Text(
            text = "Mumbai",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        Text(
            text = "Partly Cloudy",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "28°",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp),
            color = Color.White,
            modifier = Modifier.clickable {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < RAPID_TAP_THRESHOLD_MS) {
                    tapCount++
                    if (tapCount >= TAPS_REQUIRED) {
                        onUnlocked()
                    }
                } else {
                    tapCount = 1
                }
                lastTapTime = now
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "H:33°  L:24°",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(40.dp))
        // Fake hourly forecast
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp)) {
            items(listOf("Now","1PM","2PM","3PM","4PM","5PM","6PM")) { hour ->
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(hour, color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(Icons.Default.WbSunny, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${(26..32).random()}°", color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

