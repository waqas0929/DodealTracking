package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodeal.deviceagent.DodealAgentApp

/**
 * Safety net for the offline queue: even if the service is torn down, this periodically flushes any
 * buffered points once the network is back. The live path in LocationRepository handles the common
 * case; this catches the rest.
 *
 * Scheduling lives in AgentScheduler, which owns the cadence of every periodic job and reads it
 * from the policy — this class used to carry its own hard-coded 15 minutes.
 */
class LocationFlushWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DodealAgentApp
        return runCatching { app.container.locationRepository.flush() }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

}
