package com.dodeal.deviceagent.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dodeal.deviceagent.DodealAgentApp

class HeartbeatWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DodealAgentApp
        if (!app.container.deviceRepository.isRegistered()) return Result.success()
        return runCatching { app.container.telemetryRepository.sendHeartbeat() }
            .fold(onSuccess = { ok -> if (ok) Result.success() else Result.retry() },
                  onFailure = { Result.retry() })
    }
}
