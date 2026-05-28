package com.cryptotalk.app.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

/**
 * A helper tool that converts between a "PublicKey" object and a simple text format (Base64 string).
 * We do this because databases like Firestore can only store text, not complex Java objects.
 * We ONLY do this for the Public Key (which is safe to share).
 * We NEVER do this for the Private Key (which must remain secret on the device).
 */
object KeySerializer {

    /**
     * Takes a PublicKey object and converts it into a Base64 string.
     * This makes it easy to send the key over the internet or save it to a database.
     */
    fun publicKeyToBase64(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Takes a Base64 text string (like one we got from the database) 
     * and rebuilds the actual RSA PublicKey object out of it so we can use it to encrypt messages.
     */
    fun base64ToPublicKey(base64: String): PublicKey {
        // Step 1: Decode the text back into raw bytes
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        // Step 2: Tell Java what format these bytes are in (X509 is standard for public keys)
        val spec = X509EncodedKeySpec(bytes)
        // Step 3: Get an RSA key builder
        val keyFactory = KeyFactory.getInstance("RSA")
        // Step 4: Build and return the final PublicKey object
        return keyFactory.generatePublic(spec)
    }
}
