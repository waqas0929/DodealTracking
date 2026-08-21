package com.dodeal.updater.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/device/update-policy response. */
@Serializable
data class UpdatePolicyResponse(
    @SerialName("poll_minutes") val pollMinutes: Int? = null,
    @SerialName("apps") val apps: List<AppUpdate> = emptyList(),
)

@Serializable
data class AppUpdate(
    @SerialName("package_name") val packageName: String,
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String = "",
    @SerialName("download_url") val downloadUrl: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("mandatory") val mandatory: Boolean = false,
    @SerialName("min_battery_percent") val minBatteryPercent: Int = 20,
    @SerialName("install_after_download") val installAfterDownload: Boolean = true,
    @SerialName("allow_rollback") val allowRollback: Boolean = false,
    @SerialName("install_after") val installAfterEpochMs: Long? = null, // scheduled install time
)

/** POST /api/device/update-result request. */
@Serializable
data class UpdateResultRequest(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("serial_number") val serialNumber: String? = null,
    @SerialName("package_name") val packageName: String,
    @SerialName("old_version_code") val oldVersionCode: Long,
    @SerialName("new_version_code") val newVersionCode: Long,
    @SerialName("status") val status: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("download_started_at") val downloadStartedAt: String? = null,
    @SerialName("install_started_at") val installStartedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
)
