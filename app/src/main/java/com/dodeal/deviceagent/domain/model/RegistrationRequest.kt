package com.dodeal.deviceagent.domain.model

/** Domain request for POST /devices/register. No wire/JSON concerns here. */
data class RegistrationRequest(
    val agencyId: String,
    val salesUserId: String,
    val identity: DeviceIdentity,
)
