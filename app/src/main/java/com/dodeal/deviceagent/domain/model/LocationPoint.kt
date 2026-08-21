package com.dodeal.deviceagent.domain.model

import java.time.Instant

/**
 * Internal location record produced by the engine and stored in the encrypted offline queue.
 * Backend-independent: LocationPayloadMapper is the only thing that turns it into the CRM wire model.
 * eventId is a client UUID and the idempotency / dedup key.
 */
data class LocationPoint(
    val eventId: String,
    val deviceId: String,
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val batteryPercent: Int?,
    val charging: Boolean,
    val networkType: NetworkType,
    val locationProvider: String,
    val mockLocation: Boolean,
) {
    /** Basic sanity validation; invalid points are dropped instead of uploaded. */
    fun isValid(): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0) &&
            timestamp.toEpochMilli() > 0
}

enum class ChargingStatus { CHARGING, DISCHARGING, FULL, UNKNOWN }
enum class NetworkType { WIFI, CELLULAR, ETHERNET, NONE, UNKNOWN }
enum class LocationSource { GPS, FUSED, NETWORK, PASSIVE, UNKNOWN }
