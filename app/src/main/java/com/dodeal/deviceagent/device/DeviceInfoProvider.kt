package com.dodeal.deviceagent.device

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.dodeal.deviceagent.BuildConfig
import com.dodeal.deviceagent.domain.model.DeviceIdentity
import java.util.UUID

/**
 * Gathers static device facts. Some fields (real hardware serial) require privileged/system access;
 * on a standard APK they gracefully fall back to placeholders. In PrivilegedRomStrategy these can be
 * read directly from the ROM.
 */
class DeviceInfoProvider(private val context: Context) {

    fun identity(): DeviceIdentity = DeviceIdentity(
        deviceId = stableDeviceId(),
        serialNumber = serialNumber(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = Build.VERSION.RELEASE ?: "unknown",
        romVersion = romVersion(),
        agentVersion = BuildConfig.VERSION_NAME,
    )

    @SuppressLint("HardwareIds")
    private fun stableDeviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return if (!androidId.isNullOrBlank()) androidId else UUID.randomUUID().toString()
    }

    private fun serialNumber(): String = try {
        // Requires privileged access on modern Android; standard APK will fail -> placeholder.
        @Suppress("DEPRECATION")
        Build.getSerial()
    } catch (_: SecurityException) {
        "UNAVAILABLE_STANDARD_APK"
    } catch (_: Throwable) {
        "UNKNOWN"
    }

    private fun romVersion(): String {
        // On the custom ROM a dedicated property carries the build id; fall back to Build.DISPLAY.
        val prop = systemProperty("ro.dodeal.rom.version")
        return prop?.takeIf { it.isNotBlank() } ?: Build.DISPLAY ?: "UNKNOWN_ROM"
    }

    private fun systemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        get.invoke(null, key) as? String
    } catch (_: Throwable) {
        null
    }
}
