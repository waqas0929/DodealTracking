package com.dodeal.deviceagent.data.queue

import com.dodeal.deviceagent.domain.model.LocationPoint
import com.dodeal.deviceagent.domain.model.NetworkType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** Serializes a LocationPoint to/from a compact JSON blob for encrypted storage. Storage concern only. */
internal object QueuedPointCodec {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Stored(
        @SerialName("event_id") val eventId: String,
        @SerialName("device_id") val deviceId: String,
        @SerialName("timestamp") val timestamp: String,
        @SerialName("lat") val lat: Double,
        @SerialName("lng") val lng: Double,
        @SerialName("accuracy") val accuracy: Float?,
        @SerialName("altitude") val altitude: Double?,
        @SerialName("speed") val speed: Float?,
        @SerialName("bearing") val bearing: Float?,
        @SerialName("battery") val battery: Int?,
        @SerialName("charging") val charging: Boolean,
        @SerialName("network") val network: String,
        @SerialName("provider") val provider: String,
        @SerialName("mock") val mock: Boolean,
    )

    fun encode(point: LocationPoint): ByteArray {
        val stored = Stored(
            eventId = point.eventId,
            deviceId = point.deviceId,
            timestamp = point.timestamp.toString(),
            lat = point.latitude,
            lng = point.longitude,
            accuracy = point.accuracy,
            altitude = point.altitude,
            speed = point.speed,
            bearing = point.bearing,
            battery = point.batteryPercent,
            charging = point.charging,
            network = point.networkType.name,
            provider = point.locationProvider,
            mock = point.mockLocation,
        )
        return json.encodeToString(Stored.serializer(), stored).toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): LocationPoint {
        val s = json.decodeFromString(Stored.serializer(), String(bytes, Charsets.UTF_8))
        return LocationPoint(
            eventId = s.eventId,
            deviceId = s.deviceId,
            timestamp = Instant.parse(s.timestamp),
            latitude = s.lat,
            longitude = s.lng,
            accuracy = s.accuracy,
            altitude = s.altitude,
            speed = s.speed,
            bearing = s.bearing,
            batteryPercent = s.battery,
            charging = s.charging,
            networkType = runCatching { NetworkType.valueOf(s.network) }.getOrDefault(NetworkType.UNKNOWN),
            locationProvider = s.provider,
            mockLocation = s.mock,
        )
    }
}
