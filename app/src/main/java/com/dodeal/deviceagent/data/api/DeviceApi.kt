package com.dodeal.deviceagent.data.api

import com.dodeal.deviceagent.domain.model.CommandAck
import com.dodeal.deviceagent.domain.model.DeviceCommand
import com.dodeal.deviceagent.domain.model.DeviceEvent
import com.dodeal.deviceagent.domain.model.DevicePolicy
import com.dodeal.deviceagent.domain.model.GeofenceEvent
import com.dodeal.deviceagent.domain.model.Heartbeat
import com.dodeal.deviceagent.domain.model.HeartbeatAck
import com.dodeal.deviceagent.domain.model.LocationPoint
import com.dodeal.deviceagent.domain.model.LoginRequest
import com.dodeal.deviceagent.domain.model.MeetingCheckin
import com.dodeal.deviceagent.domain.model.MeetingCheckout
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.RegistrationRequest
import com.dodeal.deviceagent.domain.model.RegistrationResult

/**
 * The ONLY contract the domain/repository layer knows about. It speaks exclusively in domain models
 * and ApiResult. It has no idea what HTTP client, endpoint path, or JSON schema is behind it.
 *
 *   Repository  ->  DeviceApi (this)  ->  DodealApiAdapter  ->  Retrofit
 *
 * To move to a different backend you replace the adapter only. Location / Policy / Heartbeat /
 * Commands / Geofence / Meeting code never changes.
 */
interface DeviceApi {
    /** Per-agent enrollment: company + username + password -> scoped tokens (legacy). */
    suspend fun login(request: LoginRequest): ApiResult<RegistrationResult>

    /** Multi-tenant login against the resolved tenant base URL: loginId + password -> credentials. */
    suspend fun tenantLogin(loginId: String, password: String): ApiResult<DeviceCredentials>

    /** Exchange a refresh token for a fresh access token (token rotation). */
    suspend fun refreshToken(refreshToken: String): ApiResult<DeviceCredentials>

    suspend fun register(request: RegistrationRequest): ApiResult<RegistrationResult>
    suspend fun getPolicy(deviceId: String): ApiResult<DevicePolicy>

    suspend fun uploadLocation(point: LocationPoint): ApiResult<Unit>

    /** Bulk upload of buffered offline fixes via POST /api/device/locations (server cap 500). */
    suspend fun uploadLocationBatch(points: List<LocationPoint>): ApiResult<Unit>

    suspend fun sendHeartbeat(heartbeat: Heartbeat): ApiResult<HeartbeatAck>
    suspend fun sendEvent(event: DeviceEvent): ApiResult<Unit>

    suspend fun getCommands(deviceId: String): ApiResult<List<DeviceCommand>>
    suspend fun ackCommand(deviceId: String, ack: CommandAck): ApiResult<Unit>

    suspend fun sendGeofenceEvent(event: GeofenceEvent): ApiResult<Unit>
    suspend fun meetingCheckin(checkin: MeetingCheckin): ApiResult<Unit>
    suspend fun meetingCheckout(checkout: MeetingCheckout): ApiResult<Unit>
}
