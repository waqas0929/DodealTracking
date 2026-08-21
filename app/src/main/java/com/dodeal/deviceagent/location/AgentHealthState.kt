package com.dodeal.deviceagent.location

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * Process-wide, observable runtime health written by the service / upload path and read by the
 * diagnostics screen and heartbeat. Kept as a simple singleton so background components and UI share
 * one source of truth without extra plumbing.
 */
object AgentHealthState {
    val sessionState = MutableStateFlow(LocationSessionState.STOPPED)

    @Volatile var lastLocationAt: Instant? = null
    @Volatile var lastLocationAccuracy: Float? = null
    @Volatile var lastUploadAt: Instant? = null
    @Volatile var lastError: String? = null

    fun onLocation(accuracy: Float?) {
        lastLocationAt = Instant.now()
        lastLocationAccuracy = accuracy
    }

    fun onUpload() { lastUploadAt = Instant.now() }
    fun onError(message: String?) { lastError = message }
}
