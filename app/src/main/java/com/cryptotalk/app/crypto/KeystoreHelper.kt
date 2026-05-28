package com.cryptotalk.app.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.cryptotalk.app.utils.PerformanceUtils


/**
 * KeystoreHelper is responsible for securely saving the user's Secret (Private) Key.
 * It uses the "AndroidKeyStore" (a highly secure hardware vault on the phone) to create a "master lock" (AES key).
 * It then uses this master lock to encrypt the user's actual keys before saving them.
 * This ensures that even if someone steals the phone's data, they cannot read the keys.
 */
class KeystoreHelper(private val context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val prefs: SharedPreferences = context.getSharedPreferences("crypto_keys_prefs", Context.MODE_PRIVATE)

    /**
     * Tries to find the user's existing keys. If they don't exist, it creates a new brand new pair.
     * Returns a pair containing both the Public Key and the Private Key.
     */
    fun getOrCreateRsaKeyPair(userId: String): Pair<PublicKey, PrivateKey> {
        val pub = getPublicKey(userId)
        val priv = getPrivateKey(userId)
        if (pub != null && priv != null) {
            return pub to priv
        }
        return generateAndStoreRsaKeyPair(userId)
    }

    /**
     * Saves an existing keypair to the phone (for example, when restoring from a backup).
     * It secures the private key with the master lock before saving it to the device's storage.
     */
    fun importRsaKeyPair(userId: String, publicKeyBytes: ByteArray, privateKeyBytes: ByteArray): Pair<PublicKey, PrivateKey> {
        ensureAesWrapperKey(userId)
        
        val encryptedPriv = encryptWithWrapperKey(userId, privateKeyBytes)
        
        prefs.edit()
            .putString("pub_${userId}", Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP))
            .putString("priv_${userId}", Base64.encodeToString(encryptedPriv, Base64.NO_WRAP))
            .apply()
            
        return getPublicKey(userId)!! to getPrivateKey(userId)!!
    }

    /**
     * Retrieves the encrypted Private Key from storage, unlocks it using the master lock,
     * and returns the usable PrivateKey object.
     */
    fun getPrivateKey(userId: String): PrivateKey? {
        val b64 = prefs.getString("priv_${userId}", null) ?: return null
        return try {
            val encryptedBytes = Base64.decode(b64, Base64.NO_WRAP)
            val decryptedBytes = decryptWithWrapperKey(userId, encryptedBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(decryptedBytes))
        } catch (e: Exception) {
            android.util.Log.e("KeystoreHelper", "Failed to retrieve private key for $userId: ${e.message}", e)
            null
        }
    }

    /**
     * Retrieves the private key and unlocks it into raw bytes.
     * This is used specifically when we need to back up the key to the cloud.
     */
    fun exportPrivateKeyBytes(userId: String): ByteArray? {
        val b64 = prefs.getString("priv_${userId}", null) ?: return null
        return try {
            val encryptedBytes = Base64.decode(b64, Base64.NO_WRAP)
            decryptWithWrapperKey(userId, encryptedBytes)
        } catch (e: Exception) { null }
    }

    /**
     * Retrieves the Public Key from storage. Since it's meant to be shared, it is not encrypted.
     */
    fun getPublicKey(userId: String): PublicKey? {
        val b64 = prefs.getString("pub_${userId}", null) ?: return null
        return try {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: Exception) {
            android.util.Log.e("KeystoreHelper", "Failed to retrieve public key for $userId: ${e.message}", e)
            null
        }
    }

    fun hasKeyPair(userId: String): Boolean = getPrivateKey(userId) != null

    /**
     * Generates a completely new set of RSA keys (one Public, one Private).
     * The size is 4096 bits, making it extremely secure.
     * It then saves them securely using the import function.
     */
    private fun generateAndStoreRsaKeyPair(userId: String): Pair<PublicKey, PrivateKey> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA") // Default software generator
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        val keyPair = keyPairGenerator.generateKeyPair()

        importRsaKeyPair(userId, keyPair.public.encoded, keyPair.private.encoded)
        return keyPair.public to keyPair.private
    }

    /**
     * Checks if our "master lock" (AES key) exists in the secure AndroidKeyStore.
     * If it doesn't, it creates a new one. This lock is used to protect the Private Key.
     */
    private fun ensureAesWrapperKey(userId: String) {
        val alias = "cryptotalk_wrapper_${userId.hashCode()}"
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }

    /**
     * Takes raw data (like our Private Key) and scrambles (encrypts) it using the master lock.
     */
    private fun encryptWithWrapperKey(userId: String, plaintext: ByteArray): ByteArray = PerformanceUtils.measureTime("encryptWithWrapperKey") {
        val alias = "cryptotalk_wrapper_${userId.hashCode()}"
        val secretKey = keyStore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        
        // Return IV + Ciphertext
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        result
    }


    /**
     * Takes scrambled data and unscrambles (decrypts) it back to its original form using the master lock.
     */
    private fun decryptWithWrapperKey(userId: String, encryptedData: ByteArray): ByteArray = PerformanceUtils.measureTime("decryptWithWrapperKey") {
        val alias = "cryptotalk_wrapper_${userId.hashCode()}"
        val secretKey = keyStore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        val iv = ByteArray(12)
        System.arraycopy(encryptedData, 0, iv, 0, 12)
        val ciphertext = ByteArray(encryptedData.size - 12)
        System.arraycopy(encryptedData, 12, ciphertext, 0, ciphertext.size)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext)
    }


    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val RSA_KEY_SIZE = 4096
    }
}
