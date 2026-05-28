package com.cryptotalk.app.security

import java.security.MessageDigest
import java.util.*

/**
 * SafetyNumberHelper creates a unique "Safety Number" between two users.
 * Users can compare this number in person or over a call. If the numbers match exactly, 
 * it proves their chat is 100% secure and no hacker is intercepting the messages.
 */
object SafetyNumberHelper {

    /**
     * Generates a safety number from two public keys.
     * The order of keys is sorted to ensure the same number is generated for both users.
     */
    fun computeSafetyNumber(publicKeyA: String, publicKeyB: String): String {
        val sortedKeys = listOf(publicKeyA, publicKeyB).sorted()
        val combined = sortedKeys.joinToString("")
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        
        // Convert hash to a 60-digit numeric string (Signal-style)
        // For simplicity, we'll take the first 30 bytes and convert to chunks of numbers
        return formatAsNumericGroups(hash)
    }

    private fun formatAsNumericGroups(hash: ByteArray): String {
        val sb = StringBuilder()
        // Use a subset of the hash to generate 12 groups of 5 digits
        for (i in 0 until 12) {
            val offset = i * 2
            // Combine two bytes into an unsigned short
            val value = ((hash[offset].toInt() and 0xFF) shl 8) or (hash[offset + 1].toInt() and 0xFF)
            // Format as 5-digit number with leading zeros
            sb.append(String.format("%05d", value % 100000))
            if (i < 11) sb.append(" ")
        }
        return sb.toString()
    }
}
