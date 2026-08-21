package com.dodeal.deviceagent.health

import com.dodeal.deviceagent.location.LocationSessionState
import java.time.Instant

/** Unified device health snapshot (spec §44) shown in diagnostics and used for reporting. */
data class DeviceHealth(
    val privileged: Boolean,
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val backgroundLocation: Boolean,
    val privilegedPhoneState: Boolean,
    val batteryExempt: Boolean,
    val foregroundServiceReady: Boolean,
    val bootReceiverReady: Boolean,
    val networkConnected: Boolean,
    val backendConnected: Boolean,
    val policySynced: Boolean,
    val policyStale: Boolean,
    val trackingActive: Boolean,
    val sessionState: LocationSessionState,
    val offlineQueueSize: Int,
    val lastLocationAt: Instant?,
    val lastLocationAccuracy: Float?,
    val lastUploadAt: Instant?,
    val lastError: String?,
    val romVersion: String,
    val appVersion: String,
)
