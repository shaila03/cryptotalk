package com.cryptotalk.app.utils

import android.util.Log

/**
 * PerformanceUtils measures how long it takes for the app to do things (like encrypting a message).
 * It logs a warning if something is too slow.
 */
object PerformanceUtils {
    @PublishedApi
    internal const val TAG = "PERF_STATS"

    /**
     * Measures the execution time of a block of code and logs it.
     * Use this to verify operations take < 50ms.
     */
    inline fun <T> measureTime(label: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        
        val status = if (duration < 50) "FAST" else "SLOW"
        Log.d(TAG, "[$status] $label took ${duration}ms")
        
        return result
    }
}
