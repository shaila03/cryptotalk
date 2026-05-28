package com.cryptotalk.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.cryptotalk.app.notification.NotificationHelper
import com.cryptotalk.app.security.BiometricHelper
import com.cryptotalk.app.security.IntegrityCheck
import com.cryptotalk.app.ui.CryptoTalkNavHost
import com.cryptotalk.app.ui.theme.CryptoTalkTheme
import com.cryptotalk.app.disguise.DisguiseManager
import com.cryptotalk.app.ui.disguise.DisguiseScreen
import com.cryptotalk.app.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore

/**
 * The main starting point of the CryptoTalk app.
 * This screen handles initial security checks, checking if the user is logged in, and displaying the lock screen.
 */
class MainActivity : FragmentActivity() {

    // Listens for changes to the device ID in the database to prevent multiple logins
    private var deviceIdListener: ListenerRegistration? = null
    // Asks the user for permission to show notifications
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not, proceed */ }

    // State variables that control what the user sees on the screen
    private var isAuthenticated by mutableStateOf(false) // True if the user has successfully unlocked the app
    private var showRootWarning by mutableStateOf(false) // True if the phone is rooted (potentially insecure)
    private var showKeyResetDialog by mutableStateOf(false) // True if encryption keys were wiped and remade

    override fun onCreate(savedInstanceState: Bundle?) {
        // Disguise Mode: The app can hide itself as a different app (like a calculator).
        // The "bypass_disguise" flag tells the app to skip the fake screen and show the real login.
        val bypassDisguise = intent.getBooleanExtra("bypass_disguise", false)
        val settingsDataStore = SettingsDataStore(applicationContext)
        var disguiseModeStr = "NONE"
        var unlockCode = "0000"
        
        runBlocking {
            disguiseModeStr = settingsDataStore.disguiseMode.first()
            unlockCode = settingsDataStore.disguiseUnlockCode.first()
        }
        
        val disguiseMode = DisguiseManager.DisguiseMode.entries
            .find { it.name == disguiseModeStr } ?: DisguiseManager.DisguiseMode.NONE

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = applicationContext as CryptoTalkApplication
        val settingsRepository = app.settingsRepository

        if (IntegrityCheck.isDeviceRooted()) {
            showRootWarning = true
        }

        // Security Feature: Prevent taking screenshots or screen recordings anywhere in the app.
        // This stops other malicious apps from spying on the screen.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // Create notification channel (no-op on older APIs)
        NotificationHelper.createNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            CryptoTalkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (disguiseMode != DisguiseManager.DisguiseMode.NONE && !bypassDisguise) {
                        DisguiseScreen(
                            disguiseMode = disguiseMode,
                            unlockCode = unlockCode,
                            onUnlocked = {
                                // Restart MainActivity with bypass flag
                                val intent = Intent(this, MainActivity::class.java)
                                intent.putExtra("bypass_disguise", true)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                startActivity(intent)
                                finish()
                            }
                        )
                    } else if (isAuthenticated) {
                        // If unlocked, show the main app navigation
                        CryptoTalkNavHost(app = app)
                    } else {
                        // User is NOT verified yet. Show the lock screen.
                        val appPin by settingsRepository.appPin.collectAsState(initial = null)
                        val decoyPin by settingsRepository.decoyPin.collectAsState(initial = null)
                        
                        if (appPin != null) {
                            com.cryptotalk.app.ui.lock.LockScreen(
                                correctPin = appPin!!,
                                decoyPin = decoyPin,
                                onAuthenticated = { isDecoy ->
                                    settingsRepository.setDecoySession(isDecoy)
                                    isAuthenticated = true
                                }
                            )
                        } else {
                            // No PIN set — check biometric
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) { /* fallback empty lock surface */ }
                        }
                    }

                    if (showRootWarning) {
                        AlertDialog(
                            onDismissRequest = { showRootWarning = false },
                            title = { Text("Security Warning") },
                            text = { Text("Your device appears to be rooted or running in a compromised environment. Security features may be less effective.") },
                            confirmButton = {
                                TextButton(onClick = { showRootWarning = false }) { Text("I Understand") }
                            }
                        )
                    }

                    // Warn the user securely if their encryption keys had to be regenerated
                    // (e.g., app reinstall where keystore was wiped but firestore auth remained)
                    if (showKeyResetDialog) {
                        AlertDialog(
                            onDismissRequest = { showKeyResetDialog = false },
                            title = { Text("Encryption Keys Reset") },
                            text = { Text("Your encryption keys were reset. Previous messages cannot be recovered. New messages will be secure.") },
                            confirmButton = {
                                TextButton(onClick = { showKeyResetDialog = false }) { Text("OK") }
                            }
                        )
                    }
                }
            }
        }

        // Run first biometric check in onCreate
        checkBiometricAuth()

        // Phase 3: Single Device Login Enforcement
        // If the user logs into their account on a different phone, this phone will automatically log them out.
        lifecycleScope.launch {
            app.authRepository.authState.collect { user ->
                deviceIdListener?.remove()
                if (user != null) {
                    val currentDeviceId = com.cryptotalk.app.util.DeviceIdHelper.getDeviceId(this@MainActivity)
                    deviceIdListener = FirebaseFirestore.getInstance().collection("users").document(user.uid)
                        .collection("private").document("profile")
                        .addSnapshotListener { snapshot, _ ->
                            val dbDeviceId = snapshot?.getString("deviceId")
                            if (dbDeviceId != null && dbDeviceId != currentDeviceId) {
                                // Another device logged in and took over the deviceId. Sign out instantly.
                                app.authRepository.signOut()
                                val intent = Intent(this@MainActivity, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                startActivity(intent)
                                finish()
                            }
                        }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When the app comes back to the foreground, mark the user as 'Online' in the database
        val app = applicationContext as CryptoTalkApplication
        val uid = app.authRepository.currentUser?.uid
        if (uid != null) {
            // Status: Update user online status to 'true' in Firestore when the app is active
            lifecycleScope.launch {
                app.userRepository.setOnlineStatus(uid, true)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val app = applicationContext as CryptoTalkApplication
        val uid = app.authRepository.currentUser?.uid
        if (uid != null) {
            // Status: Update user online status to 'false' in Firestore when app goes to background
            lifecycleScope.launch {
                app.userRepository.setOnlineStatus(uid, false)
            }
        }
    }

    override fun onRestart() {
        super.onRestart()
        checkBiometricAuth()
    }

    fun signalKeyWasReset() {
        showKeyResetDialog = true
    }

    private var isVolumeExitEnabled = false
    private val volumePressTimestamps = mutableListOf<Long>()

    private fun checkBiometricAuth() {
        val app = applicationContext as CryptoTalkApplication
        val settingsRepository = app.settingsRepository
        val biometricHelper = BiometricHelper(this)

        lifecycleScope.launch {
            settingsRepository.volumeExitEnabled.collect { isEnabled ->
                isVolumeExitEnabled = isEnabled
            }
        }

        lifecycleScope.launch {
            val isBioEnabled = settingsRepository.isBiometricEnabled()
            if (isBioEnabled && biometricHelper.isBiometricAvailable()) {
                isAuthenticated = false
                biometricHelper.showBiometricPrompt(
                    activity = this@MainActivity,
                    onSuccess = { isAuthenticated = true },
                    onError = { /* Stay locked */ }
                )
            } else {
                isAuthenticated = true
            }
        }
    }

    /**
     * Security Feature: Panic Exit
     * If the user presses the volume buttons 3 times quickly, the app instantly closes 
     * and removes itself from the phone's recent apps list.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (isVolumeExitEnabled && event.action == android.view.KeyEvent.ACTION_DOWN) {
            if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || 
                event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
                
                val now = System.currentTimeMillis()
                // Remove timestamps older than 1.5 seconds
                volumePressTimestamps.removeAll { now - it > 1500 }
                volumePressTimestamps.add(now)

                if (volumePressTimestamps.size >= 3) {
                    volumePressTimestamps.clear()
                    // Instantly destroy the app entirely
                    finishAndRemoveTask()
                    kotlin.system.exitProcess(0)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
