package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire schema for POST /api/addLocation. The user is identified from the Bearer token; the device is
 * associated via the X-Device-Id header. latitude/longitude are required; the rest are optional and
 * only sent when known (the endpoint accepts them additively without breaking older callers).
 */
@Serializable
data class DodealAddLocationRequest(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("accuracy") val accuracy: Float? = null,
    @SerialName("battery_percent") val batteryPercent: Int? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("is_mock") val isMock: Boolean? = null,
)

/** One point in a batch upload (POST /api/device/locations). */
@Serializable
data class DodealLocationItem(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("accuracy") val accuracy: Float? = null,
    @SerialName("battery_percent") val batteryPercent: Int? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("is_mock") val isMock: Boolean? = null,
)

/**
 * Batch upload for fixes buffered while offline: POST /api/device/locations.
 * Server cap is device_tracking.locations.batch_max_points (500 by default) — callers chunk to it.
 */
@Serializable
data class DodealBatchLocationsRequest(
    @SerialName("locations") val locations: List<DodealLocationItem>,
)
