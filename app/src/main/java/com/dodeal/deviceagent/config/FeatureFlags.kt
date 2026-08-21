package com.dodeal.deviceagent.config

/**
 * Remote-controllable behaviour switches. Defaults here are the safe bootstrap values used
 * before the first policy sync; the CRM policy is the runtime source of truth once fetched.
 */
data class FeatureFlags(
    val locationTrackingEnabled: Boolean = true,
    val heartbeatEnabled: Boolean = true,
    val geofencingEnabled: Boolean = false,
    val meetingTrackingEnabled: Boolean = false,
    val verboseDiagnostics: Boolean = false,
)
