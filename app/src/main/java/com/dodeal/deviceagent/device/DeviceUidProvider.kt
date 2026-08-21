package com.dodeal.deviceagent.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Stable, persisted device identifier sent as the X-Device-Id header on every tenant request and
 * used as the device's client-side UID at registration. Seeded once from ANDROID_ID (or a random
 * UUID) and then never changes, so the CRM can associate all fixes/heartbeats with one device.
 * Stored in device-protected storage so it is available during direct boot (before unlock).
 */
class DeviceUidProvider(context: Context) {

    private val prefs = context.applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("dodeal_device", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    @SuppressLint("HardwareIds")
    fun uid(): String {
        prefs.getString(KEY_UID, null)?.let { return it }
        val seed = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        prefs.edit().putString(KEY_UID, seed).apply()
        return seed
    }

    private companion object { const val KEY_UID = "uid" }
}
