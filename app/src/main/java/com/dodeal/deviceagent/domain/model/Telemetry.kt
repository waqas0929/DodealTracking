package com.dodeal.deviceagent.domain.model

import java.time.Instant

data class Heartbeat(
    val deviceId: String,
    val timestamp: Instant,
    val batteryPercent: Int?,
    val charging: Boolean,
    val networkType: NetworkType,
    val appVersion: String,
    val appVersionCode: Long,
    val romVersion: String,
    val trackingActive: Boolean,
    val locationPermission: Boolean,
    val backgroundLocation: Boolean,
    val batteryExempt: Boolean,
    val offlineQueueSize: Int,
)

enum class DeviceEventType {
    DEVICE_BOOTED, DEVICE_ONLINE, DEVICE_OFFLINE, TRACKING_STARTED, TRACKING_STOPPED,
    LOCATION_UNAVAILABLE, POLICY_SYNCED, GEOFENCE_ENTER, GEOFENCE_EXIT,
    MEETING_CHECKIN, MEETING_CHECKOUT
}

data class DeviceEvent(
    val eventId: String,
    val deviceId: String,
    val type: DeviceEventType,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap(),
)

/** Fixed, safe management command set. No arbitrary/remote execution. */
enum class CommandType {
    REFRESH_POLICY, UPLOAD_PENDING_DATA, REQUEST_SINGLE_LOCATION, SYNC_NOW, REFRESH_DEVICE_INFO, UNKNOWN
}

data class DeviceCommand(
    val commandId: String,
    val type: CommandType,
    val issuedAt: Instant,
    val parameters: Map<String, String> = emptyMap(),
)

enum class CommandStatus { RECEIVED, EXECUTED, FAILED }

data class CommandAck(
    val commandId: String,
    val status: CommandStatus,
    val receivedAt: Instant,
    val executedAt: Instant,
    val result: String? = null,
)

enum class GeofenceTransition { ENTER, EXIT, DWELL }

data class GeofenceEvent(
    val eventId: String,
    val deviceId: String,
    val geofenceId: String,
    val transition: GeofenceTransition,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
)

data class MeetingCheckin(
    val eventId: String,
    val deviceId: String,
    val meetingId: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
)

data class MeetingCheckout(
    val eventId: String,
    val deviceId: String,
    val meetingId: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
)
