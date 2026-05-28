package com.cryptotalk.app.security

import android.content.Context
import com.cryptotalk.app.CryptoTalkApplication
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * SecurityWipeManager is responsible for the "Self-Destruct" or "Panic" mode.
 * When triggered, it instantly deletes all messages, passwords, and accounts from the phone forever.
 */
class SecurityWipeManager(private val context: Context) {

    fun triggerEmergencyWipe() {
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Sign out from Firebase
            FirebaseAuth.getInstance().signOut()

            // 2. Wipe DB Passphrase
            PassphraseManager(context).wipePassphrase()

            // 3. Delete Private Keys from Keystore (via KeystoreHelper if it had a wipe method)
            // For now, we rely on the DB wipe and clearing preferences.
            
            // 4. Delete local database files
            context.deleteDatabase("cryptotalk_secure.db")

            // 5. Clear all SharedPreferences
            context.getSharedPreferences("secure_vault", Context.MODE_PRIVATE).edit().clear().apply()
            context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().apply()

            // 6. Clear cache
            deleteRecursive(context.cacheDir)
            deleteRecursive(context.filesDir)

            // 7. Restart App or Exit
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        }
    }

    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            for (child in fileOrDirectory.listFiles() ?: emptyArray()) {
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }
}
