package com.dodeal.deviceagent.device

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Decides at runtime whether we're running as a privileged/system app (baked into the ROM) or as an
 * ordinary APK. This is what lets AppContainer pick PrivilegedRomStrategy vs StandardApkStrategy
 * automatically — the same binary adapts; nothing else in the app changes.
 */
class PrivilegeDetector(private val context: Context) {

    fun isSystemApp(): Boolean {
        val flags = context.applicationInfo.flags
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return (flags and systemFlags) != 0
    }
}
