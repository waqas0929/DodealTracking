package com.dodeal.deviceagent.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dodeal.deviceagent.DodealAgentApp
import com.dodeal.deviceagent.diagnostics.TrackLog
import com.dodeal.deviceagent.location.TrackingServiceStarter
import com.dodeal.deviceagent.work.LocationFlushWorker
import com.dodeal.deviceagent.work.TelemetryScheduler

/**
 * Boot / update entry point. Its ONE job that matters: synchronously start the persistent location
 * foreground service so the process is never left "empty" and killed. It does NOT gate on a cached
 * policy (the service pulls policy itself). WorkManager here is only for upload/retry scheduling —
 * it is never what keeps tracking alive.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        TrackLog.i("BOOT_RECEIVED action=$action")

        when (action) {
            // Runs before user unlock: credential-encrypted storage isn't available, so defer.
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                TrackLog.i("LOCKED_BOOT deferring to unlock")
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> startTracking(context)
        }
    }

    private fun startTracking(context: Context) {
        try {
            val container = (context.applicationContext as DodealAgentApp).container

            // Upload/retry loop (WorkManager) — reliability only, not the tracking lifeline.
            LocationFlushWorker.schedule(context)
            if (container.deviceRepository.isRegistered()) {
                TelemetryScheduler.schedule(context)
                // Start the persistent FGS directly and immediately.
                TrackingServiceStarter.start(context)
            } else {
                TrackLog.i("NOT_ENROLLED no tracking to start")
            }
        } catch (t: Throwable) {
            TrackLog.e("BOOT_STARTUP_FAILED", t)
        }
    }
}
