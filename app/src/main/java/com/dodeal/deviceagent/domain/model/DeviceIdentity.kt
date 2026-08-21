package com.dodeal.deviceagent.domain.model

/** Static facts about this device, gathered by DeviceInfoProvider. */
data class DeviceIdentity(
    val deviceId: String,
    val serialNumber: String,
    val deviceModel: String,
    val androidVersion: String,
    val romVersion: String,
    val agentVersion: String,
)
