package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules/cancels the heartbeat + command-poll loops. WorkManager's floor is 15 minutes; a
 * low-latency command path (FCM push / the foreground service loop) can be layered on later without
 * changing the repository.
 */
object TelemetryScheduler {
    private const val HEARTBEAT_WORK = "dodeal_heartbeat"
    private const val COMMANDS_WORK = "dodeal_command_poll"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val heartbeat = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()
        val commands = PeriodicWorkRequestBuilder<CommandPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints).build()

        WorkManager.getInstance(context).apply {
            enqueueUniquePeriodicWork(HEARTBEAT_WORK, ExistingPeriodicWorkPolicy.KEEP, heartbeat)
            enqueueUniquePeriodicWork(COMMANDS_WORK, ExistingPeriodicWorkPolicy.KEEP, commands)
        }
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(HEARTBEAT_WORK)
            cancelUniqueWork(COMMANDS_WORK)
        }
    }
}
