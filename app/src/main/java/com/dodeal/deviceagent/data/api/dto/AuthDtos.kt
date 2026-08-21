package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PLACEHOLDER wire schema for POST /auth/login. Map field names to the real API here only. */
@Serializable
data class DodealLoginRequest(
    @SerialName("company_id") val companyId: String,
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("serial_number") val serialNumber: String,
    @SerialName("device_model") val deviceModel: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("rom_version") val romVersion: String,
    @SerialName("agent_version") val agentVersion: String,
)

/** Shared response shape for login + refresh. refresh_token may be absent if the API uses long-lived tokens. */
@Serializable
data class DodealAuthResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_at") val expiresAt: String, // ISO-8601
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/** PLACEHOLDER wire schema for POST /auth/refresh. */
@Serializable
data class DodealRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)
