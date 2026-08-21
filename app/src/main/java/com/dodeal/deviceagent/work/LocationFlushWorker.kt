package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dodeal.deviceagent.DodealAgentApp
import java.util.concurrent.TimeUnit

/**
 * Safety net for the offline queue: even if the service is torn down, this periodically flushes any
 * buffered points once the network is back. The live path in LocationRepository handles the common
 * case; this catches the rest.
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

    companion object {
        private const val UNIQUE_NAME = "dodeal_location_flush"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationFlushWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
