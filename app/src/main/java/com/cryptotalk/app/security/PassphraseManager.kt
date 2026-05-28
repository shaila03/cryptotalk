package com.cryptotalk.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import android.util.Base64

/**
 * PassphraseManager creates and stores the master password used to lock the local database.
 * This password is automatically generated, extremely long, and hidden securely inside the phone's hardware.
 */
class PassphraseManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrGeneratePassphrase(): ByteArray {
        val storedPassphrase = sharedPrefs.getString(KEY_DB_PASSPHRASE, null)
        return if (storedPassphrase != null) {
            Base64.decode(storedPassphrase, Base64.NO_WRAP)
        } else {
            val newPassphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val encoded = Base64.encodeToString(newPassphrase, Base64.NO_WRAP)
            sharedPrefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
            newPassphrase
        }
    }

    fun wipePassphrase() {
        sharedPrefs.edit().remove(KEY_DB_PASSPHRASE).apply()
    }

    companion object {
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
