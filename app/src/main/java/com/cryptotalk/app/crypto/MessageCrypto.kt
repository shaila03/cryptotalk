package com.cryptotalk.app.crypto

import android.util.Base64
import android.util.Log
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MessageCrypto handles all the encryption and decryption for the app.
 * It uses a "Hybrid Encryption" system:
 * 1. Symmetric Encryption (AES-GCM): A new, randomly generated lock (AES key) is made for every single message to lock the actual text. This is super fast.
 * 2. Asymmetric Encryption (RSA): The sender uses the recipient's public lock (Public Key) to lock the AES key. 
 * Only the recipient has the private key (stored safely inside their device) to unlock the AES key, which in turn unlocks the message.
 */
class MessageCrypto {

    private val secureRandom = SecureRandom()

    /**
     * The Encryption Process:
     * 1. Creates a brand new, random AES key just for this specific message.
     * 2. Creates a unique "IV" (Initialization Vector) which acts as a random starting point for the encryption so identical messages don't ever look the same.
     * 3. Locks (Encrypts) the actual message text using the new AES key and the IV.
     * 4. Takes that newly generation AES key and locks it using the recipient's Public Key. If there are multiple people in the chat, it locks the AES key separately for each person using their respective Public Keys.
     * @return EncryptedPayload containing the locked message, the IV, and the locked AES key(s).
     */
    fun encrypt(
        plaintext: String,
        publicKeys: Map<String, PublicKey> // userId -> publicKey
    ): EncryptedPayload {
        val startTime = System.currentTimeMillis()
        
        val aesKey = generateAesKey()
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val ciphertext = encryptAesGcm(plaintext.toByteArray(Charsets.UTF_8), aesKey, iv)
        
        val participantKeys = publicKeys.mapValues { (_, key) ->
            Base64.encodeToString(encryptRsa(aesKey.encoded, key), Base64.NO_WRAP)
        }

        val timeTaken = System.currentTimeMillis() - startTime
        Log.d("PERFORMANCE_TEST", "Encryption (AES+RSA) finished in: ${timeTaken}ms")

        return EncryptedPayload(
            encryptedMessage = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            participantKeys = participantKeys,
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * The Decryption Process:
     * 1. Takes the locked AES key meant for the current user and unlocks it using the user's Private Key (which never leaves their device Keystore).
     * 2. Takes the now unlocked AES key and the IV (random starting point) to unlock the actual message text.
     * 3. Returns the readable message.
     */
    fun decrypt(
        encryptedMessage: String,
        encryptedAESKey: String,
        iv: String,
        privateKey: java.security.PrivateKey
    ): String {
        val startTime = System.currentTimeMillis()

        val encryptedKeyBytes = Base64.decode(encryptedAESKey, Base64.NO_WRAP)
        val aesKeyBytes = decryptRsa(encryptedKeyBytes, privateKey)
        val aesKey: SecretKey = SecretKeySpec(aesKeyBytes, AES_ALGORITHM)
        val ivBytes = Base64.decode(iv, Base64.NO_WRAP)
        val ciphertext = Base64.decode(encryptedMessage, Base64.NO_WRAP)
        val plaintextBytes = decryptAesGcm(ciphertext, aesKey, ivBytes)
        val result = String(plaintextBytes, Charsets.UTF_8)

        val timeTaken = System.currentTimeMillis() - startTime
        Log.d("PERFORMANCE_TEST", "Decryption (RSA+AES) finished in: ${timeTaken}ms")

        return result
    }

    private fun generateAesKey(): SecretKey {
        val keyBytes = ByteArray(AES_KEY_SIZE_BITS / 8).also { secureRandom.nextBytes(it) }
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }

    private fun encryptAesGcm(plaintext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    private fun decryptAesGcm(ciphertext: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e("MessageCrypto", "AES-GCM decryption failed: ${e.message}. Key size: ${key.encoded?.size}, IV size: ${iv.size}, Ciphertext size: ${ciphertext.size}", e)
            throw e
        }
    }

    private fun encryptRsa(plaintext: ByteArray, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(plaintext)
    }

    private fun decryptRsa(ciphertext: ByteArray, privateKey: java.security.PrivateKey): ByteArray {
        return try {
            val cipher = Cipher.getInstance(RSA_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            Log.e("MessageCrypto", "RSA decryption failed: ${e.message}", e)
            throw e
        }
    }

    data class EncryptedPayload(
        val encryptedMessage: String,
        val participantKeys: Map<String, String>,
        val iv: String,
        val encryptedAESKey: String? = null,
        val encryptedAESKeySender: String? = null
    )

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val AES_KEY_SIZE_BITS = 256
        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        // Use widely supported RSA PKCS1 padding with Keystore
        private const val RSA_TRANSFORM = "RSA/ECB/PKCS1Padding"
    }
}
