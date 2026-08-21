package com.dodeal.updater.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dodeal.updater.UpdateOrchestrator
import com.dodeal.updater.UpdaterLog
import com.dodeal.updater.config.UpdaterConfig
import java.util.concurrent.TimeUnit

/** Periodic + retry update checks. Interval is backend-configurable (clamped), never excessive. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        UpdateOrchestrator(applicationContext).runCheck()
        Result.success()
    } catch (t: Throwable) {
        UpdaterLog.e("WORKER_FAILED", t)
        Result.retry() // safe retry with WorkManager backoff
    }

    companion object {
        private const val UNIQUE = "dodeal_update_check"

        fun schedule(context: Context) {
            val minutes = UpdaterConfig(context).pollMinutes.toLong()
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(minutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
            UpdaterLog.i("UPDATE_WORKER_SCHEDULED every ${minutes}min")
        }
    }
}
