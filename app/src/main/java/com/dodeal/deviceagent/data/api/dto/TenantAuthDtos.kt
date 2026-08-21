package com.dodeal.deviceagent.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** POST {tenant}/login  (spec Step 3). */
@Serializable
data class TenantLoginRequest(
    @SerialName("loginId") val loginId: String,
    @SerialName("password") val password: String,
)

/**
 * DoDeal tenant login response:
 * { "success": true, "data": { "token", "jwt_token", "name", "id", "expires_at", ... }, "message": "" }
 */
@Serializable
data class TenantLoginResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("message") val message: String? = null,
    @SerialName("data") val data: TenantLoginData? = null,
) {
    // Sanctum-style personal access token ("1347|...") is used as the API bearer by default;
    // falls back to the JWT if the sanctum token is absent.
    fun bearer(): String? = data?.token ?: data?.jwtToken
    fun refresh(): String? = null
    fun expiresAtIso(): String? = data?.expiresAt
    fun identity(): String? = (data?.id as? JsonPrimitive)?.contentOrNull
}

@Serializable
data class TenantLoginData(
    @SerialName("token") val token: String? = null,
    @SerialName("jwt_token") val jwtToken: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("role") val role: JsonElement? = null,
    @SerialName("agency") val agency: JsonElement? = null,
    @SerialName("is2fa") val is2fa: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
)
