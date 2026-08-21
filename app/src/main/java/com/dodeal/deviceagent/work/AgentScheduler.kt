package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dodeal.deviceagent.diagnostics.AgentLog
import com.dodeal.deviceagent.domain.model.DevicePolicy
import java.util.concurrent.TimeUnit

/**
 * The one place the agent's background work is scheduled, and the one place its cadence is decided.
 *
 * WHY THIS EXISTS
 * ---------------
 * Heartbeat, command polling and the offline flush each had their own hard-coded 15 minutes, and
 * each was enqueued with ExistingPeriodicWorkPolicy.KEEP. Two consequences, both invisible:
 *
 *   1. heartbeat_interval_seconds and batch_upload_interval_seconds — fields the server sends and
 *      the CRM can edit — did nothing whatsoever;
 *   2. KEEP means an already-scheduled period is never replaced, so even correcting the code would
 *      not have changed a handset already in the field. UPDATE is what makes a policy change land.
 *
 * Periods still cannot go below WorkManager's own floor; the floor is read from WorkManager rather
 * than written here, and WorkIntervals does the (unit-tested) arithmetic.
 */
object AgentScheduler {

    private const val HEARTBEAT_WORK = "dodeal_heartbeat"
    private const val COMMANDS_WORK = "dodeal_command_poll"
    private const val FLUSH_WORK = "dodeal_location_flush"

    /** Used when no policy has been fetched yet — one heartbeat cycle, not a guess per worker. */
    private const val FALLBACK_HEARTBEAT_SECONDS = 900
    private const val FALLBACK_FLUSH_SECONDS = 900

    /** WorkManager's own minimum periodic interval, in minutes. Never restated as a literal. */
    private val floorMinutes: Long
        get() = TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)

    /**
     * Schedule (or re-schedule) every periodic job from the policy in force.
     *
     * Safe to call as often as you like — on boot, on app start, and after every policy sync, which
     * is exactly when the cadence may have changed.
     */
    fun apply(context: Context, policy: DevicePolicy?) {
        val heartbeatMinutes = WorkIntervals.minutesFor(
            seconds = policy?.effectiveHeartbeatSeconds,
            fallbackSeconds = FALLBACK_HEARTBEAT_SECONDS,
            floorMinutes = floorMinutes,
        )
        val flushMinutes = WorkIntervals.minutesFor(
            seconds = policy?.batchUploadIntervalSeconds,
            fallbackSeconds = FALLBACK_FLUSH_SECONDS,
            floorMinutes = floorMinutes,
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val manager = WorkManager.getInstance(context)

        manager.enqueueUniquePeriodicWork(
            HEARTBEAT_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<HeartbeatWorker>(heartbeatMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints).build(),
        )

        // Commands ride the heartbeat's cadence: the heartbeat already answers
        // has_pending_commands, so polling more often than that only wakes the radio.
        manager.enqueueUniquePeriodicWork(
            COMMANDS_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CommandPollWorker>(heartbeatMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints).build(),
        )

        manager.enqueueUniquePeriodicWork(
            FLUSH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<LocationFlushWorker>(flushMinutes, TimeUnit.MINUTES)
                .setConstraints(constraints).build(),
        )

        AgentLog.i(
            AgentLog.Category.TRACKING,
            "work scheduled: heartbeat/commands ${heartbeatMinutes}m, flush ${flushMinutes}m",
        )
    }

    /** Stop every periodic job (sign-out, disable). The flush keeps running deliberately: */
    fun cancelTelemetry(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(HEARTBEAT_WORK)
            cancelUniqueWork(COMMANDS_WORK)
        }
    }

    fun cancelAll(context: Context) {
        cancelTelemetry(context)
        WorkManager.getInstance(context).cancelUniqueWork(FLUSH_WORK)
    }
}
