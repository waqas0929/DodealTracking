package com.dodeal.deviceagent.location

import android.content.Context
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.repository.DeviceRepository
import com.dodeal.deviceagent.work.AgentScheduler

/**
 * Decides whether the location service should run and starts/stops it. Rule: track only when the
 * device is enrolled, the policy enables tracking and isn't hard-stale, and foreground permission is
 * granted. The service handles the finer working-hours gating.
 */
class LocationController(
    private val appContext: Context,
    private val deviceRepository: DeviceRepository,
) {
    /** The full answer, reason included. */
    fun decision(): TrackingDecision = TrackingStatus.decide(
        registered = deviceRepository.isRegistered(),
        hasLocationPermission = LocationPermissions.hasForeground(appContext),
        policy = deviceRepository.cachedPolicy(),
    )

    fun shouldTrack(): Boolean = decision().isActive

    fun sync() { if (shouldTrack()) start() else stop() }

    /**
     * Apply everything a freshly fetched policy implies: the background cadence AND the location
     * session. Call this after a policy sync instead of sync() alone — heartbeat and flush periods
     * live in the policy too, and before this they were fixed for the life of the install.
     */
    fun onPolicyChanged() {
        AgentScheduler.apply(appContext, deviceRepository.cachedPolicy())
        sync()
    }

    fun start() {
        if (!LocationPermissions.hasForeground(appContext)) {
            AgentLog.w(AgentLog.Category.TRACKING, "cannot start tracking: permission not granted")
            return
        }
        LocationForegroundService.start(appContext)
    }

    fun stop() = LocationForegroundService.stop(appContext)

    /**
     * Tear the location session down and bring it back under the CURRENT policy.
     *
     * `sync()` is not enough for this: if the service is already running it stays running with the
     * settings it started with, which is exactly the state the RESTART_TRACKING command exists to
     * clear — a handset that is reporting, but not on the interval or accuracy the CRM set.
     */
    fun restart() {
        stop()
        sync()
    }
}
