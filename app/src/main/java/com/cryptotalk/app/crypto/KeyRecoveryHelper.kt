package com.cryptotalk.app.crypto

import android.util.Base64
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * KeyRecoveryHelper lets users back up their Secret (Private) Key to the cloud safely.
 * Since the Private Key is super sensitive, we CANNOT just upload it directly.
 * Instead, we ask the user for a PIN code, and we use that PIN to create a strong lock.
 * We lock the Private Key with this PIN-based lock, and then upload the locked version.
 */
object KeyRecoveryHelper {

    private const val PBKDF2_ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12

    /**
     * Takes a simple PIN code (like "1234") and turns it into a complex, mathematical "key".
     * This makes it much harder for hackers to guess the lock.
     */
    private fun deriveKey(pin: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec: KeySpec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    /**
     * Takes the user's Secret Key and locks it up using their PIN.
     * It adds some random noise ("salt" and "iv") to make the lock even stronger.
     * The final result is a text string that is safe to upload to the internet.
     */
    fun encryptPrivateKeyBackup(privateKeyBytes: ByteArray, pin: String): String {
        val salt = ByteArray(SALT_LENGTH).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }

        val secretKey = deriveKey(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(privateKeyBytes)

        // Format: Base64(salt) + ":" + Base64(iv) + ":" + Base64(ciphertext)
        return "${Base64.encodeToString(salt, Base64.NO_WRAP)}:" +
               "${Base64.encodeToString(iv, Base64.NO_WRAP)}:" +
               Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /**
     * Takes the locked data downloaded from the cloud and the user's PIN.
     * If the PIN is correct, it unlocks the data and returns the original Secret Key.
     */
    fun decryptPrivateKeyBackup(backupPayload: String, pin: String): ByteArray {
        val parts = backupPayload.split(":")
        if (parts.size != 3) throw IllegalArgumentException("Invalid backup payload format")

        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

        val secretKey = deriveKey(pin, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}
