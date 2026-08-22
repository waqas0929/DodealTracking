package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/**
 * The heartbeat's ANSWER, which the agent used to throw away.
 *
 * The server replies with more than 200: it says whether the policy the device is running is still
 * current, and whether anything is queued for it. Reading it is what makes the agent self-driving —
 * a policy edited in the CRM lands on the next beat instead of waiting for the cache to go stale,
 * and commands are fetched only when there are any.
 */
@Serializable
data class DodealHeartbeatResponse(
    @SerialName("success") val success: Boolean = true,
    @SerialName("policy_version") val policyVersion: String? = null,
    @SerialName("policy_changed") val policyChanged: Boolean = false,
    @SerialName("has_pending_commands") val hasPendingCommands: Boolean = false,
    @SerialName("tracking_enabled") val trackingEnabled: Boolean? = null,
)

@Serializable
data class DodealEventRequest(
    @SerialName("event_id") val eventId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("type") val type: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("metadata") val metadata: Map<String, String> = emptyMap(),
)

/**
 * GET /api/device/commands.
 *
 * Field names follow the SERVER, not the spec the app was first written against — the two had
 * drifted and every poll died on the difference:
 *   - the server's field is `command`, the spec said `type`. A missing required field threw, so the
 *     whole response failed to parse and SafeApiCall swallowed it: commands simply never arrived.
 *   - the server's field is `payload`, the spec said `parameters`.
 *   - `command_id` arrives as a NUMBER; it stays a String here (ids are opaque) and ApiJson's
 *     lenient mode reads the unquoted value.
 *
 * `payload` is typed as a raw JsonElement because the server may send null, an object, or a plain
 * string, and a command must never be lost to the shape of its own payload.
 */
@Serializable
data class DodealCommandResponse(
    @SerialName("command_id") val commandId: String,
    @SerialName("command") val type: String,
    @SerialName("issued_at") val issuedAt: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("payload") val payload: JsonElement? = null,
) {
    /**
     * The payload flattened to the string map the executor expects. A payload that is not a flat
     * object (null, a bare string, nested values) yields an empty map rather than an error: no
     * command in the server's vocabulary needs one, so a surprise here must not stop execution.
     */
    val parameters: Map<String, String>
        get() = (payload as? JsonObject)
            ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.let { key to it.content } }
            ?.toMap()
            .orEmpty()
}

@Serializable
data class DodealCommandListResponse(
    @SerialName("commands") val commands: List<DodealCommandResponse> = emptyList(),
)

/**
 * POST /api/device/commands/{command_id}/ack.
 *
 * `success` is the field the server actually reads, and it defaults to TRUE when absent — so an ack
 * that only carried `status: "FAILED"` was recorded as executed. The status and both timestamps are
 * still sent; the server ignores what it does not use, and they cost nothing to keep.
 */
@Serializable
data class DodealCommandAckRequest(
    @SerialName("command_id") val commandId: String,
    @SerialName("success") val success: Boolean,
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
