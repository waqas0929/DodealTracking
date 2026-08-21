package com.dodeal.deviceagent.data.api.mapper

import com.dodeal.deviceagent.data.api.dto.DodealRegisterRequest
import com.dodeal.deviceagent.data.api.dto.DodealRegisterResponse
import com.dodeal.deviceagent.domain.model.DeviceCredentials
import com.dodeal.deviceagent.domain.model.RegistrationRequest
import com.dodeal.deviceagent.domain.model.RegistrationResult
import java.time.Instant

object RegistrationMapper {
    fun toDto(request: RegistrationRequest): DodealRegisterRequest = DodealRegisterRequest(
        agencyId = request.agencyId,
        salesUserId = request.salesUserId,
        deviceId = request.identity.deviceId,
        serialNumber = request.identity.serialNumber,
        deviceModel = request.identity.deviceModel,
        androidVersion = request.identity.androidVersion,
        romVersion = request.identity.romVersion,
        agentVersion = request.identity.agentVersion,
    )

    fun toDomain(response: DodealRegisterResponse): RegistrationResult = RegistrationResult(
        deviceId = response.deviceId,
        credentials = DeviceCredentials(
            deviceId = response.deviceId,
            accessToken = response.accessToken,
            tokenType = response.tokenType,
            expiresAt = Instant.parse(response.expiresAt),
        ),
    )
}
