package com.dodeal.deviceagent.data.storage

import android.content.Context
import com.dodeal.deviceagent.domain.model.DevicePolicy
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Simple JSON-file policy cache in app-private storage. Policy is non-secret operational config, so
 * plain private storage is adequate; can be moved to Room/SQLCipher later behind this same interface.
 */
class FilePolicyStore(context: Context) : PolicyStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.applicationContext.filesDir, "device_policy.json")

    override fun save(policy: DevicePolicy) {
        file.writeText(json.encodeToString(DevicePolicy.serializer(), policy))
    }

    override fun load(): DevicePolicy? = runCatching {
        if (!file.exists()) return null
        json.decodeFromString(DevicePolicy.serializer(), file.readText())
    }.getOrNull()

    override fun lastSyncedAt(): Instant? =
        if (file.exists()) Instant.ofEpochMilli(file.lastModified()) else null
}
