package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /api/device/heartbeat (spec §15). */
@Serializable
data class DodealHeartbeatRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("battery_percent") val batteryPercent: Int?,
    @SerialName("charging") val charging: Boolean,
    @SerialName("network_type") val networkType: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("app_version_code") val appVersionCode: Long,
    @SerialName("rom_version") val romVersion: String,
    @SerialName("tracking_active") val trackingActive: Boolean,
    @SerialName("location_permission") val locationPermission: Boolean,
    @SerialName("background_location") val backgroundLocation: Boolean,
    @SerialName("battery_exempt") val batteryExempt: Boolean,
    @SerialName("offline_queue_size") val offlineQueueSize: Int,
)

@Serializable
data class DodealEventRequest(
    @SerialName("event_id") val eventId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("type") val type: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("metadata") val metadata: Map<String, String> = emptyMap(),
)

/** GET /api/device/commands (spec §16). */
@Serializable
data class DodealCommandResponse(
    @SerialName("command_id") val commandId: String,
    @SerialName("type") val type: String,
    @SerialName("issued_at") val issuedAt: String = "",
    @SerialName("parameters") val parameters: Map<String, String> = emptyMap(),
)

@Serializable
data class DodealCommandListResponse(
    @SerialName("commands") val commands: List<DodealCommandResponse> = emptyList(),
)

/** POST /api/device/commands/{command_id}/ack (spec §16). */
@Serializable
data class DodealCommandAckRequest(
    @SerialName("command_id") val commandId: String,
    @SerialName("status") val status: String,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("executed_at") val executedAt: String,
    @SerialName("result") val result: String? = null,
)

/** POST /api/device/geofence/event (spec §17). */
@Serializable
data class DodealGeofenceEventRequest(
    @SerialName("event_id") val eventId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("geofence_id") val geofenceId: String,
    @SerialName("transition") val transition: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
)

/** POST /api/device/meeting/check-in|check-out (spec §18). */
@Serializable
data class DodealMeetingRequest(
    @SerialName("event_id") val eventId: String,
    @SerialName("meeting_id") val meetingId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("accuracy") val accuracy: Float?,
)
