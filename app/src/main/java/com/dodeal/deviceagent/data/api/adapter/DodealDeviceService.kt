package com.dodeal.deviceagent.data.api.adapter

import com.dodeal.deviceagent.data.api.dto.DodealAuthResponse
import com.dodeal.deviceagent.data.api.dto.DodealCommandAckRequest
import com.dodeal.deviceagent.data.api.dto.DodealCommandListResponse
import com.dodeal.deviceagent.data.api.dto.DodealEventRequest
import com.dodeal.deviceagent.data.api.dto.DodealGeofenceEventRequest
import com.dodeal.deviceagent.data.api.dto.DodealHeartbeatRequest
import com.dodeal.deviceagent.data.api.dto.DodealAddLocationRequest
import com.dodeal.deviceagent.data.api.dto.DodealBatchLocationsRequest
import com.dodeal.deviceagent.data.api.dto.DodealLoginRequest
import com.dodeal.deviceagent.data.api.dto.DodealMeetingRequest
import com.dodeal.deviceagent.data.api.dto.DodealPolicyResponse
import com.dodeal.deviceagent.data.api.dto.DodealRefreshRequest
import com.dodeal.deviceagent.data.api.dto.DodealRegisterRequest
import com.dodeal.deviceagent.data.api.dto.DodealRegisterResponse
import com.dodeal.deviceagent.data.api.dto.TenantLoginRequest
import com.dodeal.deviceagent.data.api.dto.TenantLoginResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit definition. Base URL = the tenant base resolved at login (https://{client_domain}/api/),
 * applied per-request by TenantHostInterceptor. So paths here are relative to /api/.
 */
internal interface DodealDeviceService {

    // --- Multi-tenant auth (spec Step 3) ---
    @POST("login")
    suspend fun tenantLogin(@Body body: TenantLoginRequest): Response<TenantLoginResponse>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: DodealRefreshRequest): Response<DodealAuthResponse>

    // --- Legacy enrollment (kept; unused by the tenant flow) ---
    @POST("device/register")
    suspend fun register(@Body body: DodealRegisterRequest): Response<DodealRegisterResponse>

    @POST("device/auth/login")
    suspend fun login(@Body body: DodealLoginRequest): Response<DodealAuthResponse>

    // --- Device tracking ---
    @GET("device/policy")
    suspend fun getPolicy(): Response<DodealPolicyResponse>

    // Location (DoDeal Store Location API #1 — token-authenticated, coords only).
    @POST("addLocation")
    suspend fun uploadLocation(@Body body: DodealAddLocationRequest): Response<Unit>

    @POST("device/locations")
    suspend fun uploadLocationBatch(@Body body: DodealBatchLocationsRequest): Response<Unit>

    @POST("device/heartbeat")
    suspend fun heartbeat(@Body body: DodealHeartbeatRequest): Response<Unit>

    @POST("device/events")
    suspend fun sendEvent(@Body body: DodealEventRequest): Response<Unit>

    @GET("device/commands")
    suspend fun getCommands(): Response<DodealCommandListResponse>

    @POST("device/commands/{command_id}/ack")
    suspend fun ackCommand(
        @Path("command_id") commandId: String,
        @Body body: DodealCommandAckRequest,
    ): Response<Unit>

    @POST("device/geofence/event")
    suspend fun geofenceEvent(@Body body: DodealGeofenceEventRequest): Response<Unit>

    @POST("device/meeting/check-in")
    suspend fun meetingCheckin(@Body body: DodealMeetingRequest): Response<Unit>

    @POST("device/meeting/check-out")
    suspend fun meetingCheckout(@Body body: DodealMeetingRequest): Response<Unit>
}
