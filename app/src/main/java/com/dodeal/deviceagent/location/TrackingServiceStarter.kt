package com.dodeal.deviceagent.location

import android.content.Context
import com.dodeal.deviceagent.diagnostics.TrackLog

/**
 * The one place that requests the tracking service to start. Called from MainActivity.onCreate,
 * BootCompletedReceiver (BOOT_COMPLETED / MY_PACKAGE_REPLACED) and after a successful login.
 *
 * Works for a normal /data/app install: when called from a foreground context (the activity) the
 * FGS start is permitted on modern Android and the service gains location capability. It does NOT
 * depend on privileged/system status, the persistent flag, a system allowlist or ROM behaviour.
 */
object TrackingServiceStarter {
    fun start(context: Context) {
        TrackLog.i("START_SERVICE_REQUESTED")
        LocationForegroundService.start(context.applicationContext)
    }
}
