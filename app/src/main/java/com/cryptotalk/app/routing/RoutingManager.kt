package com.cryptotalk.app.routing

import android.util.Log

/**
 * Simulates an anonymous routing system (like Tor).
 * Before a message is sent, this tool strips out all tracking metadata (like IP address and Device ID)
 * so the sender cannot be identified or tracked.
 */
object RoutingManager {

    private const val TAG = "RoutingManager"

    /**
     * Simulates routing a payload through multiple proxy layers.
     */
    fun routePayload(payload: String): String {
        Log.d(TAG, "Routing payload through Layer 1 (Entry Proxy)")
        Log.d(TAG, "Routing payload through Layer 2 (Relay Node - IP Masked)")
        Log.d(TAG, "Routing payload through Layer 3 (Exit Node)")
        // Simulation: No actual change to payload text, but we ensure no IP/DeviceID is attached
        return payload
    }

    /**
     * Strips any potential metadata from a document map before sending to Firestore.
     */
    fun stripMetadata(doc: MutableMap<String, Any?>) {
        doc.remove("ipAddress")
        doc.remove("deviceId")
        doc.remove("location")
        doc.remove("deviceModel")
        Log.d(TAG, "Metadata stripped for anonymous delivery.")
    }
}
