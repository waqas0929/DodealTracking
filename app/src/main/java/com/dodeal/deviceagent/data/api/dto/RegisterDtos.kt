package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PLACEHOLDER wire schema for POST /devices/register. Adjust field names to the real API here only. */
@Serializable
data class DodealRegisterRequest(
    @SerialName("agency_id") val agencyId: String,
    @SerialName("sales_user_id") val salesUserId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("rom_version") val romVersion: String,
    @SerialName("agent_version") val agentVersion: String,
)

@Serializable
data class DodealRegisterResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_at") val expiresAt: String, // ISO-8601
)
