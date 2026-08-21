package com.dodeal.deviceagent.data.api.mapper

import com.dodeal.deviceagent.data.api.dto.DodealPolicyResponse
import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.domain.model.GeofenceArea
import com.dodeal.deviceagent.domain.model.WorkingWindow

object PolicyMapper {
    fun toDomain(dto: DodealPolicyResponse): DevicePolicy = DevicePolicy(
        policyVersion = dto.policyVersion,
        trackingEnabled = dto.trackingEnabled,
        timeZone = dto.timezone,
        workingHours = dto.workingHours.mapKeys { it.key.lowercase() }.mapValues {
            WorkingWindow(enabled = it.value.enabled, start = it.value.start, end = it.value.end)
        },
        locationIntervalSeconds = dto.locationIntervalSeconds,
        minimumDistanceMeters = dto.minimumDistanceMeters,
        highAccuracyEnabled = dto.highAccuracyEnabled,
        heartbeatIntervalSeconds = dto.heartbeatIntervalSeconds,
        batchUploadIntervalSeconds = dto.batchUploadIntervalSeconds,
        offlineQueueLimit = dto.offlineQueueLimit,
        geofencingEnabled = dto.geofencingEnabled,
        meetingCheckinEnabled = dto.meetingCheckinEnabled,
        deviceCommandsEnabled = dto.deviceCommandsEnabled,
        geofences = dto.geofences.filter { it.enabled }.map {
            GeofenceArea(
                id = it.geofenceId,
                name = it.name,
                latitude = it.latitude,
                longitude = it.longitude,
                radiusMeters = it.radiusMeters,
            )
        },
        fetchedAtEpochMs = System.currentTimeMillis(),
    )
}
