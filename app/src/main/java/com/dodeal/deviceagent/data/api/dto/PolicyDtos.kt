package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire schema for GET /api/device/policy (spec §8). */
@Serializable
data class DodealPolicyResponse(
    @SerialName("policy_version") val policyVersion: String = "0",
    @SerialName("tracking_enabled") val trackingEnabled: Boolean = false,
    @SerialName("timezone") val timezone: String = "Asia/Dubai",
    @SerialName("working_hours") val workingHours: Map<String, DodealWorkingWindow> = emptyMap(),
    @SerialName("location_interval_seconds") val locationIntervalSeconds: Int = 60,
    @SerialName("minimum_distance_meters") val minimumDistanceMeters: Int = 20,
    @SerialName("high_accuracy_enabled") val highAccuracyEnabled: Boolean = true,
    @SerialName("heartbeat_interval_seconds") val heartbeatIntervalSeconds: Int = 300,
    @SerialName("batch_upload_interval_seconds") val batchUploadIntervalSeconds: Int = 900,
    @SerialName("offline_queue_limit") val offlineQueueLimit: Int = 10000,
    @SerialName("geofencing_enabled") val geofencingEnabled: Boolean = false,
    @SerialName("meeting_checkin_enabled") val meetingCheckinEnabled: Boolean = false,
    @SerialName("device_commands_enabled") val deviceCommandsEnabled: Boolean = true,
    @SerialName("geofences") val geofences: List<DodealGeofenceArea> = emptyList(),
)

@Serializable
data class DodealWorkingWindow(
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("start") val start: String = "00:00",
    @SerialName("end") val end: String = "00:00",
)

@Serializable
data class DodealGeofenceArea(
    @SerialName("geofence_id") val geofenceId: String,
    @SerialName("name") val name: String = "",
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("radius_meters") val radiusMeters: Float,
    @SerialName("enabled") val enabled: Boolean = true,
)
