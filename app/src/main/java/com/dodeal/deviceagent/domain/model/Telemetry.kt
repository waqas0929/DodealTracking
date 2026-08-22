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

/**
 * What the server told the device when it checked in. Acting on this is what turns a fixed polling
 * loop into a device that reacts: nothing to do costs one request, and a policy edited in the CRM
 * lands on the next beat.
 */
data class HeartbeatAck(
    val policyChanged: Boolean = false,
    val hasPendingCommands: Boolean = false,
    val policyVersion: String? = null,
)

/**
 * Fixed, safe management command set. No arbitrary/remote execution.
 *
 * The wire names come from the SERVER's allow-list (config/device_tracking.php); where the two
 * vocabularies used different words for the same action, the server's word is an alias rather than
 * a rename, so an older queued command still executes. Anything unrecognised is UNKNOWN and is
 * acknowledged as failed — never silently ignored.
 */
enum class CommandType(private vararg val wireNames: String) {
    REFRESH_POLICY("REFRESH_POLICY"),
    UPLOAD_PENDING_DATA("UPLOAD_PENDING", "UPLOAD_PENDING_DATA"),
    REQUEST_SINGLE_LOCATION("REQUEST_SINGLE_LOCATION"),
    SYNC_NOW("SYNC_NOW"),
    REFRESH_DEVICE_INFO("REFRESH_DEVICE_INFO"),
    PING("PING"),
    RESTART_TRACKING("RESTART_TRACKING"),
    UNKNOWN;

    companion object {
        /** Resolve a server command name. Case-insensitive; unknown names never throw. */
        fun fromWire(name: String?): CommandType {
            val wanted = name?.trim()?.uppercase().orEmpty()
            if (wanted.isEmpty()) return UNKNOWN
            return entries.firstOrNull { type -> type.wireNames.any { it == wanted } } ?: UNKNOWN
        }
    }
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
