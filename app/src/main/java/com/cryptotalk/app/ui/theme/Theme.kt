package com.cryptotalk.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NeonGreen,
    onPrimary = DeepNavy,
    primaryContainer = NeonGreen.copy(alpha = 0.1f),
    onPrimaryContainer = NeonGreen,
    secondary = ElectricPurple,
    onSecondary = ColdWhite,
    tertiary = CyberCyan,
    background = ColdWhite,
    onBackground = DeepNavy,
    surface = ColdWhite,
    onSurface = DeepNavy,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = MutedSlate,
    outline = SilverGray,
    error = CyberRed,
    onError = ColdWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = AmoledBlack,
    primaryContainer = NeonGreen.copy(alpha = 0.15f),
    onPrimaryContainer = NeonGreen,
    secondary = ElectricPurple,
    onSecondary = ColdWhite,
    tertiary = CyberCyan,
    background = AmoledBlack,
    onBackground = ColdWhite,
    surface = DeepNavy,
    onSurface = ColdWhite,
    surfaceVariant = SpaceLayer,
    onSurfaceVariant = SilverGray,
    outline = MutedSlate,
    error = CyberRed,
    onError = ColdWhite
)

@Suppress("DEPRECATION")
@Composable
fun CryptoTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
