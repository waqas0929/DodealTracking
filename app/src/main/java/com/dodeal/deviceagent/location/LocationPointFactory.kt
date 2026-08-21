package com.dodeal.deviceagent.location

import android.location.Location
import android.os.Build
import com.dodeal.deviceagent.domain.model.LocationPoint
import java.time.Instant
import java.util.UUID

/** Turns a raw Android Location + device state into a validated, backend-independent LocationPoint. */
class LocationPointFactory(private val deviceState: DeviceStateProvider) {

    fun create(location: Location, deviceId: String): LocationPoint =
        LocationPoint(
            eventId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            timestamp = Instant.ofEpochMilli(location.time.takeIf { it > 0 } ?: System.currentTimeMillis()),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed else null,
            bearing = if (location.hasBearing()) location.bearing else null,
            batteryPercent = deviceState.batteryLevel(),
            charging = deviceState.isCharging(),
            networkType = deviceState.networkType(),
            locationProvider = location.provider ?: "fused",
            mockLocation = isMock(location),
        )

    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
}
