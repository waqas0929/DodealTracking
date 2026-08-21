package com.dodeal.deviceagent.domain.model

/**
 * What the agent enters at enrollment: their company, username and password, together with the
 * static device identity. Exchanged ONCE for tokens; the password is never persisted.
 */
data class LoginRequest(
    val companyId: String,
    val username: String,
    val password: String,
    val identity: DeviceIdentity,
)
