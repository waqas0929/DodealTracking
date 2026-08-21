package com.dodeal.deviceagent.domain.model

import kotlinx.serialization.Serializable
import java.time.Duration

/** One day's working window. */
@Serializable
data class WorkingWindow(
    val enabled: Boolean = false,
    val start: String = "00:00",   // "HH:mm" in the policy timezone
    val end: String = "00:00",
)

/**
 * Internal policy model — the backend is the single source of truth. Serializable only for local
 * caching. Safety clamps and staleness are enforced here so a bad server value can never create an
 * uncontrolled loop or unbounded tracking.
 */
@Serializable
data class DevicePolicy(
    val policyVersion: String,
    val trackingEnabled: Boolean,
    val timeZone: String,
    val workingHours: Map<String, WorkingWindow>,   // keys: monday..sunday (lowercase)
    val locationIntervalSeconds: Int,
    val minimumDistanceMeters: Int,
    val highAccuracyEnabled: Boolean,
    val heartbeatIntervalSeconds: Int,
    val batchUploadIntervalSeconds: Int,
    val offlineQueueLimit: Int,
    val geofencingEnabled: Boolean,
    val meetingCheckinEnabled: Boolean,
    val deviceCommandsEnabled: Boolean,
    val geofences: List<GeofenceArea> = emptyList(),
    val fetchedAtEpochMs: Long = System.currentTimeMillis(),
) {
    /** Interval clamped to safe bounds (rejects 0 / absurd values -> no runaway loop). */
    val effectiveIntervalSeconds: Int
        get() = locationIntervalSeconds.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)

    val effectiveHeartbeatSeconds: Int
        get() = heartbeatIntervalSeconds.coerceIn(MIN_HEARTBEAT_SECONDS, MAX_HEARTBEAT_SECONDS)

    fun ageMs(now: Long = System.currentTimeMillis()): Long = now - fetchedAtEpochMs

    /** Stale but usable (report it). */
    fun isStale(now: Long = System.currentTimeMillis()): Boolean = ageMs(now) > SOFT_STALE.toMillis()

    /** Too old to trust -> safer behavior is to NOT track. */
    fun isHardStale(now: Long = System.currentTimeMillis()): Boolean = ageMs(now) > HARD_STALE.toMillis()

    companion object {
        const val MIN_INTERVAL_SECONDS = 10
        const val MAX_INTERVAL_SECONDS = 3600
        const val MIN_HEARTBEAT_SECONDS = 60
        const val MAX_HEARTBEAT_SECONDS = 86_400
        val SOFT_STALE: Duration = Duration.ofHours(24)
        val HARD_STALE: Duration = Duration.ofDays(7)

        /** Safe defaults used before the first successful sync — tracking OFF. */
        val SAFE_DEFAULT = DevicePolicy(
            policyVersion = "0",
            trackingEnabled = false,
            timeZone = "Asia/Dubai",
            workingHours = emptyMap(),
            locationIntervalSeconds = 60,
            minimumDistanceMeters = 20,
            highAccuracyEnabled = true,
            heartbeatIntervalSeconds = 300,
            batchUploadIntervalSeconds = 900,
            offlineQueueLimit = 10_000,
            geofencingEnabled = false,
            meetingCheckinEnabled = false,
            deviceCommandsEnabled = true,
            geofences = emptyList(),
        )
    }
}
