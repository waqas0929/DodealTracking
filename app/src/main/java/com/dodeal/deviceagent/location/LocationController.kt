package com.dodeal.deviceagent.location

import android.content.Context
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.repository.DeviceRepository

/**
 * Decides whether the location service should run and starts/stops it. Rule: track only when the
 * device is enrolled, the policy enables tracking and isn't hard-stale, and foreground permission is
 * granted. The service handles the finer working-hours gating.
 */
class LocationController(
    private val appContext: Context,
    private val deviceRepository: DeviceRepository,
) {
    fun shouldTrack(): Boolean {
        val policy = deviceRepository.cachedPolicy() ?: return false
        return deviceRepository.isRegistered() &&
            policy.trackingEnabled &&
            !policy.isHardStale() &&
            LocationPermissions.hasForeground(appContext)
    }

    fun sync() { if (shouldTrack()) start() else stop() }

    fun start() {
        if (!LocationPermissions.hasForeground(appContext)) {
            AgentLog.w(AgentLog.Category.TRACKING, "cannot start tracking: permission not granted")
            return
        }
        LocationForegroundService.start(appContext)
    }

    fun stop() = LocationForegroundService.stop(appContext)
}
