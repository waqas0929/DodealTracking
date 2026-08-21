package com.dodeal.deviceagent.data.api.adapter

import com.dodeal.deviceagent.data.api.ApiResult
import com.dodeal.deviceagent.data.api.DeviceApi
import com.dodeal.deviceagent.data.api.dto.DodealRefreshRequest
import com.dodeal.deviceagent.data.api.mapper.LocationPayloadMapper
import com.dodeal.deviceagent.data.api.mapper.LoginMapper
import com.dodeal.deviceagent.data.api.mapper.PolicyMapper
import com.dodeal.deviceagent.data.api.mapper.RegistrationMapper
import com.dodeal.deviceagent.data.api.mapper.TelemetryMapper
import com.dodeal.deviceagent.domain.model.CommandAck
import com.dodeal.deviceagent.domain.model.DeviceCommand
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.DeviceEvent
import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.domain.model.GeofenceEvent
import com.dodeal.deviceagent.domain.model.Heartbeat
import com.dodeal.deviceagent.domain.model.LocationPoint
import com.dodeal.deviceagent.domain.model.LoginRequest
import com.dodeal.deviceagent.domain.model.MeetingCheckin
import com.dodeal.deviceagent.domain.model.MeetingCheckout
import com.dodeal.deviceagent.domain.model.RegistrationRequest
import com.dodeal.deviceagent.domain.model.RegistrationResult

/**
 * The one and only translator between the domain-facing DeviceApi and the concrete Dodeal backend.
 * Swapping backends = replacing this class + its DTOs/mappers. Nothing else changes.
 */
internal class DodealApiAdapter(
    private val service: DodealDeviceService,
) : DeviceApi {

    override suspend fun login(request: LoginRequest): ApiResult<RegistrationResult> =
        safeApiCall(
            call = { service.login(LoginMapper.toDto(request)) },
            transform = { body -> LoginMapper.toResult(body!!) },
        )

    override suspend fun refreshToken(refreshToken: String): ApiResult<DeviceCredentials> =
        safeApiCall(
            call = { service.refresh(DodealRefreshRequest(refreshToken)) },
            transform = { body -> LoginMapper.toCredentials(body!!) },
        )

    override suspend fun tenantLogin(loginId: String, password: String): ApiResult<DeviceCredentials> =
        safeApiCall(
            call = { service.tenantLogin(com.dodeal.deviceagent.data.api.dto.TenantLoginRequest(loginId, password)) },
            transform = { body ->
                val b = body!!
                val access = b.bearer() ?: error("Login response missing token")
                val expiresAt = b.expiresAtIso()?.let { iso ->
                    runCatching { java.time.Instant.parse(iso) }.getOrNull()
                } ?: java.time.Instant.now().plusSeconds(43_200) // default 12h
                DeviceCredentials(
                    deviceId = b.identity() ?: loginId,
                    accessToken = access,
                    tokenType = "Bearer",
                    expiresAt = expiresAt,
                    refreshToken = b.refresh(),
                )
            },
        )

    override suspend fun register(request: RegistrationRequest): ApiResult<RegistrationResult> =
        safeApiCall(
            call = { service.register(RegistrationMapper.toDto(request)) },
            transform = { body -> RegistrationMapper.toDomain(body!!) },
        )

    override suspend fun getPolicy(deviceId: String): ApiResult<DevicePolicy> =
        safeApiCall(
            call = { service.getPolicy() },
            transform = { body -> PolicyMapper.toDomain(body!!) },
        )

    override suspend fun uploadLocation(point: LocationPoint): ApiResult<Unit> =
        safeApiCall(
            call = { service.uploadLocation(LocationPayloadMapper.toDto(point)) },
            transform = { },
        )

    override suspend fun uploadLocationBatch(points: List<LocationPoint>): ApiResult<Unit> =
        safeApiCall(
            call = { service.uploadLocationBatch(LocationPayloadMapper.toBatch(points)) },
            transform = { },
        )


    override suspend fun sendHeartbeat(heartbeat: Heartbeat): ApiResult<Unit> =
        safeApiCall({ service.heartbeat(TelemetryMapper.toDto(heartbeat)) }, { })

    override suspend fun sendEvent(event: DeviceEvent): ApiResult<Unit> =
        safeApiCall({ service.sendEvent(TelemetryMapper.toDto(event)) }, { })

    override suspend fun getCommands(deviceId: String): ApiResult<List<DeviceCommand>> =
        safeApiCall(
            call = { service.getCommands() },
            transform = { body -> body?.commands?.map(TelemetryMapper::toDomain) ?: emptyList() },
        )

    override suspend fun ackCommand(deviceId: String, ack: CommandAck): ApiResult<Unit> =
        safeApiCall({ service.ackCommand(ack.commandId, TelemetryMapper.toDto(ack)) }, { })

    override suspend fun sendGeofenceEvent(event: GeofenceEvent): ApiResult<Unit> =
        safeApiCall({ service.geofenceEvent(TelemetryMapper.toDto(event)) }, { })

    override suspend fun meetingCheckin(checkin: MeetingCheckin): ApiResult<Unit> =
        safeApiCall({ service.meetingCheckin(TelemetryMapper.toDto(checkin)) }, { })

    override suspend fun meetingCheckout(checkout: MeetingCheckout): ApiResult<Unit> =
        safeApiCall({ service.meetingCheckout(TelemetryMapper.toDto(checkout)) }, { })
}
