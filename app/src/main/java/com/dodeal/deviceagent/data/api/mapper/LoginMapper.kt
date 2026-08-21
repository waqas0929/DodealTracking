package com.dodeal.deviceagent.data.api.mapper

import com.dodeal.deviceagent.data.api.dto.DodealAuthResponse
import com.dodeal.deviceagent.data.api.dto.DodealLoginRequest
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.LoginRequest
import com.dodeal.deviceagent.domain.model.RegistrationResult
import java.time.Instant

object LoginMapper {
    fun toDto(request: LoginRequest): DodealLoginRequest = DodealLoginRequest(
        companyId = request.companyId,
        username = request.username,
        password = request.password,
        deviceId = request.identity.deviceId,
        serialNumber = request.identity.serialNumber,
        deviceModel = request.identity.deviceModel,
        androidVersion = request.identity.androidVersion,
        romVersion = request.identity.romVersion,
        agentVersion = request.identity.agentVersion,
    )

    fun toCredentials(response: DodealAuthResponse): DeviceCredentials = DeviceCredentials(
        deviceId = response.deviceId,
        accessToken = response.accessToken,
        tokenType = response.tokenType,
        expiresAt = Instant.parse(response.expiresAt),
        refreshToken = response.refreshToken,
    )

    fun toResult(response: DodealAuthResponse): RegistrationResult = RegistrationResult(
        deviceId = response.deviceId,
        credentials = toCredentials(response),
    )
}
