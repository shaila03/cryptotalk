package com.cryptotalk.app.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.cryptotalk.app.data.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DisguiseManager handles hiding the app.
 * It works by changing the app's launcher icon and name (e.g., to a Calculator or Weather app)
 * so snooping people won't know it's a secure chat app.
 */
class DisguiseManager(private val context: Context) {

    // All known component names — must match AndroidManifest.xml exactly
    // First param is ONLY the appId (com.application.cryptotalk), second is the FULL class name including namespace (com.cryptotalk.app)
    private val allAliases = listOf(
        ComponentName("com.application.cryptotalk", "com.cryptotalk.app.RealLauncher"),
        ComponentName("com.application.cryptotalk", "com.cryptotalk.app.CalculatorLauncher"),
        ComponentName("com.application.cryptotalk", "com.cryptotalk.app.WeatherLauncher")
    )

    enum class DisguiseMode(val aliasName: String, val displayName: String) {
        NONE("RealLauncher", "CryptoTalk (Real Icon)"),
        CALCULATOR("CalculatorLauncher", "Calculator"),
        WEATHER("WeatherLauncher", "Weather")
    }

    fun applyDisguise(mode: DisguiseMode) {
        val pm = context.packageManager

        // Disable ALL aliases first
        allAliases.forEach { component ->
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // Enable only the selected alias
        val targetComponent = ComponentName(
            "com.application.cryptotalk",
            "com.cryptotalk.app.${mode.aliasName}"
        )
        pm.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0 // Force launcher refresh by killing app
        )
    }

    fun getCurrentDisguise(settingsDataStore: SettingsDataStore): Flow<DisguiseMode> {
        return settingsDataStore.disguiseMode.map { modeName ->
            DisguiseMode.entries.find { it.name == modeName } ?: DisguiseMode.NONE
        }
    }
}
