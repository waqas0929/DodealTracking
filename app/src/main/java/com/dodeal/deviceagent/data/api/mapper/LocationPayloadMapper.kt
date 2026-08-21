package com.dodeal.deviceagent.data.api.mapper

import com.dodeal.deviceagent.data.api.dto.DodealAddLocationRequest
import com.dodeal.deviceagent.data.api.dto.DodealBatchLocationsRequest
import com.dodeal.deviceagent.data.api.dto.DodealLocationItem
import com.dodeal.deviceagent.domain.model.LocationPoint

/**
 * LocationPoint -> DoDeal payloads. Single fixes go to /api/addLocation; buffered offline fixes go
 * to /api/device/locations as a batch. Optional fields are included when known.
 */
object LocationPayloadMapper {
    fun toDto(point: LocationPoint): DodealAddLocationRequest = DodealAddLocationRequest(
        latitude = point.latitude,
        longitude = point.longitude,
        accuracy = point.accuracy,
        batteryPercent = point.batteryPercent,
        provider = point.locationProvider,
        recordedAt = point.timestamp.toString(),
        isMock = point.mockLocation,
    )

    fun toItem(point: LocationPoint): DodealLocationItem = DodealLocationItem(
        latitude = point.latitude,
        longitude = point.longitude,
        recordedAt = point.timestamp.toString(),
        accuracy = point.accuracy,
        batteryPercent = point.batteryPercent,
        provider = point.locationProvider,
        isMock = point.mockLocation,
    )

    fun toBatch(points: List<LocationPoint>): DodealBatchLocationsRequest =
        DodealBatchLocationsRequest(locations = points.map { toItem(it) })
}
