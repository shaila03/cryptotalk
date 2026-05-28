package com.cryptotalk.app.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FlowUtils provides helper functions for reading data streams (Flows) quickly.
 */
object FlowUtils {
    /**
     * Helper to get the current value of a Flow with a timeout.
     * Useful for repositories that need to check a setting quickly.
     */
    suspend fun <T> getValue(flow: Flow<T>, default: T): T {
        return withTimeoutOrNull(500) {
            flow.first()
        } ?: default
    }
}
