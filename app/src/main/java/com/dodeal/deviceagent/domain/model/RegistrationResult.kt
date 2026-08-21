package com.dodeal.deviceagent.domain.model

/** Domain result of a successful registration. */
data class RegistrationResult(
    val deviceId: String,
    val credentials: DeviceCredentials,
)
