package com.dodeal.deviceagent.data.api.mapper

import com.dodeal.deviceagent.data.api.dto.DodealCommandAckRequest
import com.dodeal.deviceagent.data.api.dto.DodealCommandResponse
import com.dodeal.deviceagent.data.api.dto.DodealEventRequest
import com.dodeal.deviceagent.data.api.dto.DodealGeofenceEventRequest
import com.dodeal.deviceagent.data.api.dto.DodealHeartbeatRequest
import com.dodeal.deviceagent.data.api.dto.DodealMeetingRequest
import com.dodeal.deviceagent.domain.model.CommandAck
import com.dodeal.deviceagent.domain.model.CommandType
import com.dodeal.deviceagent.domain.model.DeviceCommand
import com.dodeal.deviceagent.domain.model.DeviceEvent
import com.dodeal.deviceagent.domain.model.GeofenceEvent
import com.dodeal.deviceagent.domain.model.Heartbeat
import com.dodeal.deviceagent.domain.model.MeetingCheckin
import com.dodeal.deviceagent.domain.model.MeetingCheckout
import java.time.Instant

object TelemetryMapper {
    fun toDto(h: Heartbeat) = DodealHeartbeatRequest(
        deviceId = h.deviceId,
        timestamp = h.timestamp.toString(),
        batteryPercent = h.batteryPercent,
        charging = h.charging,
        networkType = h.networkType.name,
        appVersion = h.appVersion,
        appVersionCode = h.appVersionCode,
        romVersion = h.romVersion,
        trackingActive = h.trackingActive,
        locationPermission = h.locationPermission,
        backgroundLocation = h.backgroundLocation,
        batteryExempt = h.batteryExempt,
        offlineQueueSize = h.offlineQueueSize,
    )

    fun toDto(e: DeviceEvent) = DodealEventRequest(
        eventId = e.eventId,
        deviceId = e.deviceId,
        type = e.type.name,
        timestamp = e.timestamp.toString(),
        metadata = e.metadata,
    )

    fun toDto(a: CommandAck) = DodealCommandAckRequest(
        commandId = a.commandId,
        status = a.status.name,
        receivedAt = a.receivedAt.toString(),
        executedAt = a.executedAt.toString(),
        result = a.result,
    )

    fun toDomain(dto: DodealCommandResponse) = DeviceCommand(
        commandId = dto.commandId,
        type = runCatching { CommandType.valueOf(dto.type.uppercase()) }.getOrDefault(CommandType.UNKNOWN),
        issuedAt = runCatching { Instant.parse(dto.issuedAt) }.getOrDefault(Instant.now()),
        parameters = dto.parameters,
    )

    fun toDto(e: GeofenceEvent) = DodealGeofenceEventRequest(
        eventId = e.eventId,
        deviceId = e.deviceId,
        geofenceId = e.geofenceId,
        transition = e.transition.name,
        timestamp = e.timestamp.toString(),
        latitude = e.latitude,
        longitude = e.longitude,
    )

    fun toDto(c: MeetingCheckin) = DodealMeetingRequest(
        eventId = c.eventId, meetingId = c.meetingId, deviceId = c.deviceId,
        timestamp = c.timestamp.toString(), latitude = c.latitude, longitude = c.longitude, accuracy = c.accuracy,
    )

    fun toDto(c: MeetingCheckout) = DodealMeetingRequest(
        eventId = c.eventId, meetingId = c.meetingId, deviceId = c.deviceId,
        timestamp = c.timestamp.toString(), latitude = c.latitude, longitude = c.longitude, accuracy = c.accuracy,
    )
}
